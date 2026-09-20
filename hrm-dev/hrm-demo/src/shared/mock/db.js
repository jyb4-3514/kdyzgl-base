import {
  createRandom,
  randomInt,
  randomWeighted,
  pickOne,
  formatDate,
  formatDateTime,
  parseTime,
  shiftDays,
  todayStart,
  maskPhone
} from './util.js'
import { WORK_ORDER_SLA_HOURS } from '../constants/dict.js'
import { PersistedSessionMap } from './sessionStore.js'

/** 相对日期偏移 n 分钟（同步日志/工单节点时间用） */
const plusMinutes = (date, n) => new Date(new Date(date).getTime() + n * 60000)

/**
 * 内存数据库（种子实体 + 运行时状态）
 * - 规模刻意对齐 db.md/api.md 看板示例：驿站 8 / 部门 6 / 员工 56，避免「文档 8 个驿站、演示显示 12 个」的疑问
 * - 全部数据由固定种子 PRNG 生成：同一天、同参数重新生成结果一致（刷新不变）
 * - 密码：Mock 内以明文占位（真实环境是后端 BCrypt 散列，Mock 不做散列的原因是无法在前端复现 argon 成本且演示无需安全性）
 */

/** 演示密码：仅存在于 Mock 数据中，不是任何环境的真实凭据 */
export const DEMO_PASSWORD = 'demo1234'

/**
 * 数据代际号：每次 resetDb() 自增。
 * 为什么需要：配置中心的「配置项/选项集/全局默认/驿站覆盖」落在独立 localStorage 覆盖层，
 * 但覆盖层的迁移结果依赖 db.syncConfigs 的种子值；调用方（入口页重置、校验脚本）执行 resetDb()
 * 后，覆盖层必须丢弃快照、按新种子重建，否则会残留上一轮演示的改写值。
 * 用代际号做「是否重建」判定，比反向依赖 store 更简单，也不引入循环依赖。
 */
export let dbGeneration = 0

/** 驿站种子：id 即二期 parcel.station_id 的取值域；8 号刻意停用，用于覆盖 4004（停用驿站不可归属员工） */
const STATION_SEED = [
  { code: 'ST001', stationName: '城东驿站', contactPerson: '王建国', address: '示例市河东区解放路 1 号' },
  { code: 'ST002', stationName: '城西驿站', contactPerson: '刘敏', address: '示例市河西区文昌路 22 号' },
  { code: 'ST003', stationName: '城南驿站', contactPerson: '陈志强', address: '示例市城南区向阳路 8 号' },
  { code: 'ST004', stationName: '城北驿站', contactPerson: '赵丽华', address: '示例市城北区前进路 35 号' },
  { code: 'ST005', stationName: '高新驿站', contactPerson: '孙浩', address: '示例市高新区科技大道 66 号' },
  { code: 'ST006', stationName: '大学城驿站', contactPerson: '周晓雨', address: '示例市大学城学府路 12 号' },
  { code: 'ST007', stationName: '老城驿站', contactPerson: '吴国强', address: '示例市老城区中山街 101 号' },
  { code: 'ST008', stationName: '开发区分站', contactPerson: '郑鹏', address: '示例市经开区创业路 3 号' }
]

/** 部门种子：1 为根，2 有子部门（覆盖 3002），2/3/4/5 有直属员工（覆盖 3003），6 无子部门无员工（可删除） */
const DEPARTMENT_SEED = [
  { parentId: 0, deptName: '总公司', sortOrder: 1 },
  { parentId: 1, deptName: '运营部', sortOrder: 1 },
  { parentId: 1, deptName: '人事行政部', sortOrder: 2 },
  { parentId: 1, deptName: '财务部', sortOrder: 3 },
  { parentId: 1, deptName: '技术部', sortOrder: 4 },
  { parentId: 2, deptName: '城东片区组', sortOrder: 1 }
]

/** 固定演示账号（demo-design.md 3.3）：顺序即 id，便于文档与演示话术对照 */
const FIXED_EMPLOYEES = [
  {
    username: 'admin',
    realName: '系统管理员',
    phone: '13800000000',
    gender: 0,
    deptId: 1,
    stationId: null,
    role: 'ADMIN',
    pwdChanged: 1
  },
  {
    username: 'admin_pwd0',
    realName: '演示管理员',
    phone: '13800000001',
    gender: 1,
    deptId: 3,
    stationId: null,
    role: 'ADMIN',
    pwdChanged: 0
  },
  {
    username: 'st001_admin',
    realName: '王城东',
    phone: '13800000002',
    gender: 1,
    deptId: 2,
    stationId: 1,
    role: 'STATION_ADMIN',
    pwdChanged: 1
  },
  {
    username: 'st001_staff',
    realName: '李小明',
    phone: '13800000003',
    gender: 2,
    deptId: 2,
    stationId: 1,
    role: 'STAFF',
    pwdChanged: 1
  }
]

const SURNAMES =
  '王李张刘陈杨黄赵周吴徐孙马朱胡林郭何高罗郑梁谢宋唐许韩冯邓曹彭曾萧田董袁潘于蒋蔡余杜叶程苏魏吕丁任沈姚卢姜崔钟谭陆汪范金石廖贾夏韦付方白邹孟熊秦邱江尹薛闫段雷侯龙史陶黎贺顾毛郝龚邵万钱严覃武戴莫孔向汤'
