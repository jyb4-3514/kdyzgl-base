import { CODE, FINANCE_CODE } from '../../constants/errorCode.js'
import { ALL_ROLES } from '../../constants/role.js'
import { fail, ok } from '../util.js'
import { isBlank, isMonth, pageSizeInvalid, textLen } from '../validate.js'
import {
  PAYROLL_BILL_TYPE_LABEL,
  PAYROLL_ITEM_SOURCE_LABEL,
  PAYROLL_ITEM_TYPE_LABEL,
  PAYROLL_STATUS_LABEL,
  approvePayroll,
  confirmPayroll,
  createRule,
  findPayrollForUser,
  findRule,
  generatePayrolls,
  listPayrolls,
  listRules,
  myPayrolls,
  objectPayroll,
  publishPayrolls,
  removeRule,
  submitPayrolls,
  updatePayrollItems,
  updateRule
} from '../financeStore.js'

/**
 * 财务接口（需求9 工资单）
 *
 * 权限口径：
 * - 规则维护 / 生成 / 提交 / 审核 / 发布 / 改人工项：仅管理员（ADMIN）
 * - 查询本人工资单 / 确认 / 提异议：任意角色，但一律以登录身份过滤，不接受前端传 employeeId
 * - 工资单详情：ADMIN 全量；其余角色仅本人且仅已发布 / 已确认（越权 9404，未发布 9403）
 */

const ITEM_TYPES = Object.keys(PAYROLL_ITEM_TYPE_LABEL)
const ITEM_SOURCES = Object.keys(PAYROLL_ITEM_SOURCE_LABEL)
const RULE_STATUS = [0, 1, true, false]

function toIdList(value) {
  if (value === undefined || value === null) return null
  if (!Array.isArray(value)) return 'INVALID'
  return value.map(Number)
}

/* ==================== 计薪规则 ==================== */

function ruleList() {
  return ok({ list: listRules() })
}

/** 规则项校验：来源与类型必须落在枚举内，params 只收对象（具体参数由算薪内核按来源解释） */
function validateItems(items) {
  if (!Array.isArray(items) || !items.length) return 'items 须为非空数组'
  const keys = new Set()
  for (const item of items) {
    if (!item || !textLen(item.key, 1, 30)) return '规则项 key 长度须为 1-30'
    if (!/^[A-Z][A-Z0-9_]*$/.test(String(item.key))) return '规则项 key 须为大写字母、数字与下划线'
    if (keys.has(item.key)) return `规则项 key 重复：${item.key}`
    keys.add(item.key)
    if (!textLen(item.name, 1, 20)) return '规则项名称长度须为 1-20'
    if (!ITEM_TYPES.includes(item.type)) return `规则项类型仅支持 ${ITEM_TYPES.join(' / ')}`
    if (!ITEM_SOURCES.includes(item.source)) return `规则项来源仅支持 ${ITEM_SOURCES.join(' / ')}`
    if (
      item.params !== undefined &&
      (typeof item.params !== 'object' || item.params === null || Array.isArray(item.params))
    )
      return '规则项 params 须为对象'
    if (item.enabled !== undefined && ![0, 1, true, false].includes(item.enabled)) return '规则项 enabled 仅支持 0 / 1'
  }
  return null
}

function validateRuleBody(body, isCreate) {
  if (body.ruleName !== undefined || isCreate) {
    if (!textLen(body.ruleName, 2, 50)) return '规则名称长度须为 2-50'
  }
  if (body.status !== undefined && !RULE_STATUS.includes(body.status)) return 'status 仅支持 0 / 1'
  if (body.remark !== undefined && !isBlank(body.remark) && !textLen(body.remark, 0, 200)) return '备注不可超过 200 字'
  if (body.items !== undefined || isCreate) {
    const error = validateItems(body.items)
    if (error) return error
  }
  return null
}

function ruleCreate({ body }) {
  const error = validateRuleBody(body, true)
  if (error) return fail(CODE.BAD_REQUEST, error)
  const result = createRule(body)
  return result.code === 200 ? ok(result.data) : fail(result.code, result.message)
}

