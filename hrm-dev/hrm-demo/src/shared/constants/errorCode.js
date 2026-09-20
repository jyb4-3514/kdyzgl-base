/**
 * 错误码常量（严格对齐 hrm-dev/docs/api.md 第 2 章，码值不得改动）
 * 一期 10xx-50xx 由后端定义；二三期段位为 Demo 提案或 plan.md 已定段位，见文件末尾说明
 */

/** 通用段（与 HTTP 语义对齐） */
export const CODE = {
  SUCCESS: 200,
  BAD_REQUEST: 400,
  UNAUTHORIZED: 401,
  FORBIDDEN: 403,
  NOT_FOUND: 404,
  SERVER_ERROR: 500
}

/** 认证与账号（10xx） */
export const AUTH_CODE = {
  LOGIN_FAILED: 1001, // 用户名或密码错误（不区分账号不存在，防账号探测）
  ACCOUNT_DISABLED: 1002, // 账号已禁用
  USERNAME_EXISTS: 1003, // 登录账号已存在
  OLD_PASSWORD_WRONG: 1004 // 原密码错误
}

/** 员工（20xx） */
export const EMPLOYEE_CODE = {
  SELF_OPERATION: 2001, // 不允许对当前登录账号执行该操作
  LAST_ADMIN: 2002, // 不允许对最后一个可用管理员执行该操作
  PHONE_EXISTS: 2003 // 手机号已被其他员工使用
}

/** 部门（30xx） */
export const DEPARTMENT_CODE = {
  NOT_EXISTS: 3001, // 指定的部门不存在
  HAS_CHILDREN: 3002, // 存在子部门，不允许删除
  HAS_EMPLOYEE: 3003, // 部门下存在员工，不允许删除
  NAME_EXISTS: 3004 // 同级部门名称已存在
}

/** 驿站（40xx） */
export const STATION_CODE = {
  NOT_EXISTS: 4001, // 指定的驿站不存在
  CODE_EXISTS: 4002, // 驿站编码已存在
  HAS_EMPLOYEE: 4003, // 驿站下存在员工，不允许删除
  DISABLED: 4004 // 驿站已停用，不能归属员工
}

/** 导入导出（50xx） */
export const IMPORT_CODE = {
  FILE_INVALID: 5001, // 文件为空或格式不正确（仅支持 .xlsx）
  ROW_LIMIT: 5002, // 数据行数超过单次上限（1000 行）
  ROW_ERRORS: 5003 // 行级校验错误，明细见 data.errors
}

/**
 * 二三期扩展段（Demo 使用）
 * - 60xx 同步任务/爬虫、70xx 包裹：plan.md 3.4 已定段位
 * - 80xx 工单、90xx 通知：plan.md 未定义，为 Demo 提案，待三期专项设计确认
 */
export const DEMO_CODE = {
  SYNC_RETRY_NOT_ALLOWED: 6001, // 任务状态不允许重试
  SYNC_CONFIG_NOT_EXISTS: 6002, // 该驿站尚未配置采集（无对应 sync_config）
  PARCEL_NOT_EXISTS: 7001, // 运单号不存在
  PARCEL_STATUS_INVALID: 7002, // 包裹状态不允许取件
  PARCEL_PICKED_BY_OTHER: 7003, // 该包裹已被他人取件
  WORK_ORDER_STATUS_INVALID: 8001, // 工单状态流转非法
  WORK_ORDER_NO_PERMISSION: 8002, // 无权操作该工单
  WORK_ORDER_TRANSFER_NO_PERMISSION: 8003, // 无权转单该工单
  WORK_ORDER_TRANSFER_TARGET_INVALID: 8004, // 转单对象不合法
  WORK_ORDER_DISPATCH_RULE_NOT_EXISTS: 8005, // 自动派单规则不存在
  WORK_ORDER_GROUP_MSG_INVALID: 8006, // 群消息内容为空或无法解析
  NOTIFICATION_NOT_EXISTS: 9001, // 通知不存在
  NOTIFICATION_PUBLISH_SCOPE_INVALID: 9002 // 通知发布范围参数不合法
}

/**
 * 考勤与排班段（T17 新增，Demo 提案）
 *
 * 【待确认】契约给出的考勤码为 9001-9006，但 7.4.8 已把 90xx 划给「通知」，
 * 且上面 DEMO_CODE.NOTIFICATION_NOT_EXISTS 已占用 9001、被现有校验断言与已交付的通知页依赖
 * （同一码值无法承载两句不同提示，CODE_MESSAGE 只能保留一条，会导致通知错误提示串味）。
 * 为不破坏既有通知错误码，考勤顺延至 91xx 段，语义与契约逐条一致。
 * TODO(扩展): 若三期专项设计确认 90xx 归考勤，需同步迁移通知码并全量回归校验脚本。
 */
