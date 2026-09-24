import { AUTH_BOOST_CODE, AUTH_CODE, CODE } from '../../constants/errorCode.js'
import { ALL_ROLES } from '../../constants/role.js'
import { db, persistTrustedDevices, toEmployeeVO } from '../db.js'
import { fail, formatDateTime, maskPhone, ok } from '../util.js'
import { isBlank, isPhone, isStrongPassword } from '../validate.js'

/**
 * 认证接口
 * - 一期 4 端点（api.md 4.1）：login / logout / me / password —— 入参/出参**保持不变**
 * - 登录体系改造新增（multi-client-architecture.md §4.1.2 + demo-login-redesign.md §7.2）：
 *   sms/send(A1)、sms/login(A2)、device/verify(B2)、devices(C1/C2)、captcha(D1)
 * - login 为白名单（route.auth=false）；A1/A2/B2/D1 同为公开白名单；C1/C2 需登录态且限本人
 */

/* ==================== 登录体系改造常量 ==================== */

/** 会话有效期 3 天（multi-client §4.3.1：JWT exp 与 Redis TTL 双控的服务端一侧） */
const SESSION_TTL_SECONDS = 259200
/** 验证码 TTL 300s / 同手机号重发间隔 60s / 尝试上限 5（multi-client §4.4.2、§4.4.3） */
const SMS_CODE_TTL_MS = 300 * 1000
const SMS_RESEND_INTERVAL_MS = 60 * 1000
const SMS_MAX_ATTEMPTS = 5
/** 二次验证票据 TTL：短时效 5 分钟（multi-client §4.3.2） */
const DEVICE_TICKET_TTL_MS = 5 * 60 * 1000

/**
 * 演示固定验证码（demo-login-redesign §6）
 * - 只允许存在于 Mock 实现内部：生产构建（build:prod）整块剔除 mock 层，本字面量不进公开产物
 * - 绝不回显到页面可见区、Toast、日志或响应体（security-auth-review §4.4 红线）
 * - 演示取码：翻看演示剧本/门户说明，而非从页面读取
 */
const DEMO_FIXED_CODE = '000000'

/* ==================== 通用工具 ==================== */

