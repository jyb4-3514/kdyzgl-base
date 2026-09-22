import { AUTH_CODE, CODE } from '../../constants/errorCode.js'
import { ALL_ROLES } from '../../constants/role.js'
import { db, toEmployeeVO } from '../db.js'
import { fail, formatDateTime, ok } from '../util.js'
import { isBlank, isStrongPassword } from '../validate.js'

/**
 * 认证接口（api.md 4.1，4 个）
 * login 为白名单（route.auth=false）；其余需登录态，由 engine 统一校验 token 与单会话 jti
 */

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

  const jti = createJti()
  // 同账号覆盖写会话：旧 token 因 jti 不匹配立即失效（互踢）
  database.sessions.set(employee.id, jti)
  employee.last_login_time = formatDateTime(new Date())
  addLoginLog({ username, employeeId: employee.id, result: 1, reason: null })

  return ok({
    token: `mock.${employee.id}.${jti}`,
    expiresIn: 86400,
    employee: toEmployeeVO(employee)
  })
}

function logout({ db: database, user }) {
  // 幂等：无会话也返回成功
  database.sessions.delete(user.id)
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
  // 改密后强制下线（api.md 4.1.4）：前端随后跳登录页
  database.sessions.delete(user.id)
  return ok(null)
}

export const authRoutes = [
  { method: 'post', path: '/auth/login', auth: false, handler: login },
  { method: 'post', path: '/auth/logout', roles: ALL_ROLES, handler: logout },
  { method: 'get', path: '/auth/me', roles: ALL_ROLES, handler: me },
  { method: 'put', path: '/auth/password', roles: ALL_ROLES, handler: updatePassword }
]