function ruleUpdate({ pathParams, body }) {
  const error = validateRuleBody(body, false)
  if (error) return fail(CODE.BAD_REQUEST, error)
  const result = updateRule(pathParams.id, body)
  return result.code === 200 ? ok(result.data) : fail(result.code, result.message)
}

function ruleDelete({ pathParams }) {
  const result = removeRule(pathParams.id)
  return result.code === 200 ? ok(null) : fail(result.code, result.message)
}

/** 规则详情（生成草稿前预览规则用；不在契约主表内，属只读补充）；规则不存在与 PUT / DELETE 同口径回 9401 */
function ruleDetail({ pathParams }) {
  const rule = findRule(pathParams.id)
  return rule ? ok(rule) : fail(FINANCE_CODE.RULE_NOT_EXISTS)
}

/* ==================== 工资单 ==================== */

function generate({ body }) {
  if (isBlank(body.month) || !isMonth(body.month)) return fail(CODE.BAD_REQUEST, 'month 格式须为 YYYY-MM')
  const employeeIds = toIdList(body.employeeIds)
  if (employeeIds === 'INVALID') return fail(CODE.BAD_REQUEST, 'employeeIds 须为数组')
  const result = generatePayrolls({
    month: body.month,
    stationId: isBlank(body.stationId) ? null : Number(body.stationId),
    deptId: isBlank(body.deptId) ? null : Number(body.deptId),
    employeeIds,
    ruleId: isBlank(body.ruleId) ? null : Number(body.ruleId)
  })
  return result.code === 200 ? ok(result.data) : fail(result.code, result.message)
}

function payrollList({ params }) {
  if (pageSizeInvalid(params.pageSize)) return fail(CODE.BAD_REQUEST, '每页条数须为 1-100')
  if (!isBlank(params.month) && !isMonth(params.month)) return fail(CODE.BAD_REQUEST, 'month 格式须为 YYYY-MM')
  if (!isBlank(params.status) && !Object.keys(PAYROLL_STATUS_LABEL).includes(params.status))
    return fail(CODE.BAD_REQUEST, 'status 取值非法')
  if (!isBlank(params.billType) && !Object.keys(PAYROLL_BILL_TYPE_LABEL).includes(params.billType))
    return fail(CODE.BAD_REQUEST, 'billType 取值非法')
  return ok(
    listPayrolls({
      month: params.month,
      stationId: params.stationId,
      employeeId: params.employeeId,
      status: params.status,
      billType: params.billType,
      keyword: params.keyword,
      pageNum: params.pageNum,
      pageSize: params.pageSize
    })
  )
}

function myList({ params, user }) {
  if (pageSizeInvalid(params.pageSize)) return fail(CODE.BAD_REQUEST, '每页条数须为 1-100')
  if (!isBlank(params.month) && !isMonth(params.month)) return fail(CODE.BAD_REQUEST, 'month 格式须为 YYYY-MM')
  if (!isBlank(params.status) && !['PUBLISHED', 'CONFIRMED'].includes(params.status))
    return fail(CODE.BAD_REQUEST, 'status 取值非法')
  return ok(
    myPayrolls(user, { month: params.month, status: params.status, pageNum: params.pageNum, pageSize: params.pageSize })
  )
}

// TODO(扩展): 与请假详情对齐，由服务端补下发派生操作标志（如 canConfirm/canObject/canEdit），
//   当前由前端按「状态 + 角色」本地判定；口径定稿后再补，避免与前端判定并存形成双口径
function payrollDetail({ pathParams, user }) {
  const result = findPayrollForUser(pathParams.id, user)
  return result.code === 200 ? ok(result.data) : fail(result.code, result.message)
}

function submit({ body }) {
  const ids = toIdList(body.ids)
  if (ids === 'INVALID' || !ids || !ids.length) return fail(CODE.BAD_REQUEST, 'ids 须为非空数组')
  const result = submitPayrolls(ids)
  return result.code === 200 ? ok(result.data) : fail(result.code, result.message)
}

