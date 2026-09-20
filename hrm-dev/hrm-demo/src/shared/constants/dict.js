/**
 * 业务字典（PC / 移动 / Mock 三端唯一真源）
 *
 * 一期字典值严格对齐 db.md / api.md；二三期字段取 demo-design.md 7.4 的 Demo 契约草案。
 * 本文件由原 PC 侧与移动侧的两份字典副本合并而来：同一批枚举只留一处表述，
 * 页面上看到的「值 → 文案 → 色/形」全部由此收口，避免两端同名不同值、同键不同文案。
 *
 * variant 三档语义（颜色由 type 决定，形态由 variant 决定）：
 *   soft    = 状态描述（默认）
 *   outline = 终态、无动作诉求
 *   solid   = 需立刻行动
 *
 * 两处不可动的硬约束（被 shared/mock 反向依赖）：
 *   WORK_ORDER_SLA_HOURS 键值用于推算 sla_deadline（mock/db.js、mock/routes/workOrder.js）；
 *   COLLECT_FREQUENCY 的键是 legacy code（syncConfigStore 的 legacyCodes 映射目标），不可改成新 code。
 */

/* ==================== 一、一期原有字典（键值与 db.md / api.md 一致，不随合并改动） ==================== */

/** 驿站状态：0=停用，1=启用 */
export const STATION_STATUS = {
  0: { label: '停用', type: 'info' },
  1: { label: '启用', type: 'success' }
}

/** 包裹状态：0=待入库，1=在库待取，2=已取件，3=异常，4=已退回（已退回为终态，用描边） */
export const PARCEL_STATUS = {
  0: { label: '待入库', type: 'info', variant: 'soft' },
  1: { label: '在库待取', type: 'warning', variant: 'soft' },
  2: { label: '已取件', type: 'success', variant: 'soft' },
  3: { label: '异常', type: 'danger', variant: 'soft' },
  4: { label: '已退回', type: 'info', variant: 'outline' }
}

/** 同步任务状态：0=待领取，1=执行中，2=成功，3=失败（plan.md 3.2 四态，不引入第五态） */
export const SYNC_STATUS = {
  0: { label: '待领取', type: 'info', variant: 'outline' },
  1: { label: '执行中', type: 'primary', variant: 'soft' },
  2: { label: '成功', type: 'success', variant: 'soft' },
  3: { label: '失败', type: 'danger', variant: 'soft' }
}

/** 同步日志级别：0=info，1=warn，2=error */
export const SYNC_LOG_LEVEL = {
  0: { label: 'INFO', type: 'info', variant: 'soft' },
  1: { label: 'WARN', type: 'warning', variant: 'soft' },
  2: { label: 'ERROR', type: 'danger', variant: 'soft' }
}

/** 工单类型：1=包裹异常，2=设备故障，3=客户投诉，4=其他 */
export const WORK_ORDER_TYPE = {
  1: { label: '包裹异常' },
  2: { label: '设备故障' },
  3: { label: '客户投诉' },
  4: { label: '其他' }
}

/** 工单状态：0=待处理，1=处理中，2=已解决，3=已关闭（已关闭为终态，用描边） */
export const WORK_ORDER_STATUS = {
  0: { label: '待处理', type: 'warning', variant: 'soft' },
  1: { label: '处理中', type: 'primary', variant: 'soft' },
  2: { label: '已解决', type: 'success', variant: 'soft' },
  3: { label: '已关闭', type: 'info', variant: 'outline' }
}

/** 工单优先级：0=低，1=中，2=高（高优先级需立刻行动，用实心） */
export const WORK_ORDER_PRIORITY = {
  0: { label: '低', type: 'info', variant: 'outline' },
  1: { label: '中', type: 'warning', variant: 'soft' },
  2: { label: '高', type: 'danger', variant: 'solid' }
}

/**
 * 工单 SLA 时长（小时），按优先级计算 sla_deadline
 * TODO(扩展): 三期专项设计定稿后改为可配置（服务端下发），此处先固化为演示口径
 */
export const WORK_ORDER_SLA_HOURS = { 0: 48, 1: 24, 2: 8 }