const GIVEN_NAMES = [
  '伟',
  '芳',
  '娜',
  '敏',
  '静',
  '丽',
  '强',
  '磊',
  '军',
  '洋',
  '勇',
  '艳',
  '杰',
  '娟',
  '涛',
  '明',
  '超',
  '秀英',
  '霞',
  '平',
  '刚',
  '桂英',
  '文',
  '辉',
  '建华',
  '雪',
  '婷',
  '波',
  '斌',
  '宇',
  '晨',
  '阳',
  '琳',
  '楠',
  '鑫',
  '倩',
  '婷玉',
  '浩然',
  '思远',
  '子涵'
]

/** 生成拼音风格登录账号：姓氏拼音 + 序号，满足 ^[a-zA-Z][a-zA-Z0-9_]{3,29}$ 且天然唯一 */
const USERNAME_PINYIN = [
  'wang',
  'li',
  'zhang',
  'liu',
  'chen',
  'yang',
  'huang',
  'zhao',
  'zhou',
  'wu',
  'xu',
  'sun',
  'ma',
  'zhu',
  'hu',
  'lin',
  'guo',
  'he',
  'gao',
  'luo',
  'zheng',
  'liang',
  'xie',
  'song',
  'tang',
  'xu2',
  'han',
  'feng',
  'deng',
  'cao',
  'peng',
  'zeng',
  'tian',
  'dong',
  'yuan',
  'pan',
  'jiang',
  'cai',
  'yu',
  'du'
]

/** 运行时库：种子实体 + 会话 + 自增序号 */
export const db = {
  stations: [],
  departments: [],
  employees: [],
  loginLogs: [],
  syncTasks: [],
  syncLogs: [],
  syncConfigs: [],
  workOrders: [],
  workOrderTransfers: [],
  dispatchRules: [],
  notifications: [],
  /** 单会话/账号（api.md 3.1）：employeeId → jti，用于演示被顶下线/被强制下线的 401；落盘以支持刷新不丢登录态（见 sessionStore.js） */
  sessions: new PersistedSessionMap(),
  seq: {
    employee: 0,
    department: 0,
    station: 0,
    loginLog: 0,
    syncTask: 0,
    syncLog: 0,
    syncConfig: 0,
    workOrder: 0,
    workOrderTransfer: 0,
    dispatchRule: 0,
    notification: 0
  }
}

/**
 * 重建全部种子实体（不动会话）
 * 为什么与 resetDb 拆开：模块加载时要构建种子，但**绝不能**顺手清会话——
 * 会话已落盘（sessionStore.js），加载即清会让「刷新不丢登录态」形同虚设。
 */
function buildSeed() {
  dbGeneration += 1
  const random = createRandom(20260917)
  const stations = buildStations()
  const departments = buildDepartments()
  const employees = buildEmployees(random, departments, stations)
  const loginLogs = buildLoginLogs(random, employees)
  // 二三期实体用独立种子，避免与员工/登录日志的随机序列耦合（各自数据稳定可复现）
  const syncTasks = buildSyncTasks()
  const syncLogs = buildSyncLogs(syncTasks)
  const syncConfigs = buildSyncConfigs()
  const dispatchRules = buildDispatchRules()
  const workOrders = buildWorkOrders(employees)
  // 转单留痕在工单之后生成：它会同步改写工单的当前处理人与时间线，保证详情页留痕与工单状态自洽
  const workOrderTransfers = buildWorkOrderTransfers(workOrders, employees)
  const notifications = buildNotifications(employees)

  db.stations = stations
  db.departments = departments
  db.employees = employees
  db.loginLogs = loginLogs
  db.syncTasks = syncTasks
  db.syncLogs = syncLogs
  db.syncConfigs = syncConfigs
  db.workOrders = workOrders
  db.workOrderTransfers = workOrderTransfers
  db.dispatchRules = dispatchRules
  db.notifications = notifications
  db.seq.employee = employees.length
  db.seq.department = departments.length
  db.seq.station = stations.length
  db.seq.loginLog = loginLogs.length
  db.seq.syncTask = syncTasks.length
  db.seq.syncLog = syncLogs.length
  db.seq.syncConfig = syncConfigs.length
  db.seq.workOrder = workOrders.length
  db.seq.workOrderTransfer = workOrderTransfers.length
  db.seq.dispatchRule = dispatchRules.length
  db.seq.notification = notifications.length
}

/** 重建全部种子数据并清会话（T16「重置演示数据」直接复用本函数；clear 会一并清掉会话快照） */
export function resetDb() {
  buildSeed()
  db.sessions.clear()
}

function buildStations() {
  return STATION_SEED.map((item, index) => {
    const id = index + 1
    return {
      id,
      code: item.code,
      station_name: item.stationName,
      contact_person: item.contactPerson,
      contact_phone: `13700000${String(id).padStart(3, '0')}`,
      address: item.address,
      // 8 号驿站停用：用于覆盖 4004 分支；其余启用保证 4001（不存在）/4004（停用）语义可区分
      status: id === 8 ? 0 : 1,
      remark: null,
      is_deleted: 0,
      create_time: formatDateTime(shiftDays(-220 + id, 10, 0, 0)),
      update_time: formatDateTime(shiftDays(-220 + id, 10, 0, 0))
    }
  })
}

function buildDepartments() {
  return DEPARTMENT_SEED.map((item, index) => {
    const id = index + 1
    return {
      id,
      parent_id: item.parentId,
      dept_name: item.deptName,
      sort_order: item.sortOrder,
      is_deleted: 0,
      create_time: formatDateTime(shiftDays(-260 + id, 9, 30, 0)),
      update_time: formatDateTime(shiftDays(-260 + id, 9, 30, 0))
    }
  })
}