export const ATTENDANCE_CODE = {
  RULE_NOT_CONFIGURED: 9101, // 打卡规则未配置
  OUT_OF_TIME_WINDOW: 9102, // 不在打卡时间窗内
  WIFI_MISMATCH: 9103, // WiFi 校验未通过
  LOCATION_MISMATCH: 9104, // 定位校验未通过（超出围栏范围）
  DUPLICATE_CHECK: 9105, // 今日该类型打卡已完成（重复打卡）
  SHIFT_UNAVAILABLE: 9106, // 班次不存在或已停用
  // 一个码承载两句语义：打卡时索引越界（时段不存在）、保存规则时时段配置非法。
  // 两者都是「时段」维度的问题，前端可按调用接口区分，故不额外占码。
  PERIOD_NOT_FOUND: 9107, // 打卡时段不存在 / 规则时段配置非法
  // 补卡（T19）：同一码承载「重复申请」与「已有正常卡无需补卡」两句语义，前端提示文案一致，不再额外占码
  MAKEUP_DUPLICATE: 9108, // 该时段当日已有补卡申请或已正常打卡
  MAKEUP_STATUS_INVALID: 9109 // 补卡申请状态不允许该操作（已审批的单子不可重复审批）
}

/**
 * KPI 考核段（92xx，Demo 提案，需求7）
 * 与既有 91xx 考勤段并列顺延：考勤是「打卡事实」，KPI 是「按月按指标算出的考核结果」，两套错误语义不重叠。
 * TODO(扩展): 三期专项设计定稿后，本段位需与后端 error_code 表对齐并同步 CODE_MESSAGE。
 */
export const KPI_CODE = {
  METRIC_NOT_EXISTS: 9201, // KPI 指标不存在
  WEIGHT_SUM_INVALID: 9202, // 启用指标的权重合计必须为 100%
  NO_METRIC: 9203, // 该考核月份无有效 KPI 指标
  SCORE_NOT_EXISTS: 9204 // KPI 评分记录不存在（该员工该月尚未算分）
}

/**
 * 人事段（93xx，含入离职流程，Demo 提案，需求8 / 需求10）
 * 与 20xx「员工」段的分工：20xx 管账号与组织归属，93xx 管人事档案、定薪与流程流转。
 */
export const HR_CODE = {
  PROFILE_NOT_EXISTS: 9301, // 人事档案不存在
  EMPLOYEE_RESIGNED: 9302, // 员工已离职，不可操作
  ONBOARDING_STATUS_INVALID: 9303, // 入职流程状态不允许该操作
  OFFBOARDING_STATUS_INVALID: 9304, // 离职流程状态不允许该操作
  SALARY_NOT_EXISTS: 9305, // 薪资档案不存在
  SETTLEMENT_UNFINISHED: 9306 // 离职薪资结算未完成，不可离岗
}

/**
 * 财务段（94xx，工资单，Demo 提案，需求9）
 * 独立于 50xx「导入导出」：本段描述工资单与计薪规则的状态与权限，与文件操作无关。
 */
export const FINANCE_CODE = {
  RULE_NOT_EXISTS: 9401, // 计薪规则不存在
  PAYROLL_NOT_EXISTS: 9402, // 工资单不存在
  PAYROLL_STATUS_INVALID: 9403, // 工资单状态不允许该操作
  PAYROLL_NO_PERMISSION: 9404, // 无权查看他人工资单
  PAYROLL_GENERATED: 9405 // 该月工资单已提交审核或已发布，不可重复生成
}

/**
 * 同步配置中心段（95xx，Demo 提案，同步任务自定义配置）
 * 与 60xx「同步任务/采集状态」的分工：60xx 描述采集任务与配置行的运行态，95xx 描述
 * 「配置项定义 / 选项集 / 全局默认 / 驿站覆盖 / 导入导出」这套配置管理动作的校验失败。
 */
export const SYNC_CONFIG_CODE = {
  ITEM_NOT_EXISTS: 9501, // 配置项不存在
  ITEM_KEY_EXISTS: 9502, // 配置项 Key 已存在
  ITEM_IN_USE: 9503, // 配置项被驿站覆盖引用，需 confirm 确认后删除
  OPTION_NOT_EXISTS: 9504, // 选项不存在
  OPTION_IN_USE: 9505, // 选项被引用（全局默认硬阻断 / 驿站覆盖需 confirm 确认）
  VALUE_INVALID: 9506, // 配置值不符合该配置项的约束
  REQUIRED_EMPTY: 9507, // 必填配置项不允许置空
  IMPORT_PARSE_ERROR: 9508, // 导入内容解析失败或格式不正确
  IMPORT_CONFLICT_INVALID: 9509, // 导入冲突策略参数不合法
  BUILTIN_NOT_DELETABLE: 9510 // 内置配置项或选项不允许删除，只能停用
}