/**
 * 通知类型：1=工单指派，2=工单流转，3=同步失败，4=系统公告，5/6=请假（M11）
 * 5=请假申请（发给审批人），6=请假结果（发给申请人）。
 * 新增值必须同步放行通知发布接口的类型硬校验（mock/routes/notification.js 的 PUBLISH_TYPES），否则发通知会被拦。
 */
export const NOTIFICATION_TYPE = {
  1: { label: '工单指派' },
  2: { label: '工单流转' },
  3: { label: '同步失败' },
  4: { label: '系统公告' },
  5: { label: '请假申请' },
  6: { label: '请假结果' }
}

/** 打卡类型：ON=上班卡，OFF=下班卡 */
export const CHECK_TYPE = {
  ON: { label: '上班卡' },
  OFF: { label: '下班卡' }
}

/** 打卡方式：记录实际命中的校验项，用于列表展示与异常排查 */
export const CHECK_MODE = {
  WIFI: { label: 'WiFi' },
  LOCATION: { label: '定位' },
  'WIFI+LOCATION': { label: 'WiFi+定位' }
}

/** 打卡状态：ABNORMAL 指 WiFi/定位校验未通过的异常卡，不计入出勤口径 */
export const ATTENDANCE_STATUS = {
  NORMAL: { label: '正常', type: 'success' },
  LATE: { label: '迟到', type: 'warning' },
  EARLY_LEAVE: { label: '早退', type: 'warning' },
  ABNORMAL: { label: '异常', type: 'danger' }
}

/** 校验项组合模式：ALL=全部满足，ANY=任一满足 */
export const MATCH_MODE = {
  ALL: { label: '全部满足' },
  ANY: { label: '任一满足' }
}

/* ==================== 二、两副本有取值差异，统一取 PC 口径（含 variant） ==================== */

/** 采集状态：与 shared/mock/routes/syncConfig.js 的 COLLECT_STATES 逐字一致；异常需立刻处理用实心，已停用无动作诉求用描边 */
export const COLLECT_STATE = {
  NORMAL: { label: '正常', type: 'success', variant: 'soft' },
  ABNORMAL: { label: '异常', type: 'danger', variant: 'solid' },
  UNCONFIGURED: { label: '未配置', type: 'warning', variant: 'soft' },
  DISABLED: { label: '已停用', type: 'info', variant: 'outline' }
}

/** 考核等级：等级色落到既有 4 色族 soft，不新增色族（kpiStore.LEVELS） */
export const KPI_LEVEL = {
  EXCELLENT: { label: '优秀', type: 'success', variant: 'soft' },
  GOOD: { label: '良好', type: 'primary', variant: 'soft' },
  PASS: { label: '合格', type: 'warning', variant: 'soft' },
  IMPROVE: { label: '待改进', type: 'danger', variant: 'soft' }
}

/**
 * 工资单状态（契约 6 态，financeStore.PAYROLL_STATUS_LABEL）：
 * DRAFT / PENDING_APPROVAL / APPROVED / REJECTED / PUBLISHED / CONFIRMED。
 * APPROVED 是契约独有的「已审核待发布」中间态，归入 success 族（与 CONFIRMED 同族，靠步骤条位置区分）。
 */
export const PAYROLL_STATUS = {
  DRAFT: { label: '草稿', type: 'info', variant: 'outline' },
  PENDING_APPROVAL: { label: '待审核', type: 'warning', variant: 'soft' },
  APPROVED: { label: '已通过', type: 'success', variant: 'soft' },
  REJECTED: { label: '已驳回', type: 'danger', variant: 'soft' },
  PUBLISHED: { label: '已发布', type: 'primary', variant: 'soft' },
  CONFIRMED: { label: '已确认', type: 'success', variant: 'soft' }
}

/** 流程状态：与 hrStore 的 FLOW_STATUS_LABEL 一致 */
export const FLOW_STATUS = {
  IN_PROGRESS: { label: '进行中', type: 'primary', variant: 'soft' },
  COMPLETED: { label: '已完成', type: 'success', variant: 'soft' },
  REJECTED: { label: '已驳回', type: 'danger', variant: 'soft' }
}

/* ==================== 三、两副本完全一致，取任一份 ==================== */