function buildEmployees(random, departments, stations) {
  const list = FIXED_EMPLOYEES.map((item, index) => {
    const id = index + 1
    return {
      id,
      username: item.username,
      password: DEMO_PASSWORD,
      real_name: item.realName,
      phone: item.phone,
      gender: item.gender,
      dept_id: item.deptId,
      station_id: item.stationId,
      role: item.role,
      status: 1,
      pwd_changed: item.pwdChanged,
      entry_date: formatDate(shiftDays(-600 + id * 3, 0, 0, 0)),
      last_login_time: null,
      remark: null,
      is_deleted: 0,
      create_time: formatDateTime(shiftDays(-600 + id * 3, 10, 0, 0)),
      update_time: formatDateTime(shiftDays(-600 + id * 3, 10, 0, 0))
    }
  })

  // 2-7 号启用驿站各配 1 名站长（1 号站已由 st001_admin 覆盖）：员工端「站长视角」每个启用驿站都可演示
  for (let stationId = 2; stationId <= 7; stationId += 1) {
    list.push(createEmployee(random, list.length + 1, { role: 'STATION_ADMIN', stationId, deptId: 2 }))
  }

  // 第 3 个全局管理员：用于演示「最后一个可用管理员」保护链路（2002）的防御分支
  list.push(createEmployee(random, list.length + 1, { role: 'ADMIN', stationId: null, deptId: 5 }))

  // 其余为普通员工：仅归属 1-7 号启用驿站（8 号站停用且无员工 → 驿站删除校验 4003 可正反验证）
  const deptPool = [2, 3, 4, 5]
  const deptWeights = [6, 3, 2, 1]
  while (list.length < 56) {
    const id = list.length + 1
    list.push(
      createEmployee(random, id, {
        role: 'STAFF',
        stationId: randomInt(random, 1, 7),
        deptId: deptPool[randomWeighted(random, deptWeights)]
      })
    )
  }

  // 末位员工置为禁用：覆盖列表 status=0 筛选与看板「含禁用」口径
  const last = list[list.length - 1]
  last.status = 0
  last.remark = '演示用禁用账号'

  // 今日登录：固定挑 23 人（对齐 api.md 4.2.1 示例 todayLoginCount=23），其余人分布在一周内
  const todayLogins = list.slice(0, 23)
  todayLogins.forEach((emp, index) => {
    emp.last_login_time = formatDateTime(shiftDays(0, 7 + (index % 11), index % 60, 0))
  })
  list.slice(23).forEach((emp, index) => {
    emp.last_login_time = formatDateTime(shiftDays(-1 - (index % 6), 8 + (index % 10), (index * 7) % 60, 0))
  })

  // 部门/驿站 id 兜底：种子自造数据保证引用有效（3001/4001 的非法值由接口入参触发）
  const deptIds = new Set(departments.map((d) => d.id))
  const stationIds = new Set(stations.map((s) => s.id))
  list.forEach((emp) => {
    if (emp.dept_id != null && !deptIds.has(emp.dept_id)) emp.dept_id = 2
    if (emp.station_id != null && !stationIds.has(emp.station_id)) emp.station_id = 1
  })

  return list
}

function createEmployee(random, id, { role, stationId, deptId }) {
  const realName = `${pickOne(random, SURNAMES.split(''))}${pickOne(random, GIVEN_NAMES)}`
  return {
    id,
    username: `${USERNAME_PINYIN[(id * 3) % USERNAME_PINYIN.length]}${String(id).padStart(2, '0')}`,
    password: DEMO_PASSWORD,
    real_name: realName,
    phone: `139${String(10000000 + id * 137).slice(0, 8)}`,
    gender: randomInt(random, 1, 2),
    dept_id: deptId,
    station_id: stationId,
    role,
    status: 1,
    pwd_changed: 1,
    entry_date: formatDate(shiftDays(-500 + id * 5, 0, 0, 0)),
    last_login_time: null,
    remark: null,
    is_deleted: 0,
    create_time: formatDateTime(shiftDays(-500 + id * 5, 9, 15, 0)),
    update_time: formatDateTime(shiftDays(-500 + id * 5, 9, 15, 0))
  }
}

function buildLoginLogs(random, employees) {
  const logs = []
  let id = 0
  const push = (emp, result, loginTime, failReason = null) => {
    id += 1
    logs.push({
      id,
      username: emp ? emp.username : 'unknown_user',
      employee_id: emp ? emp.id : null,
      login_ip: '127.0.0.1',
      login_result: result,
      fail_reason: failReason,
      user_agent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) DemoBrowser/1.0',
      login_time: loginTime
    })
  }

  const todayStartTs = todayStart()
  // 今日：23 人去重成功 + 3 条重复登录（验证看板「去重员工数」口径）+ 3 条失败
  employees.slice(0, 23).forEach((emp, index) => {
    push(emp, 1, formatDateTime(shiftDays(0, 7 + (index % 11), index % 60, 0)))
  })
  employees.slice(0, 3).forEach((emp, index) => {
    push(emp, 1, formatDateTime(shiftDays(0, 12 + index, index * 13, 30)))
  })
  ;[
    ['nobody01', '用户名或密码错误'],
    ['nobody02', '用户名或密码错误'],
    ['staff_disabled', '账号已禁用，请联系管理员']
  ].forEach(([username, reason], index) => {
    id += 1
    logs.push({
      id,
      username,
      employee_id: null,
      login_ip: '127.0.0.1',
      login_result: 0,
      fail_reason: reason,
      user_agent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) DemoBrowser/1.0',
      login_time: formatDateTime(shiftDays(0, 9 + index, 5, 0))
    })
  })

  // 历史 6 天：每天随机 10-20 人成功登录，供趋势与登录记录演示
  for (let day = 1; day <= 6; day += 1) {
    const count = randomInt(random, 10, 20)
    for (let i = 0; i < count; i += 1) {
      const emp = pickOne(random, employees)
      push(emp, 1, formatDateTime(shiftDays(-day, randomInt(random, 7, 20), randomInt(random, 0, 59), 0)))
    }
  }

  // 兜底：今日成功登录的去重员工数必须正好 23（看板口径强依赖）
  const todaySuccessIds = new Set(
    logs
      .filter(
        (l) =>
          l.login_result === 1 && l.employee_id && new Date(l.login_time.replace(' ', 'T')).getTime() >= todayStartTs
      )
      .map((l) => l.employee_id)
  )
  if (todaySuccessIds.size !== 23) {
    throw new Error(`种子登录日志口径异常：今日去重登录员工数应为 23，实际 ${todaySuccessIds.size}`)
  }

  return logs
}