/**
 * 请假段（96xx，M11 设计规范附录 B）
 * 段位顺延：91xx 考勤 / 92xx KPI / 93xx 人事 / 94xx 财务 / 95xx 同步配置 → 96xx 请假。
 * 每码必须同时写入 CODE_MESSAGE，否则会出现「有码无文案」。
 */
export const LEAVE_CODE = {
  NOT_EXISTS: 9601, // 请假申请不存在
  STATUS_INVALID: 9602, // 状态不允许该操作（已被他人处理 / 当前状态不可编辑）
  OVERLAP: 9603, // 与已有申请的时间段重叠
  DATE_INVALID: 9604, // 日期非法（早于今天 / 结束早于开始 / 超单次上限）
  NO_PERMISSION: 9605, // 无权操作（跨站 / 审自己 / ADMIN 提交）
  PAYROLL_LOCKED: 9606, // 账期工资单已生成，不可撤回
  EDIT_FORBIDDEN: 9607 // 当前状态不允许修改
}

/** 错误码 → 默认提示文案（handler 未显式给 message 时兜底，与 api.md 2.2 示例逐条一致） */
export const CODE_MESSAGE = {
  200: 'success',
  400: '参数校验失败',
  401: '未登录或登录态已失效',
  403: '无权限访问该资源',
  404: '资源不存在',
  500: '系统繁忙，请稍后重试',
  1001: '用户名或密码错误',
  1002: '账号已禁用，请联系管理员',
  1003: '登录账号已存在',
  1004: '原密码错误',
  2001: '不允许对当前登录账号执行该操作',
  2002: '不允许对最后一个可用管理员执行该操作',
  2003: '手机号已被其他员工使用',
  3001: '指定的部门不存在',
  3002: '存在子部门，不允许删除',
  3003: '部门下存在员工，不允许删除',
  3004: '同级部门名称已存在',
  4001: '指定的驿站不存在',
  4002: '驿站编码已存在',
  4003: '驿站下存在员工，不允许删除',
  4004: '驿站已停用，不能归属员工',
  5001: '导入文件为空或格式不正确（仅支持 .xlsx）',
  5002: '导入数据超过单次上限（1000 行）',
  5003: '导入数据存在校验错误',
  6001: '任务状态不允许重试',
  6002: '该驿站尚未配置采集',
  7001: '运单号不存在',
  7002: '包裹状态不允许取件',
  7003: '该包裹已被他人取件',
  8001: '工单状态流转非法',
  8002: '无权操作该工单',
  8003: '无权转单该工单',
  8004: '转单对象不合法',
  8005: '自动派单规则不存在',
  8006: '群消息内容为空或无法解析',
  9001: '通知不存在',
  9002: '通知发布范围参数不合法',
  9101: '该驿站尚未配置打卡规则',
  9102: '不在打卡时间窗内',
  9103: 'WiFi 校验未通过',
  9104: '定位校验未通过，已超出打卡围栏范围',
  9105: '今日该类型打卡已完成',
  9106: '班次不存在或已停用',
  // 保存规则时的时段配置错误会由 handler 显式传 message，这里只兜底打卡越界场景
  9107: '打卡时段不存在',
  9108: '该时段当日已有补卡申请或已正常打卡',
  9109: '补卡申请状态不允许该操作',
  9201: 'KPI 指标不存在',
  9202: '启用指标的权重合计必须为 100%',
  9203: '该考核月份无有效 KPI 指标',
  9204: '该员工该考核月份暂无评分记录',
  9301: '人事档案不存在',
  9302: '员工已离职，不可操作',
  9303: '入职流程状态不允许该操作',
  9304: '离职流程状态不允许该操作',
  9305: '薪资档案不存在',
  9306: '离职薪资结算未完成，不可离岗',
  9401: '计薪规则不存在',
  9402: '工资单不存在',
  9403: '工资单状态不允许该操作',
  9404: '无权查看他人工资单',
  9405: '该月工资单已提交审核或已发布，不可重复生成',
  9501: '配置项不存在',
  9502: '配置项 Key 已存在',
  9503: '配置项被驿站覆盖，需确认后删除',
  9504: '选项不存在',
  9505: '选项被引用，需确认后删除',
  9506: '配置值不符合该配置项的约束',
  9507: '必填配置项不允许置空',
  9508: '导入内容解析失败或格式不正确',
  9509: '导入冲突策略参数不合法',
  9510: '内置配置项或选项不允许删除，只能停用',
  9601: '请假申请不存在',
  9602: '该申请当前状态不支持此操作',
  9603: '该时间段与已有申请重叠',
  9604: '请假日期不合法',
  9605: '无权操作该请假申请',
  9606: '该账期工资单已生成，不可撤回',
  9607: '该申请当前状态不允许修改'
}

/** 取错误码默认文案 */
export function codeMessage(code) {
  return CODE_MESSAGE[code] || '操作失败'
}