/**
 * 采集频次：键是 legacy code，与 syncConfig.js 的 FREQUENCIES 对应，
 * 也是 syncConfigStore 里 legacyCodes 的映射目标，不要改成新 code（新档位在配置中心模型里）
 */
export const COLLECT_FREQUENCY = {
  HOURLY: { label: '每小时' },
  EVERY_2H: { label: '每 2 小时' },
  EVERY_4H: { label: '每 4 小时' },
  DAILY: { label: '每天' }
}

/** 发布范围：与 notification.js 的 PUBLISH_SCOPES 逐字一致 */
export const PUBLISH_SCOPE = {
  ALL: { label: '全员' },
  STATION: { label: '指定驿站' },
  EMPLOYEE: { label: '指定员工' }
}

/** 指标类型：与 kpiStore.KPI_METRIC_TYPE_LABEL 一致 */
export const KPI_METRIC_TYPE = {
  PARCEL: { label: '派件量' },
  PICKUP: { label: '取件及时率' },
  COMPLAINT: { label: '客户投诉' },
  ATTENDANCE: { label: '出勤打卡' },
  SERVICE: { label: '服务评分' },
  WORK_ORDER: { label: '工单处理' },
  TRAINING: { label: '培训完成率' },
  OTHER: { label: '其他' }
}

/** 评分规则（kpiStore.KPI_SCORE_MODE_LABEL） */
export const KPI_SCORE_MODE = {
  LINEAR: { label: '线性折算' },
  TIERED: { label: '阶梯评分' },
  BINARY: { label: '达标即满分' }
}

/** 指标方向（kpiStore.KPI_DIRECTION_LABEL）：越低越好的指标，达成率按「目标 / 实际」折算 */
export const KPI_DIRECTION = {
  UP: { label: '越高越好' },
  DOWN: { label: '越低越好' }
}

/** 工资单项类型：增项 / 扣项（扣项金额本身为正数，语义由 type 承载） */
export const PAYROLL_ITEM_TYPE = {
  ADDITION: { label: '增项' },
  DEDUCTION: { label: '扣项' }
}

/** 离职类型：与 hrStore 的 OFFBOARDING_TYPE_LABEL 一致 */
export const OFFBOARDING_TYPE = {
  RESIGN: { label: '辞职' },
  DISMISS: { label: '辞退' },
  RETIRE: { label: '退休' }
}

/* ==================== 四、原 PC 独有字典 ==================== */

/** 工单来源：MANUAL 手工新建（无动作诉求用描边）/ AUTO_WECHAT 企微群消息自动派发 */
export const WORK_ORDER_SOURCE = {
  MANUAL: { label: '手工', type: 'info', variant: 'outline' },
  AUTO_WECHAT: { label: '企微自动', type: 'primary', variant: 'soft' }
}

/** 指标适用角色：空 = 全员适用（契约口径），与 kpi.js 的 ROLE_SCOPES 一致 */
export const KPI_ROLE_SCOPE = {
  ADMIN: { label: '老板' },
  STATION_ADMIN: { label: '站长' },
  STAFF: { label: '员工' }
}

/** 合同到期预警：≤30 天 warning、已过期 danger（hrStore 派生，无独立状态字段） */
export const CONTRACT_WARN = {
  SOON: { label: '合同将至', type: 'warning', variant: 'soft' },
  EXPIRED: { label: '已过期', type: 'danger', variant: 'soft' }
}

/** 工资单类型：月度工资单 / 离职结算单（离职流程「薪资结算」步骤自动生成） */
export const PAYROLL_BILL_TYPE = {
  MONTHLY: { label: '月度工资单' },
  SETTLEMENT: { label: '离职结算单' }
}

/**
 * 规则项数据来源（financeStore.PAYROLL_ITEM_SOURCE_LABEL）：
 * 四类来源是「积木式配置」的全部可选值，前端不提供自由输入，避免出现无法解析的取数口径；
 * hint 用于表单里解释「这一项的钱从哪来」，属配置说明而非状态文案，故随字典一起托管。
 */