/* ==================== 二三期种子实体（T07/T08/T09） ==================== */

/** 同步任务：8 驿站 × 近 30 天 = 240 条；状态机四态与失败/重试路径按 plan.md 3.2 */
function buildSyncTasks() {
  const random = createRandom(0xabc123)
  const tasks = []
  let id = 0
  for (let sid = 1; sid <= 8; sid += 1) {
    for (let day = 0; day < 30; day += 1) {
      id += 1
      // 今天偶站执行中、奇站待领取；城东昨天失败（S5 演示点）、老城 3 天前失败（供 6001 校验多一条失败样本）
      let status = 2
      if (day === 0) status = sid % 2 === 0 ? 1 : 0
      if (day === 1 && sid === 1) status = 3
      if (day === 3 && sid === 7) status = 3

      const parcelTotal = randomInt(random, 300, 900)
      const failCount = status === 3 ? randomInt(random, 1, 20) : 0
      const createDate = shiftDays(-day, 6 + (sid % 5), (sid * 7) % 60, 0)
      const createTime = formatDateTime(createDate)
      const startTime = status === 1 || status === 2 || status === 3 ? formatDateTime(plusMinutes(createDate, 5)) : null
      const finishTime =
        status === 2 || status === 3 ? formatDateTime(plusMinutes(new Date(startTime), status === 3 ? 2 : 10)) : null

      tasks.push({
        id,
        station_id: sid,
        batch_no: `B${String(sid).padStart(2, '0')}-${formatDate(createDate).replace(/-/g, '')}`,
        status,
        parcel_total: parcelTotal,
        success_count: status === 2 ? parcelTotal : 0,
        fail_count: failCount,
        retry_count: 0,
        error_msg: status === 3 ? '目标系统响应超时（演示示例）' : null,
        assign_time: null,
        start_time: startTime,
        finish_time: finishTime,
        create_time: createTime,
        update_time: finishTime || startTime || createTime
      })
    }
  }
  return tasks
}

/** 同步日志：每任务 3-8 条，总量约 1300 条；失败任务带 warn/error 级别，供日志抽屉演示 */
function buildSyncLogs(tasks) {
  const random = createRandom(0xdeadbeef)
  const logs = []
  let id = 0
  const push = (task, level, message, logTime) => {
    id += 1
    logs.push({ id, task_id: task.id, batch_no: task.batch_no, level, message, log_time: logTime })
  }

  tasks.forEach((task) => {
    const start = task.start_time || task.create_time
    if (task.status === 3) {
      push(task, 0, '开始执行同步', start)
      push(task, 1, '目标系统响应缓慢，重试第 1 次', start)
      push(task, 2, task.error_msg, task.finish_time || start)
      push(task, 0, '任务结束，状态：失败', task.finish_time || start)
    } else if (task.status === 1) {
      push(task, 0, '开始执行同步', start)
      push(task, 0, '正在解析包裹数据', start)
      push(task, 0, '执行中，请稍候', start)
    } else if (task.status === 2) {
      push(task, 0, '开始执行同步', start)
      push(task, 0, `成功写入 ${task.success_count} 条`, task.finish_time || start)
      push(task, 0, '任务结束，状态：成功', task.finish_time || start)
    } else {
      push(task, 0, `批次 ${task.batch_no} 已创建`, task.create_time)
      push(task, 0, '等待领取执行', task.create_time)
    }
    const extra = randomInt(random, 0, 5)
    for (let k = 0; k < extra; k += 1) {
      push(task, 0, `数据校验通过：第 ${k + 1} 批`, start)
    }
  })
  return logs
}

/**
 * 驿站采集配置（sync_config）：8 个驿站各一条。
 * enabled 是「采集开关」，status 是「配置行是否停用」，两者语义不同，据此派生四种采集状态：
 * - 已停用（DISABLED）：status = 0（整行停用，不再采集）
 * - 未配置（UNCONFIGURED）：status = 1 但 enabled = 0 且未选数据源（建了配置行、还没完成采集配置）
 * - 异常（ABNORMAL）：已启用采集且最近一次采集失败
 * - 正常（NORMAL）：已启用采集且最近一次采集成功 / 进行中
 * 让每站都有一行配置而非「缺行」，三端看板可直接铺表格，无需区分「查不到」与「未配置」两种空态。
 */