/** Mock 令牌 jti：不引三方依赖；用时间戳+随机串即可满足「同账号再次登录后旧 token 立即 401」的演示诉求 */
function createJti() {
  return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 10)}`
}

/** 写登录日志（api.md 4.1.1：成功/失败均记录） */
function addLoginLog({ username, employeeId, result, reason }) {
  db.seq.loginLog += 1
  db.loginLogs.push({
    id: db.seq.loginLog,
    username,
    employee_id: employeeId,
    login_ip: '127.0.0.1',
    login_result: result,
    fail_reason: reason,
    user_agent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) DemoBrowser/1.0',
    login_time: formatDateTime(new Date())
  })
}

/**
 * 端准入（demo-login-redesign §1.3 + 主智能体裁定）
 * - `clientType` 缺省即不校验：既有调用方（verify 脚本、一期页面）不传该字段，行为与改造前完全一致
 * - WEB（PC）：仅 ADMIN；H5 且入口声明 `as=boss`（管理端视角）：ADMIN / STATION_ADMIN
 * - 员工端（H5 无 as / as=staff|station）不限角色
 */
function endAdmissionError(clientType, role, as) {
  if (!clientType) return null
  if (clientType === 'WEB') return role === 'ADMIN' ? null : AUTH_BOOST_CODE.END_NOT_ALLOWED
  if (clientType === 'H5' && as === 'boss') {
    return role === 'ADMIN' || role === 'STATION_ADMIN' ? null : AUTH_BOOST_CODE.END_NOT_ALLOWED
  }
  return null
}

/** 签发会话：返回登录出参（既有 3 字段 + 追加的 sessionExpireAt），并写入 sessions 与 sessionMeta（3 天到期判定用） */
function issueSession(database, employee, deviceId) {
  const jti = createJti()
  const expireAt = Date.now() + SESSION_TTL_SECONDS * 1000
  // 同账号覆盖写会话：旧 token 因 jti 不匹配立即失效（互踢）
  database.sessions.set(employee.id, jti)
  database.sessionMeta.set(employee.id, { deviceId: deviceId || null, expireAt })
  employee.last_login_time = formatDateTime(new Date())
  return {
    token: `mock.${employee.id}.${jti}`,
    expiresIn: SESSION_TTL_SECONDS,
    employee: toEmployeeVO(employee),
    sessionExpireAt: formatDateTime(new Date(expireAt))
  }
}

/** 受信设备行（不筛 revoked，供 upsert 复用；是否受信另判） */
function deviceRow(database, employeeId, deviceId) {
  return database.trustedDevices.find((d) => d.employee_id === employeeId && d.device_id === deviceId)
}

/** 是否已受信（服务端权威；前端 deviceId 仅为弱信号，不作放行依据） */
function isDeviceTrusted(database, employeeId, deviceId) {
  const row = deviceRow(database, employeeId, deviceId)
  return !!(row && row.revoked === 0)
}

/** 信任设备（幂等 upsert，对齐 auth_trusted_device 的唯一键语义） */
function trustDevice(database, employee, device) {
  const deviceId = device && device.deviceId ? String(device.deviceId) : ''
  if (!deviceId) return null
  const now = formatDateTime(new Date())
  const row = deviceRow(database, employee.id, deviceId)
  if (row) {
    row.last_seen_time = now
    row.revoked = 0
    row.trusted = 1
    persistTrustedDevices()
    return row
  }
  database.seq.trustedDevice += 1
  const created = {
    id: database.seq.trustedDevice,
    employee_id: employee.id,
    device_id: deviceId,
    platform: device.platform || 'WEB',
    model: device.model || null,
    os_version: device.osVersion || null,
    app_version: device.appVersion || null,
    last_ip: '127.0.0.1',
    first_seen_time: now,
    last_seen_time: now,
    trusted: 1,
    revoked: 0,
    create_time: now,
    update_time: now
  }
  database.trustedDevices.push(created)
  persistTrustedDevices()
  return created
}

/** IPv4 末段打码（设备列表出参脱敏） */
const maskIp = (ip) => (ip ? String(ip).replace(/\.\d+$/, '.**') : null)

/* ==================== 短信与验证码 ==================== */

/**
 * 验证码暂存键：LOGIN 场景按手机号，DEVICE_VERIFY 场景按 employeeId
 * 为什么设备场景改按员工：设备步的手机号在页面上是**脱敏只读**的（前端不得自行还原明文），
 * 故以「员工 + 场景」为键，让前端只需携带 twoFactorTicket 即可发码/校验，无需接触明文手机号。
 */
const codeKey = (scene, identifier) => `${scene}:${identifier}`

/** 写短信审计（仅脱敏手机号；绝不写验证码 —— security-auth-review §4.4） */
function addSmsLog(database, { phoneMasked, scene, deviceId, result, failReason }) {
  database.seq.smsLog += 1
  database.smsLogs.push({
    id: database.seq.smsLog,
    phone_masked: phoneMasked,
    scene,
    send_ip: '127.0.0.1',
    device_id: deviceId || null,
    result,
    fail_reason: failReason || null,
    create_time: formatDateTime(new Date())
  })
}

/** 同手机号最近一次成功发码时刻（60s 频控判定） */
function lastSendAt(database, phoneMasked) {
  const hit = database.smsLogs.filter((l) => l.phone_masked === phoneMasked && l.result === 1)
  return hit.length ? new Date(hit[hit.length - 1].create_time.replace(' ', 'T')).getTime() : 0
}

/** 发码：写入验证码暂存（服务端权威，校验成功即删） */
function storeCode(database, scene, identifier) {
  database.smsCodes.set(codeKey(scene, identifier), {
    code: DEMO_FIXED_CODE,
    expireAt: Date.now() + SMS_CODE_TTL_MS,
    attempts: 0
  })
}

/** 校验验证码：通过返回 null，否则返回错误码（1102 错/过期、1103 尝试超限） */
function verifyCode(database, scene, identifier, code) {
  const key = codeKey(scene, identifier)
  const record = database.smsCodes.get(key)
  if (!record) return AUTH_BOOST_CODE.CODE_INVALID
  if (record.expireAt <= Date.now()) {
    database.smsCodes.delete(key)
    return AUTH_BOOST_CODE.CODE_INVALID
  }
  if (record.attempts >= SMS_MAX_ATTEMPTS) {
    database.smsCodes.delete(key)
    return AUTH_BOOST_CODE.CODE_ATTEMPTS_EXCEEDED
  }
  if (String(code || '').trim() !== record.code) {
    record.attempts += 1
    if (record.attempts >= SMS_MAX_ATTEMPTS) {
      database.smsCodes.delete(key)
      return AUTH_BOOST_CODE.CODE_ATTEMPTS_EXCEEDED
    }
    return AUTH_BOOST_CODE.CODE_INVALID
  }
  database.smsCodes.delete(key)
  return null
}

/* ==================== 一期 4 端点（入参/出参保持不变） ==================== */

function login({ db: database, body }) {
  const username = String(body.username || '').trim()
  const password = String(body.password || '')

  if (username.length < 4 || username.length > 30) return fail(CODE.BAD_REQUEST, '请输入 4-30 位登录账号')
  if (isBlank(password)) return fail(CODE.BAD_REQUEST, '请输入密码')

  const employee = database.employees.find((e) => e.is_deleted === 0 && e.username === username)
  // 账号不存在与密码错误统一 1001（防账号探测，api.md 4.1.1）
  if (!employee || employee.password !== password) {
    addLoginLog({ username, employeeId: employee ? employee.id : null, result: 0, reason: '用户名或密码错误' })
    return fail(AUTH_CODE.LOGIN_FAILED)
  }
  if (employee.status !== 1) {
    addLoginLog({ username, employeeId: employee.id, result: 0, reason: '账号已禁用，请联系管理员' })
    return fail(AUTH_CODE.ACCOUNT_DISABLED)
  }

  // 端准入：仅当调用方显式上报 clientType 时校验，未上报即维持既有行为（既有 4 端点零变更）
  const endError = endAdmissionError(body.clientType, employee.role, body.as)
  if (endError) {
    addLoginLog({ username, employeeId: employee.id, result: 0, reason: '该账号无权登录此端' })
    return fail(endError)
  }

  // 新设备二次验证：仅当上报了 device 且该设备未被信任时触发（设计 S3；短信通道登录不触发）
  const device = body.device && typeof body.device === 'object' ? body.device : null
  const deviceId = device ? String(device.deviceId || '') : ''
  if (deviceId && !isDeviceTrusted(database, employee.id, deviceId)) {
    const ticket = `ticket.${createJti()}`
    database.deviceTickets.set(ticket, {
      employeeId: employee.id,
      deviceId,
      clientType: body.clientType || null,
      as: body.as || null,
      expireAt: Date.now() + DEVICE_TICKET_TTL_MS
    })
    addLoginLog({ username, employeeId: employee.id, result: 0, reason: '新设备需短信二次验证' })
    // 1104 为业务分流码（非错误）：此处以正常响应 + 字段承载，前端据此进入卡片第 2 步
    return ok({
      needDeviceVerify: true,
      deviceTrusted: false,
      twoFactorTicket: ticket,
      employee: toEmployeeVO(employee)
    })
  }

  const session = issueSession(database, employee, deviceId)
  if (deviceId) trustDevice(database, employee, device)
  addLoginLog({ username, employeeId: employee.id, result: 1, reason: null })
  return ok({ ...session, deviceTrusted: true, needDeviceVerify: false })
}

function logout({ db: database, user }) {
  // 幂等：无会话也返回成功
  database.sessions.delete(user.id)
  database.sessionMeta.delete(user.id)
  return ok(null)
}

function me({ user }) {
  return ok(toEmployeeVO(user))
}

function updatePassword({ db: database, body, user }) {
  const oldPassword = String(body.oldPassword || '')
  const newPassword = String(body.newPassword || '')

  if (!isStrongPassword(newPassword)) return fail(CODE.BAD_REQUEST, '新密码须为 8-20 位且同时包含字母和数字')
  if (user.password !== oldPassword) return fail(AUTH_CODE.OLD_PASSWORD_WRONG)

  user.password = newPassword
  user.pwd_changed = 1
  user.update_time = formatDateTime(new Date())
  // 改密后强制下线（api.md 4.1.4）：前端随后跳登录页。元数据须一并清，避免残留到期时间
  database.sessions.delete(user.id)
  database.sessionMeta.delete(user.id)
  return ok(null)
}

/* ==================== 登录体系改造新增端点 ==================== */

/**
 * A1 发码：演示态恒成功返回
 * - LOGIN 场景：按手机号定位账号（未绑定 → 1109，与账号不存在同码防枚举）
 * - DEVICE_VERIFY 场景：按 twoFactorTicket 定位账号（页面手机号脱敏只读，无需也不接受明文手机号）
 * 说明：`twoFactorTicket` 是本次为设备场景追加的**可选**入参（设计规范 §7.1 A1 未列，口径见 §7.3 追加可选字段）
 */
function smsSend({ db: database, body }) {
  const scene = String(body.scene || 'LOGIN')
  const deviceId = body.deviceId ? String(body.deviceId) : null

  if (scene === 'DEVICE_VERIFY') {
    const record = database.deviceTickets.get(String(body.twoFactorTicket || ''))
    if (!record || record.expireAt <= Date.now()) {
      if (record) database.deviceTickets.delete(String(body.twoFactorTicket || ''))
      return fail(AUTH_BOOST_CODE.DEVICE_REVOKED, '设备验证已失效，请重新登录')
    }
    const employee = database.employees.find((e) => e.id === record.employeeId)
    if (!employee || employee.status !== 1) return fail(AUTH_CODE.ACCOUNT_DISABLED)

    const masked = maskPhone(employee.phone)
    const lastDeviceSend = lastSendAt(database, masked)
    if (lastDeviceSend && Date.now() - lastDeviceSend < SMS_RESEND_INTERVAL_MS) {
      addSmsLog(database, { phoneMasked: masked, scene, deviceId: record.deviceId, result: 0, failReason: '发送过于频繁' })
      return fail(AUTH_BOOST_CODE.SMS_RATE_LIMITED)
    }
    storeCode(database, 'DEVICE_VERIFY', employee.id)
    addSmsLog(database, { phoneMasked: masked, scene, deviceId: record.deviceId, result: 1, failReason: null })
    return ok({ sent: true, expireIn: 300, nextAllowedIn: 60, requireCaptcha: false })
  }

  const phone = String(body.phone || '').trim()
  if (!isPhone(phone)) return fail(CODE.BAD_REQUEST, '请输入正确的 11 位手机号')

  const employee = database.employees.find((e) => e.is_deleted === 0 && e.phone === phone)
  // 账号不存在与未绑手机号统一 1109，避免以不同码暴露手机号是否已注册
  if (!employee) {
    addSmsLog(database, { phoneMasked: maskPhone(phone), scene, deviceId, result: 0, failReason: '手机号未绑定账号' })
    return fail(AUTH_BOOST_CODE.PHONE_NOT_BOUND)
  }
  if (employee.status !== 1) return fail(AUTH_CODE.ACCOUNT_DISABLED)

  const last = lastSendAt(database, maskPhone(phone))
  if (last && Date.now() - last < SMS_RESEND_INTERVAL_MS) {
    addSmsLog(database, { phoneMasked: maskPhone(phone), scene, deviceId, result: 0, failReason: '发送过于频繁' })
    return fail(AUTH_BOOST_CODE.SMS_RATE_LIMITED)
  }

  storeCode(database, 'LOGIN', phone)
  addSmsLog(database, { phoneMasked: maskPhone(phone), scene, deviceId, result: 1, failReason: null })
  // 演示态：不真实发送、不开启图形验证码（demo-login-redesign §6）
  return ok({ sent: true, expireIn: 300, nextAllowedIn: 60, requireCaptcha: false })
}

/** A2 短信登录：短信本身即二次因子，登录即视为受信 */
function smsLogin({ db: database, body }) {
  const phone = String(body.phone || '').trim()
  const code = String(body.code || '').trim()
  if (!isPhone(phone)) return fail(CODE.BAD_REQUEST, '请输入正确的 11 位手机号')
  if (isBlank(code)) return fail(CODE.BAD_REQUEST, '请输入验证码')

  const employee = database.employees.find((e) => e.is_deleted === 0 && e.phone === phone)
  // 账号不存在与未绑手机号统一 1001（防手机号枚举，与密码通道同口径）
  if (!employee) return fail(AUTH_CODE.LOGIN_FAILED)
  if (employee.status !== 1) return fail(AUTH_CODE.ACCOUNT_DISABLED)

  const endError = endAdmissionError(body.clientType, employee.role, body.as)
  if (endError) return fail(endError)

  const codeError = verifyCode(database, 'LOGIN', phone, code)
  if (codeError) return fail(codeError)

  const device = body.device && typeof body.device === 'object' ? body.device : null
  const deviceId = device ? String(device.deviceId || '') : ''
  const session = issueSession(database, employee, deviceId)
  if (deviceId) trustDevice(database, employee, device)
  addLoginLog({ username: employee.username, employeeId: employee.id, result: 1, reason: '短信验证码登录' })
  return ok({ ...session, deviceTrusted: true })
}

/** B2 设备二次验证：校验票据 + 验证码，通过后签发会话并写入受信设备 */
function deviceVerify({ db: database, body }) {
  const ticket = String(body.twoFactorTicket || '')
  const code = String(body.code || '').trim()

  const record = database.deviceTickets.get(ticket)
  if (!record || record.expireAt <= Date.now()) {
    if (record) database.deviceTickets.delete(ticket)
    // 票据失效等价于「须重新登录」：复用 1107 码位并给明确文案（不改 1107 的默认语义）
    return fail(AUTH_BOOST_CODE.DEVICE_REVOKED, '设备验证已失效，请重新登录')
  }
  const employee = database.employees.find((e) => e.id === record.employeeId)
  if (!employee || employee.status !== 1) return fail(AUTH_CODE.ACCOUNT_DISABLED)

  const endError = endAdmissionError(record.clientType, employee.role, record.as)
  if (endError) return fail(endError)

  // 设备场景验证码按 employeeId 暂存（页面不回传明文手机号）
  const codeError = verifyCode(database, 'DEVICE_VERIFY', employee.id, code)
  if (codeError) return fail(codeError)

  database.deviceTickets.delete(ticket)
  const session = issueSession(database, employee, record.deviceId)
  trustDevice(database, employee, {
    deviceId: record.deviceId,
    platform: record.clientType === 'WEB' ? 'WEB' : 'H5'
  })
  addLoginLog({ username: employee.username, employeeId: employee.id, result: 1, reason: '设备二次验证通过' })
  return ok({ ...session, deviceTrusted: true })
}

/** C1 本人设备列表（仅本人；IP 脱敏） */
function listDevices({ db: database, user }) {
  const meta = database.sessionMeta.get(user.id)
  const currentDeviceId = meta ? meta.deviceId : null
  return ok(
    database.trustedDevices
      .filter((d) => d.employee_id === user.id && d.revoked === 0)
      .map((d) => ({
        deviceId: d.device_id,
        platform: d.platform,
        model: d.model,
        lastIp: maskIp(d.last_ip),
        lastSeenTime: d.last_seen_time,
        current: d.device_id === currentDeviceId
      }))
  )
}

/** C2 撤销本人设备（撤销当前设备等价登出；会话维度精准吊销） */
function revokeDevice({ db: database, pathParams, user }) {
  const deviceId = String(pathParams.deviceId || '')
  const row = deviceRow(database, user.id, deviceId)
  if (!row || row.revoked === 1) return fail(AUTH_BOOST_CODE.DEVICE_REVOKED)
  row.revoked = 1
  row.update_time = formatDateTime(new Date())
  persistTrustedDevices()
  const meta = database.sessionMeta.get(user.id)
  if (meta && meta.deviceId === deviceId) {
    database.sessions.delete(user.id)
    database.sessionMeta.delete(user.id)
  }
  return ok(null)
}

/** 1×1 透明 PNG（演示占位；图形验证码在演示态默认不启用 —— demo-login-redesign §6） */
const CAPTCHA_PLACEHOLDER =
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII='

/** D1 图形验证码（契约对齐用；演示态默认不启用，返回不含任何可复用凭据） */
function captcha() {
  return ok({ ticket: `captcha.${createJti()}`, imageBase64: CAPTCHA_PLACEHOLDER, expireIn: 120 })
}

export const authRoutes = [
  { method: 'post', path: '/auth/login', auth: false, handler: login },
  { method: 'post', path: '/auth/logout', roles: ALL_ROLES, handler: logout },
  { method: 'get', path: '/auth/me', roles: ALL_ROLES, handler: me },
  { method: 'put', path: '/auth/password', roles: ALL_ROLES, handler: updatePassword },
  // 新增：A1/A2/B2/D1 公开白名单（auth:false），C1/C2 需登录态且限本人
  { method: 'post', path: '/auth/sms/send', auth: false, handler: smsSend },
  { method: 'post', path: '/auth/sms/login', auth: false, handler: smsLogin },
  { method: 'post', path: '/auth/device/verify', auth: false, handler: deviceVerify },
  { method: 'get', path: '/auth/devices', roles: ALL_ROLES, handler: listDevices },
  { method: 'delete', path: '/auth/devices/:deviceId', roles: ALL_ROLES, handler: revokeDevice },
  { method: 'get', path: '/auth/captcha', auth: false, handler: captcha }
]