export const PAYROLL_ITEM_SOURCE = {
  FIXED: { label: '人事定薪项', hint: '取人事模块的定薪档案' },
  ATTENDANCE: { label: '考勤推算', hint: '按考勤记录的次数或达标情况计算' },
  KPI: { label: 'KPI 考核', hint: '按 KPI 得分与绩效基数推算' },
  MANUAL: { label: '人工填写', hint: '生成草稿后逐人调整' }
}

/** FIXED 来源可选的定薪字段（与 financeStore.SALARY_FIELD_LABEL 一致） */
export const SALARY_FIELD = {
  basicSalary: { label: '基本工资' },
  postSalary: { label: '岗位工资' },
  performanceBase: { label: '绩效基数' },
  allowances: { label: '津贴合计' }
}

/**
 * ATTENDANCE 来源可选的考勤指标（与 financeStore.ATTENDANCE_LABEL 一致）
 * LEAVE = 已批请假天数（M11 D3 联动）；三处镜像必须同增，见 payrollPreview.js 的保真约定
 */
export const ATTENDANCE_METRIC = {
  LATE: { label: '迟到' },
  EARLY_LEAVE: { label: '早退' },
  ABSENT: { label: '缺勤' },
  ABNORMAL: { label: '异常卡' },
  LEAVE: { label: '请假' }
}

/** ATTENDANCE 计算方式（与算薪内核 attendanceAmount 的分支一一对应） */
export const ATTENDANCE_MODE = {
  PER_COUNT: { label: '按次数计' },
  BONUS_IF_ZERO: { label: '达标即发放' }
}

/** 流程类型：ONBOARDING / OFFBOARDING（契约用 flowType 区分，无独立字典） */
export const FLOW_TYPE = {
  ONBOARDING: { label: '入职', type: 'primary', variant: 'soft' },
  OFFBOARDING: { label: '离职', type: 'warning', variant: 'soft' }
}

/* ==================== 五、原移动独有字典 ==================== */

/** 明细展示顺序：要处理的排最前（异常 → 未配置），已停用沉底 */
export const COLLECT_STATE_ORDER = ['ABNORMAL', 'UNCONFIGURED', 'NORMAL', 'DISABLED']

/** 工资单筛选（顺序即老板端的处理动线：待审核 → 已通过 → 已发布 → 已确认 → 驳回/草稿） */
export const PAYROLL_FILTERS = [
  { value: 'PENDING_APPROVAL', label: '待审核' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'PUBLISHED', label: '已发布' },
  { value: 'CONFIRMED', label: '已确认' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'DRAFT', label: '草稿' }
]

/* ==================== 六、学历与合同类型（原 PC 侧命名 HR_* 统一为通用名） ==================== */

/** 学历：与 hrStore 的 EDUCATION_LABEL 一致 */
export const EDUCATION = {
  MASTER: { label: '硕士' },
  BACHELOR: { label: '本科' },
  COLLEGE: { label: '大专' },
  HIGH_SCHOOL: { label: '高中及以下' }
}

/** 合同类型：与 hrStore 的 CONTRACT_TYPE_LABEL 一致 */
export const CONTRACT_TYPE = {
  FIXED_TERM: { label: '固定期限' },
  NON_FIXED_TERM: { label: '无固定期限' },
  INTERN: { label: '实习协议' },
  DISPATCH: { label: '劳务派遣' }
}

/* ==================== 七、请假（M11） ==================== */

/**
 * 请假状态 6 态（设计规范 §1.1）。
 * 遵循既有变体语义：soft = 状态描述；outline = 终态无动作诉求。
 * 请假不使用 solid —— 请假没有「超时未处理即事故」的紧迫语义（不像工单高优先级）。
 */
export const LEAVE_STATUS = {
  PENDING_STATION: { label: '待站长初审', type: 'warning', variant: 'soft' },
  PENDING_BOSS: { label: '待老板终审', type: 'primary', variant: 'soft' },
  APPROVED: { label: '已通过', type: 'success', variant: 'soft' },
  REJECTED: { label: '已驳回', type: 'danger', variant: 'soft' },
  CANCELLED: { label: '已撤销', type: 'info', variant: 'outline' },
  REVOKED: { label: '已撤回', type: 'warning', variant: 'outline' }
}