const SYNC_CONFIG_SEED = [
  { stationId: 1, enabled: 1, frequency: 'HOURLY', dataSource: '多多买菜', lastCollectStatus: 'SUCCESS', status: 1 },
  { stationId: 2, enabled: 1, frequency: 'EVERY_2H', dataSource: '菜鸟裹裹', lastCollectStatus: 'SUCCESS', status: 1 },
  { stationId: 3, enabled: 1, frequency: 'EVERY_4H', dataSource: '多多买菜', lastCollectStatus: 'SUCCESS', status: 1 },
  { stationId: 4, enabled: 1, frequency: 'DAILY', dataSource: '京东物流', lastCollectStatus: 'SUCCESS', status: 1 },
  { stationId: 5, enabled: 1, frequency: 'EVERY_2H', dataSource: '多多买菜', lastCollectStatus: 'FAILED', status: 1 },
  // 6 号数据源刻意用一个「不在受管选项集里」的历史文本：迁移时应自动纳管为 source=MIGRATED 选项，
  // 而不是置空（置空会落下「启用采集却没有数据源」的矛盾配置，见设计 A.4.2）
  { stationId: 6, enabled: 1, frequency: 'HOURLY', dataSource: '丰巢智能柜', lastCollectStatus: 'FAILED', status: 1 },
  { stationId: 7, enabled: 0, frequency: 'EVERY_4H', dataSource: null, lastCollectStatus: 'NEVER', status: 1 },
  { stationId: 8, enabled: 0, frequency: 'DAILY', dataSource: null, lastCollectStatus: 'NEVER', status: 0 }
]

function buildSyncConfigs() {
  return SYNC_CONFIG_SEED.map((item, index) => ({
    id: index + 1,
    station_id: item.stationId,
    enabled: item.enabled,
    frequency: item.frequency,
    data_source: item.dataSource,
    collect_start_time: '08:00',
    collect_end_time: '20:00',
    // 采集过的站点给一个「近两天内」的最近采集时间；从未采集的留空，前端按 lastCollectStatus=NEVER 展示
    last_collect_time:
      item.lastCollectStatus === 'NEVER'
        ? null
        : formatDateTime(shiftDays(-(item.stationId % 2), 6 + item.stationId, item.stationId * 5, 0)),
    last_collect_status: item.lastCollectStatus,
    status: item.status,
    update_time: formatDateTime(shiftDays(-30 + item.stationId, 9, 0, 0))
  }))
}

/**
 * 企微自动派单规则种子：群消息关键词 → 工单类型 / 优先级。
 * default_assignee_id 统一留空：规则不绑定驿站，默认指派某站员工会产生「跨站指派」的矛盾数据，
 * 正式实现由规则配置页按驿站选择处理人（见 workOrder.js 的 auto-dispatch 说明）。
 */
const DISPATCH_RULE_SEED = [
  { keyword: '破损', workOrderType: 1, priority: 2 },
  { keyword: '丢失', workOrderType: 1, priority: 2 },
  { keyword: '投诉', workOrderType: 3, priority: 1 },
  { keyword: '延迟', workOrderType: 4, priority: 0 },
  { keyword: '错分', workOrderType: 1, priority: 1 }
]

function buildDispatchRules() {
  return DISPATCH_RULE_SEED.map((item, index) => ({
    id: index + 1,
    keyword: item.keyword,
    work_order_type: item.workOrderType,
    priority: item.priority,
    default_assignee_id: null,
    enabled: 1,
    create_time: formatDateTime(shiftDays(-60, 9, 0, 0)),
    update_time: formatDateTime(shiftDays(-60, 9, 0, 0))
  }))
}