/** 批量发布：ids 与 「month + stationId」二选一，后者用于「整月一键发布」 */
function publish({ body, user }) {
  const ids = toIdList(body.ids)
  if (ids === 'INVALID') return fail(CODE.BAD_REQUEST, 'ids 须为数组')
  if (!ids && isBlank(body.month)) return fail(CODE.BAD_REQUEST, '请传入 ids 或 month')
  if (!isBlank(body.month) && !isMonth(body.month)) return fail(CODE.BAD_REQUEST, 'month 格式须为 YYYY-MM')
  const result = publishPayrolls(
    { ids, month: body.month, stationId: isBlank(body.stationId) ? null : Number(body.stationId) },
    user
  )
  return result.code === 200 ? ok(result.data) : fail(result.code, result.message)
}

function approve({ pathParams, body, user }) {
  if (typeof body.approved !== 'boolean') return fail(CODE.BAD_REQUEST, 'approved 须为布尔值')
  if (!isBlank(body.approveRemark) && !textLen(body.approveRemark, 0, 200))
    return fail(CODE.BAD_REQUEST, '审核意见不可超过 200 字')
  const result = approvePayroll(
    pathParams.id,
    body.approved,
    isBlank(body.approveRemark) ? null : String(body.approveRemark).trim(),
    user
  )
  return result.code === 200 ? ok(result.data) : fail(result.code, result.message)
}

function updateItems({ pathParams, body }) {
  if (!Array.isArray(body.items) || !body.items.length) return fail(CODE.BAD_REQUEST, 'items 须为非空数组')
  const result = updatePayrollItems(pathParams.id, body.items)
  return result.code === 200 ? ok(result.data) : fail(result.code, result.message)
}

function confirm({ pathParams, user }) {
  const result = confirmPayroll(pathParams.id, user)
  return result.code === 200 ? ok(result.data) : fail(result.code, result.message)
}

function objection({ pathParams, body, user }) {
  if (!textLen(body.reason, 2, 200)) return fail(CODE.BAD_REQUEST, '异议原因长度须为 2-200')
  const result = objectPayroll(pathParams.id, String(body.reason).trim(), user)
  return result.code === 200 ? ok(result.data) : fail(result.code, result.message)
}

/**
 * 路由注册顺序：/finance/payrolls 下的静态段（my / generate / submit / publish）必须排在
 * /finance/payrolls/:id 之前，否则 GET /finance/payrolls/my 会被 :id 匹配成「工资单不存在」。
 */
export const financeRoutes = [
  { method: 'get', path: '/finance/payroll-rules', roles: ['ADMIN'], handler: ruleList },
  { method: 'post', path: '/finance/payroll-rules', roles: ['ADMIN'], handler: ruleCreate },
  { method: 'get', path: '/finance/payroll-rules/:id', roles: ['ADMIN'], handler: ruleDetail },
  { method: 'put', path: '/finance/payroll-rules/:id', roles: ['ADMIN'], handler: ruleUpdate },
  { method: 'delete', path: '/finance/payroll-rules/:id', roles: ['ADMIN'], handler: ruleDelete },
  { method: 'get', path: '/finance/payrolls/my', roles: ALL_ROLES, handler: myList },
  { method: 'post', path: '/finance/payrolls/generate', roles: ['ADMIN'], handler: generate },
  { method: 'post', path: '/finance/payrolls/submit', roles: ['ADMIN'], handler: submit },
  { method: 'post', path: '/finance/payrolls/publish', roles: ['ADMIN'], handler: publish },
  { method: 'get', path: '/finance/payrolls', roles: ['ADMIN'], handler: payrollList },
  { method: 'get', path: '/finance/payrolls/:id', roles: ALL_ROLES, handler: payrollDetail },
  { method: 'put', path: '/finance/payrolls/:id/items', roles: ['ADMIN'], handler: updateItems },
  { method: 'post', path: '/finance/payrolls/:id/approve', roles: ['ADMIN'], handler: approve },
  { method: 'post', path: '/finance/payrolls/:id/confirm', roles: ALL_ROLES, handler: confirm },
  { method: 'post', path: '/finance/payrolls/:id/objection', roles: ALL_ROLES, handler: objection }
]