/** 驳回阶段（仅 REJECTED 有值）：两级驳回共用一个状态，靠本字段在列表副信息里区分 */
export const LEAVE_REJECT_STAGE = {
  STATION: { label: '初审驳回' },
  BOSS: { label: '终审驳回' }
}

/**
 * 假别（D4：固定枚举，不做额度）。
 * countMode 决定计薪天数口径：SCHEDULED = 逐日查排班矩阵（自动排除轮休日）；NATURAL = 按自然日连续计。
 */
export const LEAVE_TYPE = {
  ANNUAL: { label: '年假', countMode: 'SCHEDULED' },
  PERSONAL: { label: '事假', countMode: 'SCHEDULED' },
  SICK: { label: '病假', countMode: 'SCHEDULED' },
  COMPENSATORY: { label: '调休', countMode: 'SCHEDULED' },
  MARRIAGE: { label: '婚假', countMode: 'NATURAL' },
  MATERNITY: { label: '产假', countMode: 'NATURAL' },
  PATERNITY: { label: '陪产假', countMode: 'NATURAL' },
  BEREAVEMENT: { label: '丧假', countMode: 'NATURAL' },
  OTHER: { label: '其他', countMode: 'SCHEDULED' }
}

/** 半天粒度：请假的最小时段单位。上午 = 当日 00:00–12:00 归属段，下午 = 12:00–24:00 */
export const HALF_DAY = { AM: { label: '上午' }, PM: { label: '下午' } }

/**
 * 筛选器：'PENDING' 是服务端展开的聚合虚拟值（= PENDING_STATION + PENDING_BOSS），
 * 前端只传 'PENDING'，避免三端各自做两次请求求和。
 * 顺序即处理动线：待办优先 → 结果 → 终态 → 全部。
 */
export const LEAVE_FILTERS = [
  { value: 'PENDING', label: '审批中' },
  { value: 'PENDING_STATION', label: '待初审' },
  { value: 'PENDING_BOSS', label: '待终审' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'CANCELLED', label: '已撤销' },
  { value: 'REVOKED', label: '已撤回' },
  { value: '', label: '全部' }
]

/** 操作留痕动作（D6 审计）：NOTIFY_SKIP 是「通知目标缺失」的排障留痕，不属业务动作但同样要可检索 */
export const LEAVE_LOG_ACTION = {
  SUBMIT: { label: '提交申请' },
  UPDATE: { label: '修改申请' },
  RESUBMIT: { label: '修改后重新提交' },
  CANCEL: { label: '申请人撤销' },
  STATION_APPROVE: { label: '站长初审通过' },
  STATION_REJECT: { label: '站长初审驳回' },
  FINAL_APPROVE: { label: '老板终审通过' },
  FINAL_REJECT: { label: '老板终审驳回' },
  REVOKE: { label: '审批人撤回' },
  NOTIFY_SKIP: { label: '通知未送达' }
}

/**
 * 按天考勤展示态（考勤记录/汇总的「按天」展示层，不是打卡记录级枚举）。
 * 为什么不往 ATTENDANCE_STATUS 加 LEAVE：那个枚举描述「打卡事实」，请假不是一种打卡行为。
 */
export const DAY_ATTENDANCE_STATE = {
  NORMAL: { label: '正常', type: 'success', variant: 'soft' },
  LATE: { label: '迟到', type: 'warning', variant: 'soft' },
  EARLY_LEAVE: { label: '早退', type: 'warning', variant: 'soft' },
  ABNORMAL: { label: '异常', type: 'danger', variant: 'soft' },
  LEAVE: { label: '请假', type: 'primary', variant: 'soft' },
  MISS: { label: '缺卡', type: 'info', variant: 'outline' }
}

/** 运行日志上报端（M11 D6）：级别筛选复用 SYNC_LOG_LEVEL，不另立一份级别字典 */
export const CLIENT_LOG_SOURCE = {
  PC: { label: 'PC' },
  H5: { label: 'H5' },
  SHELL: { label: '壳' }
}

/* ==================== 通用工具 ==================== */

/** 字典通用取值：不存在时返回原值，避免页面出现空白 */
export function dictLabel(dict, value, fallback = '-') {
  const item = dict[value]
  return item ? item.label : fallback
}