/** 工单：120 条，含 6 条超 SLA、20 条已关闭；sla_deadline 按优先级（低 48h/中 24h/高 8h）计算 */
function buildWorkOrders(employees) {
  const random = createRandom(0x1234567)
  const orders = []
  let id = 0
  let orderSeq = 0
  const active = employees.filter((e) => e.is_deleted === 0)
  const admins = active.filter((e) => e.role === 'ADMIN')
  const staffOf = (sid) => active.filter((e) => e.station_id === sid)
  const pickReporter = (sid) => pickOne(random, staffOf(sid).concat(admins))
  const pickAssignee = (sid) => {
    const pool = staffOf(sid)
    return pool.length ? pickOne(random, pool) : pickOne(random, admins)
  }
  const TITLES = {
    1: ['包裹破损待核', '包裹错分驿站', '包裹丢失待查'],
    2: ['扫码枪故障', '货架损坏', '门禁异常'],
    3: ['客户投诉取件慢', '客户投诉服务态度', '客户投诉包裹损坏'],
    4: ['其他事项待处理']
  }

  const makeOrder = ({ stationId, type, status, priority, createDate, assigneeId }) => {
    id += 1
    orderSeq += 1
    const reporter = pickReporter(stationId)
    const orderNo = `WO-${formatDate(createDate).replace(/-/g, '')}-${String(orderSeq).padStart(4, '0')}`
    const slaDeadline = plusMinutes(createDate, WORK_ORDER_SLA_HOURS[priority] * 60)
    const resolvedTime = status >= 2 ? formatDateTime(plusMinutes(createDate, 30)) : null
    const closedTime = status === 3 ? formatDateTime(plusMinutes(createDate, 60)) : null
    const handleLog = [
      { time: formatDateTime(createDate), action: 'create', operatorName: reporter.real_name, content: '创建工单' }
    ]
    if (status >= 1)
      handleLog.push({
        time: formatDateTime(plusMinutes(createDate, 10)),
        action: 'accept',
        operatorName: assigneeId ? '处理人' : '系统',
        content: '接单处理'
      })
    if (status >= 2)
      handleLog.push({
        time: resolvedTime,
        action: 'resolve',
        operatorName: assigneeId ? '处理人' : '系统',
        content: '标记已解决'
      })
    if (status === 3) handleLog.push({ time: closedTime, action: 'close', operatorName: '系统', content: '关闭工单' })

    orders.push({
      id,
      order_no: orderNo,
      type,
      status,
      priority,
      title: TITLES[type][(orderSeq + type) % TITLES[type].length],
      content: '演示工单内容：用于验证四态流转、SLA 倒计时与通知联动',
      station_id: stationId,
      parcel_id: null,
      waybill_no: null,
      reporter_id: reporter.id,
      assignee_id: assigneeId,
      // 来源：种子全部为手工新建；企微自动派发写入 AUTO_WECHAT（见 workOrder.js）
      source: 'MANUAL',
      sla_deadline: formatDateTime(slaDeadline),
      resolved_time: resolvedTime,
      closed_time: closedTime,
      handle_log: JSON.stringify(handleLog),
      is_deleted: 0,
      create_time: formatDateTime(createDate),
      update_time: closedTime || resolvedTime || formatDateTime(createDate)
    })
  }

  // 6 条超 SLA：创建时间 2-3 天前 + 高/中优先级，sla_deadline 必然已过
  for (let i = 0; i < 6; i += 1) {
    makeOrder({
      stationId: (i % 7) + 1,
      type: (i % 4) + 1,
      status: i % 2 === 0 ? 0 : 1,
      priority: i % 2 === 0 ? 2 : 1,
      createDate: shiftDays(-2 - (i % 2), 8 + i, i * 3, 0),
      assigneeId: i % 2 === 0 ? null : pickAssignee((i % 7) + 1).id
    })
  }
  // 20 条已关闭：近 15 天内，已解决后再关闭
  for (let i = 0; i < 20; i += 1) {
    makeOrder({
      stationId: (i % 7) + 1,
      type: (i % 4) + 1,
      status: 3,
      priority: i % 3,
      createDate: shiftDays(-1 - (i % 15), 9, i, 0),
      assigneeId: pickAssignee((i % 7) + 1).id
    })
  }
  // 其余 94 条：近 4 小时内创建，保证不会超 SLA（设计口径恰好 6 条超 SLA）
  for (let i = 0; i < 94; i += 1) {
    const status = i < 40 ? 0 : i < 70 ? 1 : 2
    makeOrder({
      stationId: (i % 7) + 1,
      type: (i % 4) + 1,
      status,
      priority: i % 3,
      createDate: new Date(Date.now() - (i % 5) * 3600000),
      assigneeId: status === 0 ? (i % 3 === 0 ? pickAssignee((i % 7) + 1).id : null) : pickAssignee((i % 7) + 1).id
    })
  }
  return orders
}

/** 工单时间线解析：handle_log 以 JSON 字符串落库，脏数据一律当空数组，避免一条坏记录打挂详情接口 */
export function parseHandleLog(raw) {
  try {
    return typeof raw === 'string' ? JSON.parse(raw) : raw || []
  } catch (e) {
    return []
  }
}

/**
 * 工单转单留痕：至少 8 条工单带转单记录（1 条管理员跨站 + 多条站内 / 处理人本人转单）。
 * 为什么在这里同步改写工单：详情页要同时展示「当前处理人」与「转单时间线」，
 * 若只造留痕不改工单，就会出现「转给了 A、工单却挂在 B 名下」的矛盾数据。
 */
function buildWorkOrderTransfers(orders, employees) {
  const random = createRandom(0x5b3d91c7) // 独立种子：不与工单主体的随机序列互相干扰
  const active = employees.filter((e) => e.is_deleted === 0 && e.status === 1)
  const admin = active.find((e) => e.role === 'ADMIN') || null
  const nameOf = (id) => {
    const emp = employees.find((e) => e.id === Number(id))
    return emp ? emp.real_name : null
  }
  const REASONS = [
    '原处理人轮休，转交同事跟进',
    '该片区由对方负责，转单处理',
    '原处理人外出取件，转交站内同事',
    '跨站协同：由就近驿站处理',
    '原处理人请假，转交他人接手'
  ]

  // 候选：有处理人且未关闭（关单后再转单不符合驿站处理习惯）
  const candidates = orders.filter((o) => o.is_deleted === 0 && o.assignee_id != null && o.status !== 3)
  const step = Math.max(1, Math.floor(candidates.length / 8))
  const picked = []
  for (let i = 0; picked.length < 8 && i < candidates.length; i += 1) {
    const order = candidates[(i * step) % candidates.length]
    if (!picked.includes(order)) picked.push(order)
  }

  const list = []
  picked.forEach((order, index) => {
    const sid = order.station_id
    // 第 1 条走管理员跨站转单，其余按「站长转单 / 处理人本人转单」交替，覆盖三种权限形态
    const crossStation = index === 0
    const selfService = !crossStation && index % 3 === 2
    const operator = crossStation
      ? admin
      : selfService
        ? active.find((e) => e.id === order.assignee_id)
        : active.find((e) => e.role === 'STATION_ADMIN' && e.station_id === sid)
    if (!operator) return
    const pool = crossStation
      ? active.filter((e) => e.station_id != null && e.station_id !== sid)
      : active.filter((e) => e.station_id === sid)
    const targets = pool.filter((e) => e.id !== operator.id && e.id !== order.assignee_id)
    if (!targets.length) return
    const target = targets[randomInt(random, 0, targets.length - 1)]

    // 转单时间取工单创建后半小时起步，并夹在「现在」之前，避免出现未来时间的留痕
    const time = formatDateTime(
      new Date(Math.min(Date.now() - 60000, parseTime(order.create_time) + (index + 1) * 1800000))
    )
    const fromId = order.assignee_id
    order.assignee_id = target.id
    order.update_time = time

    const log = parseHandleLog(order.handle_log)
    log.push({
      time,
      action: 'transfer',
      operatorName: operator.real_name,
      content: `转单给 ${target.real_name}：${REASONS[index % REASONS.length]}`
    })
    order.handle_log = JSON.stringify(log)

    list.push({
      id: list.length + 1,
      workOrderId: order.id,
      fromEmployeeId: fromId,
      fromEmployeeName: nameOf(fromId),
      toEmployeeId: target.id,
      toEmployeeName: target.real_name,
      reason: REASONS[index % REASONS.length],
      operatorId: operator.id,
      operatorName: operator.real_name,
      transferTime: time
    })
  })
  return list
}

/**
 * 通知：系统联动通知 60 条（type 1-4，部分已读，工单/同步通知带 biz_type/biz_id 供跳转）
 * + 手工发布样本 3 批（scope = ALL / STATION / EMPLOYEE），后者 is_published = 1 且带发布人信息。
 */
function buildNotifications(employees) {
  const random = createRandom(0x2468ace)
  const notifs = []
  const active = employees.filter((e) => e.is_deleted === 0 && e.status === 1)
  const TITLES = { 1: '工单指派', 2: '工单流转', 3: '同步任务失败', 4: '系统公告' }
  const BIZ_TYPE = { 1: 'work_order', 2: 'work_order', 3: 'sync_task', 4: null }
  const push = (row) => notifs.push({ id: notifs.length + 1, ...row })

  for (let i = 0; i < 60; i += 1) {
    const type = randomInt(random, 1, 4)
    const emp = pickOne(random, active)
    const isRead = random() < 0.5 ? 1 : 0
    const createDate = shiftDays(-randomInt(random, 0, 6), randomInt(random, 7, 20), randomInt(random, 0, 59), 0)
    push({
      employee_id: emp.id,
      type,
      title: TITLES[type],
      content: type === 4 ? '演示公告：三端数据同源，刷新后保持一致' : `演示通知 #${i + 1}`,
      biz_type: BIZ_TYPE[type],
      biz_id: type === 4 ? null : randomInt(random, 1, 120),
      is_read: isRead,
      read_time: isRead ? formatDateTime(plusMinutes(createDate, randomInt(random, 5, 120))) : null,
      create_time: formatDateTime(createDate),
      is_published: 0,
      publisher_id: null,
      publisher_name: null,
      publish_scope: null
    })
  }

  // 手工发布样本：发布人统一取首个管理员（与 POST /notifications/publish 的发布人来源一致）
  const publisher = employees.find((e) => e.role === 'ADMIN') || null
  const published = [
    {
      scope: 'ALL',
      title: '系统公告：三端数据已完成同步',
      content: '演示公告：PC 端、老板端与员工端数据同源，刷新后保持一致，可直接对外讲解。',
      targets: active,
      publishAt: shiftDays(-3, 9, 0, 0)
    },
    {
      scope: 'STATION',
      title: '城东驿站设备检修通知',
      content: '城东驿站扫码枪将于本周六维护，请提前备份待入库包裹。',
      targets: active.filter((e) => e.station_id === 1),
      publishAt: shiftDays(-2, 10, 30, 0)
    },
    {
      scope: 'EMPLOYEE',
      title: '演示账号使用说明',
      content: '今日打卡演示入口已预留，进入员工端打卡页可直接完成一次打卡。',
      targets: active.filter((e) => [3, 4].includes(e.id)),
      publishAt: shiftDays(-1, 8, 0, 0)
    }
  ]
  published.forEach((batch) => {
    batch.targets.forEach((emp) => {
      push({
        employee_id: emp.id,
        type: 4,
        title: batch.title,
        content: batch.content,
        biz_type: null,
        biz_id: null,
        is_read: 0,
        read_time: null,
        create_time: formatDateTime(batch.publishAt),
        is_published: 1,
        publisher_id: publisher ? publisher.id : null,
        publisher_name: publisher ? publisher.real_name : null,
        publish_scope: batch.scope
      })
    })
  })
  return notifs
}

/* ==================== 查询辅助（路由层只做参数校验与响应包装） ==================== */

export const activeEmployees = () => db.employees.filter((e) => e.is_deleted === 0)
export const activeDepartments = () => db.departments.filter((d) => d.is_deleted === 0)
export const activeStations = () => db.stations.filter((s) => s.is_deleted === 0)

export const findEmployeeById = (id) => activeEmployees().find((e) => e.id === Number(id))
export const findEmployeeByUsername = (username) => activeEmployees().find((e) => e.username === username)
export const findDepartmentById = (id) => activeDepartments().find((d) => d.id === Number(id))
export const findStationById = (id) => activeStations().find((s) => s.id === Number(id))

export const deptName = (id) => {
  const dept = db.departments.find((d) => d.id === Number(id))
  return dept ? dept.dept_name : null
}
export const stationName = (id) => {
  const station = db.stations.find((s) => s.id === Number(id))
  return station ? station.station_name : null
}

/** 部门 id 及其全部子孙部门 id（api.md 4.3.1：deptId 筛选含子部门，内存递归展开） */
export function deptIdWithChildren(deptId) {
  const ids = [Number(deptId)]
  for (let i = 0; i < ids.length; i += 1) {
    activeDepartments()
      .filter((d) => d.parent_id === ids[i])
      .forEach((d) => {
        if (!ids.includes(d.id)) ids.push(d.id)
      })
  }
  return ids
}

/** 部门直属员工数（不含子部门，api.md 4.4.1 口径） */
export const deptDirectEmployeeCount = (deptId) => activeEmployees().filter((e) => e.dept_id === Number(deptId)).length

/** 驿站归属员工数（api.md 4.5.1 / 删除前置校验 4003 口径） */
export const stationEmployeeCount = (stationId) =>
  activeEmployees().filter((e) => e.station_id === Number(stationId)).length

/** 活跃管理员数量（status=1 且未删除），排除指定 id 用于「最后一个管理员」判定 */
export const activeAdminCount = (excludeId = null) =>
  activeEmployees().filter((e) => e.role === 'ADMIN' && e.status === 1 && e.id !== Number(excludeId)).length

/** 员工实体 → 出参 VO：脱敏手机号、补 deptName/stationName、布尔化 pwdChanged、去掉 password */
export function toEmployeeVO(employee) {
  return {
    id: employee.id,
    username: employee.username,
    realName: employee.real_name,
    phone: maskPhone(employee.phone),
    gender: employee.gender,
    deptId: employee.dept_id,
    deptName: deptName(employee.dept_id),
    stationId: employee.station_id,
    stationName: stationName(employee.station_id),
    role: employee.role,
    status: employee.status,
    pwdChanged: employee.pwd_changed === 1,
    entryDate: employee.entry_date,
    remark: employee.remark,
    lastLoginTime: employee.last_login_time,
    createTime: employee.create_time
  }
}

/* ==================== 二三期查询辅助与运行时写操作 ==================== */

export const employeeName = (id) => {
  const emp = db.employees.find((e) => e.id === Number(id))
  return emp ? emp.real_name : null
}
export const findSyncTaskById = (id) => db.syncTasks.find((t) => t.id === Number(id))
export const findSyncConfigByStation = (stationId) => db.syncConfigs.find((c) => c.station_id === Number(stationId))
export const findWorkOrderById = (id) => db.workOrders.find((o) => o.id === Number(id) && o.is_deleted === 0)
export const findDispatchRuleById = (id) => db.dispatchRules.find((r) => r.id === Number(id))
export const findNotificationById = (id) => db.notifications.find((n) => n.id === Number(id))

/** 采集配置按驿站覆盖式保存：新驿站首存即创建，与 attendanceStore.saveRule 同口径 */
export function saveSyncConfig(stationId, payload = {}) {
  const sid = Number(stationId)
  let config = findSyncConfigByStation(sid)
  if (!config) {
    config = {
      id: (db.seq.syncConfig += 1),
      station_id: sid,
      enabled: 0,
      frequency: 'EVERY_4H',
      data_source: null,
      collect_start_time: '08:00',
      collect_end_time: '20:00',
      last_collect_time: null,
      last_collect_status: 'NEVER',
      status: 1,
      update_time: formatDateTime(new Date())
    }
    db.syncConfigs.push(config)
  }
  // 白名单写入：请求体的 id / stationId / 最近采集状态不接受，防止越权改归属与伪造采集结果
  ;['enabled', 'frequency', 'data_source', 'collect_start_time', 'collect_end_time', 'status'].forEach((key) => {
    if (payload[key] !== undefined) config[key] = payload[key]
  })
  config.update_time = formatDateTime(new Date())
  return { ...config }
}

/** 工单转单留痕：按转单时间倒序返回（详情页时间线要「就近在上」） */
export const listWorkOrderTransfers = (workOrderId) =>
  db.workOrderTransfers
    .filter((t) => t.workOrderId === Number(workOrderId))
    .sort((a, b) => (a.transferTime < b.transferTime ? 1 : -1))
    .map((t) => ({ ...t }))

/** 写入转单留痕（转单接口调用；与 pushNotification 同层，都是「一次性落库 + 回显」的运行时写操作） */
export function pushWorkOrderTransfer({
  workOrderId,
  fromEmployeeId,
  fromEmployeeName,
  toEmployeeId,
  toEmployeeName,
  reason,
  operatorId,
  operatorName,
  transferTime
}) {
  const id = (db.seq.workOrderTransfer += 1)
  const row = {
    id,
    workOrderId: Number(workOrderId),
    fromEmployeeId,
    fromEmployeeName,
    toEmployeeId,
    toEmployeeName,
    reason,
    operatorId,
    operatorName,
    transferTime: transferTime || formatDateTime(new Date())
  }
  db.workOrderTransfers.push(row)
  return { ...row }
}

/** 追加同步日志（seed 与运行时共用；logTime 缺省为当前时间） */
export function pushSyncLog(taskId, batchNo, level, message, logTime) {
  const id = (db.seq.syncLog += 1)
  db.syncLogs.push({
    id,
    task_id: Number(taskId),
    batch_no: batchNo,
    level,
    message,
    log_time: logTime || formatDateTime(new Date())
  })
  return id
}

/**
 * 写入站内信（T09 工单指派/解决联动 + 手工发布共用）
 * 系统联动通知 is_published = 0；手工发布的通知由 publishNotifications 传 isPublished = 1
 */
export function pushNotification({
  employeeId,
  type,
  title,
  content,
  bizType,
  bizId,
  publisherId = null,
  publisherName = null,
  publishScope = null,
  isPublished = 0
}) {
  const id = (db.seq.notification += 1)
  db.notifications.push({
    id,
    employee_id: Number(employeeId),
    type,
    title,
    content,
    biz_type: bizType,
    biz_id: bizId,
    is_read: 0,
    read_time: null,
    create_time: formatDateTime(new Date()),
    is_published: isPublished,
    publisher_id: publisherId,
    publisher_name: publisherName,
    publish_scope: publishScope
  })
  return id
}

// 模块加载即构建种子数据：静态 import 的页面无需等待即可查到数据（只建种子，不动已落盘的会话）
buildSeed()
