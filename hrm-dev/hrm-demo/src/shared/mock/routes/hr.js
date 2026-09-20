import { findDepartmentById, findEmployeeById, findStationById } from '../db.js'
import { CODE } from '../../constants/errorCode.js'
import { fail, formatDate, ok } from '../util.js'
import { isBlank, isDate, isPhone, pageSizeInvalid, textLen } from '../validate.js'
import {
  CONTRACT_TYPE_LABEL,
  EDUCATION_LABEL,
  FLOW_STATUS_LABEL,
  OFFBOARDING_TYPE_LABEL,
  completeOffboardingStep,
  completeOnboardingStep,
  createOffboarding,
  createOnboarding,
  findOffboarding,
  findOnboarding,
  findProfile,
  findSalary,
  listOffboardings,
  listOnboardings,
  listProfiles,
  listSalaries,
  rejectOffboarding,
  rejectOnboarding,
  saveProfile,
  saveSalary
} from '../hrStore.js'
import { createSettlementPayroll } from '../financeStore.js'

/**
 * 人事接口（需求8 人事档案与定薪 + 需求10 入职/离职流程）
 *
 * 全部为老板（ADMIN）视角，唯一例外是「本人可查自己的人事档案与定薪」——
 * 站长不开放：薪资与银行卡属于敏感信息，按最小权限收在 ADMIN 与本人两端。
 *
 * 离职流程的「薪资结算」步骤需要生成工资单，而工资单属于财务域；
 * 人事域不反向依赖财务域（会成环），因此由本路由层在步骤完成时编排：
 * 先经财务域创建结算单 → 把结算单号回写流程（见 completeOffboardingStep 的 settlementRef）。
 */

const EDUCATION_KEYS = Object.keys(EDUCATION_LABEL)
const CONTRACT_TYPE_KEYS = Object.keys(CONTRACT_TYPE_LABEL)
const OFFBOARDING_TYPE_KEYS = Object.keys(OFFBOARDING_TYPE_LABEL)
const FLOW_STATUS_KEYS = Object.keys(FLOW_STATUS_LABEL)

/** 人事档案详情/编辑的可见范围：老板全量，员工只能看自己 */
const canAccessEmployee = (user, employeeId) => user.role === 'ADMIN' || user.id === Number(employeeId)

/* ==================== 人事档案 ==================== */

function profileList({ params }) {
  if (pageSizeInvalid(params.pageSize)) return fail(CODE.BAD_REQUEST, '每页条数须为 1-100')
  return ok(
    listProfiles({
      deptId: params.deptId,
      stationId: params.stationId,
      keyword: params.keyword,
      pageNum: params.pageNum,
      pageSize: params.pageSize
    })
  )
}

function profileDetail({ pathParams, user }) {
  const employeeId = Number(pathParams.employeeId)
  if (!canAccessEmployee(user, employeeId)) return fail(CODE.FORBIDDEN, '无权查看他人人事档案')
  const result = findProfile(employeeId)
  return result.code === 200 ? ok(result.data) : fail(result.code, result.message)
}

/** 档案字段校验：日期用「起止先后」而非只校验格式，避免落下合同到期早于生效的档案 */
function validateProfile(body) {
  if (body.education !== undefined && body.education !== null && !EDUCATION_KEYS.includes(body.education))
    return `学历仅支持 ${EDUCATION_KEYS.join(' / ')}`
  if (body.contractType !== undefined && body.contractType !== null && !CONTRACT_TYPE_KEYS.includes(body.contractType))
    return `合同类型仅支持 ${CONTRACT_TYPE_KEYS.join(' / ')}`
  for (const key of ['contractStart', 'contractEnd', 'probationEnd', 'regularDate']) {
    if (!isBlank(body[key]) && !isDate(body[key])) return `${key} 格式须为 YYYY-MM-DD`
  }
  const start = isBlank(body.contractStart) ? null : body.contractStart
  const end = isBlank(body.contractEnd) ? null : body.contractEnd
  if (start && end && end < start) return '合同到期日不能早于生效日'
  if (
    body.probationMonths !== undefined &&
    (!Number.isInteger(Number(body.probationMonths)) ||
      Number(body.probationMonths) < 0 ||
      Number(body.probationMonths) > 12)
  )
    return '试用期月数须为 0-12 的整数'
  if (
    body.socialSecurityBase !== undefined &&
    body.socialSecurityBase !== null &&
    !(Number(body.socialSecurityBase) >= 0)
  )
    return '社保基数须不小于 0'
  if (!isBlank(body.emergencyContactPhone) && !isPhone(body.emergencyContactPhone)) return '紧急联系人手机号格式不正确'
  if (!isBlank(body.emergencyContactName) && !textLen(body.emergencyContactName, 2, 20))
    return '紧急联系人姓名长度须为 2-20'
  if (!isBlank(body.bankName) && !textLen(body.bankName, 2, 50)) return '开户行长度须为 2-50'
  if (!isBlank(body.bankAccount) && !/^\d{12,25}$/.test(String(body.bankAccount).replace(/\s/g, '')))
    return '银行卡号须为 12-25 位数字'
  return null
}

function profileUpdate({ pathParams, body }) {
  const error = validateProfile(body)
  if (error) return fail(CODE.BAD_REQUEST, error)
  const result = saveProfile(pathParams.employeeId, body)
  return result.code === 200 ? ok(result.data) : fail(result.code, result.message)
}

/* ==================== 定薪档案 ==================== */

function salaryList({ params }) {
  if (pageSizeInvalid(params.pageSize)) return fail(CODE.BAD_REQUEST, '每页条数须为 1-100')
  return ok(
    listSalaries({
      deptId: params.deptId,
      stationId: params.stationId,
      keyword: params.keyword,
      pageNum: params.pageNum,
      pageSize: params.pageSize
    })
  )
}

function salaryDetail({ pathParams, user }) {
  const employeeId = Number(pathParams.employeeId)
  if (!canAccessEmployee(user, employeeId)) return fail(CODE.FORBIDDEN, '无权查看他人定薪档案')
  const result = findSalary(employeeId)
  return result.code === 200 ? ok(result.data) : fail(result.code, result.message)
}

/** 金额校验：定薪项与津贴全是钱，负值一律挡在入口；生效日期决定留痕排序，必须是合法日期 */
function validateSalary(body, isCreate) {
  for (const key of ['basicSalary', 'postSalary', 'performanceBase']) {
    if (body[key] !== undefined || isCreate) {
      if (!Number.isFinite(Number(body[key])) || Number(body[key]) < 0) return `${key} 须为不小于 0 的数字`
    }
  }
  if (body.allowances !== undefined) {
    if (!Array.isArray(body.allowances)) return 'allowances 须为数组'
    for (const item of body.allowances) {
      if (!item || !textLen(item.name, 1, 20)) return '津贴项名称长度须为 1-20'
      if (!Number.isFinite(Number(item.amount)) || Number(item.amount) < 0) return '津贴金额须为不小于 0 的数字'
    }
  }
  if (!isBlank(body.effectiveDate) && !isDate(body.effectiveDate)) return 'effectiveDate 格式须为 YYYY-MM-DD'
  if (body.reason !== undefined && !isBlank(body.reason) && !textLen(body.reason, 2, 50)) return '调薪原因长度须为 2-50'
  return null
}

function salaryUpdate({ pathParams, body, user }) {
  const error = validateSalary(body, true)
  if (error) return fail(CODE.BAD_REQUEST, error)
  const result = saveSalary(pathParams.employeeId, body, user, 'ADJUST')
  return result.code === 200 ? ok(result.data) : fail(result.code, result.message)
}

/* ==================== 入职流程 ==================== */

function onboardingList({ params }) {
  if (pageSizeInvalid(params.pageSize)) return fail(CODE.BAD_REQUEST, '每页条数须为 1-100')
  if (!isBlank(params.status) && !FLOW_STATUS_KEYS.includes(params.status))
    return fail(CODE.BAD_REQUEST, 'status 取值非法')
  return ok(
    listOnboardings({
      status: params.status,
      stationId: params.stationId,
      keyword: params.keyword,
      pageNum: params.pageNum,
      pageSize: params.pageSize
    })
  )
}

function onboardingDetail({ pathParams }) {
  const result = findOnboarding(pathParams.id)
  return result.code === 200 ? ok(result.data) : fail(result.code, result.message)
}

function onboardingCreate({ body, user }) {
  if (!textLen(body.candidateName, 2, 20)) return fail(CODE.BAD_REQUEST, '候选人姓名长度须为 2-20')
  if (!isPhone(body.phone)) return fail(CODE.BAD_REQUEST, '手机号格式不正确')
  if (body.gender !== undefined && ![0, 1, 2].includes(Number(body.gender)))
    return fail(CODE.BAD_REQUEST, '性别取值非法')
  if (!isBlank(body.education) && !EDUCATION_KEYS.includes(body.education))
    return fail(CODE.BAD_REQUEST, '学历取值非法')
  if (!isBlank(body.expectedEntryDate) && !isDate(body.expectedEntryDate))
    return fail(CODE.BAD_REQUEST, 'expectedEntryDate 格式须为 YYYY-MM-DD')
  if (!isBlank(body.deptId) && !findDepartmentById(body.deptId)) return fail(CODE.BAD_REQUEST, '指定的部门不存在')
  if (!isBlank(body.stationId) && !findStationById(body.stationId)) return fail(CODE.BAD_REQUEST, '指定的驿站不存在')
  if (!isBlank(body.remark) && !textLen(body.remark, 0, 200)) return fail(CODE.BAD_REQUEST, '备注不可超过 200 字')
  const result = createOnboarding(body, user)
  return result.code === 200 ? ok(result.data) : fail(result.code, result.message)
}

/** 步骤办理：步骤级参数（账号/驿站/定薪）由 store 按步骤语义校验，路由只做通用字段把关 */
function onboardingStepComplete({ pathParams, body, user }) {
  if (!textLen(pathParams.key, 2, 40)) return fail(CODE.BAD_REQUEST, '步骤标识非法')
  if (!isBlank(body.remark) && !textLen(body.remark, 0, 200)) return fail(CODE.BAD_REQUEST, '备注不可超过 200 字')
  if (!isBlank(body.expectedEntryDate) && !isDate(body.expectedEntryDate))
    return fail(CODE.BAD_REQUEST, 'expectedEntryDate 格式须为 YYYY-MM-DD')
  const error = validateSalary(body, false)
  if (error) return fail(CODE.BAD_REQUEST, error)
  const result = completeOnboardingStep(pathParams.id, pathParams.key, body, user)
  return result.code === 200 ? ok(result.data) : fail(result.code, result.message)
}

function onboardingReject({ pathParams, body, user }) {
  if (!textLen(body.reason, 2, 200)) return fail(CODE.BAD_REQUEST, '驳回原因长度须为 2-200')
  const result = rejectOnboarding(pathParams.id, String(body.reason).trim(), user)
  return result.code === 200 ? ok(result.data) : fail(result.code, result.message)
}

/* ==================== 离职流程 ==================== */

function offboardingList({ params }) {
  if (pageSizeInvalid(params.pageSize)) return fail(CODE.BAD_REQUEST, '每页条数须为 1-100')
  if (!isBlank(params.status) && !FLOW_STATUS_KEYS.includes(params.status))
    return fail(CODE.BAD_REQUEST, 'status 取值非法')
  return ok(
    listOffboardings({
      status: params.status,
      stationId: params.stationId,
      keyword: params.keyword,
      pageNum: params.pageNum,
      pageSize: params.pageSize
    })
  )
}

function offboardingDetail({ pathParams }) {
  const result = findOffboarding(pathParams.id)
  return result.code === 200 ? ok(result.data) : fail(result.code, result.message)
}

function offboardingCreate({ body, user }) {
  if (!OFFBOARDING_TYPE_KEYS.includes(body.type))
    return fail(CODE.BAD_REQUEST, `type 仅支持 ${OFFBOARDING_TYPE_KEYS.join(' / ')}`)
  if (!textLen(body.reason, 2, 200)) return fail(CODE.BAD_REQUEST, '离职原因长度须为 2-200')
  if (isBlank(body.lastWorkDate) || !isDate(body.lastWorkDate))
    return fail(CODE.BAD_REQUEST, 'lastWorkDate 格式须为 YYYY-MM-DD')
  if (!findEmployeeById(body.employeeId)) return fail(CODE.NOT_FOUND, '员工不存在')
  const result = createOffboarding(body, user)
  return result.code === 200 ? ok(result.data) : fail(result.code, result.message)
}

function offboardingStepComplete({ pathParams, body, user }) {
  const flow = findOffboarding(pathParams.id)
  if (flow.code !== 200) return fail(flow.code, flow.message)
  if (!isBlank(body.remark) && !textLen(body.remark, 0, 200)) return fail(CODE.BAD_REQUEST, '备注不可超过 200 字')

  let settlementRef = null
  // 薪资结算：先经财务域生成结算单（草稿），再把单号回写流程，保证「结算」不是一句空状态
  if (pathParams.key === 'SETTLEMENT') {
    const result = createSettlementPayroll({
      employeeId: flow.data.employeeId,
      month: String(flow.data.lastWorkDate || formatDate(new Date())).slice(0, 7),
      offboardingId: flow.data.id,
      remark: `离职薪资结算（最后工作日 ${flow.data.lastWorkDate}）`
    })
    if (result.code !== 200) return fail(result.code, result.message)
    settlementRef = { payrollId: result.data.id, payrollNo: result.data.payrollNo, amount: result.data.netAmount }
  }

  const result = completeOffboardingStep(pathParams.id, pathParams.key, { ...body, settlementRef }, user)
  return result.code === 200 ? ok(result.data) : fail(result.code, result.message)
}

function offboardingReject({ pathParams, body, user }) {
  if (!textLen(body.reason, 2, 200)) return fail(CODE.BAD_REQUEST, '驳回原因长度须为 2-200')
  const result = rejectOffboarding(pathParams.id, String(body.reason).trim(), user)
  return result.code === 200 ? ok(result.data) : fail(result.code, result.message)
}

/**
 * 路由注册顺序：静态路径（/hr/profiles、/hr/salary-structures、/hr/onboarding、/hr/offboarding）
 * 均排在带 :id 的路径之前；步骤路径 /:id/steps/:key/complete 与 /:id 段数不同，不会互相遮蔽。
 */
export const hrRoutes = [
  { method: 'get', path: '/hr/profiles', roles: ['ADMIN'], handler: profileList },
  { method: 'get', path: '/hr/profiles/:employeeId', handler: profileDetail },
  { method: 'put', path: '/hr/profiles/:employeeId', roles: ['ADMIN'], handler: profileUpdate },
  { method: 'get', path: '/hr/salary-structures', roles: ['ADMIN'], handler: salaryList },
  { method: 'get', path: '/hr/salary-structures/:employeeId', handler: salaryDetail },
  { method: 'put', path: '/hr/salary-structures/:employeeId', roles: ['ADMIN'], handler: salaryUpdate },
  { method: 'get', path: '/hr/onboarding', roles: ['ADMIN'], handler: onboardingList },
  { method: 'post', path: '/hr/onboarding', roles: ['ADMIN'], handler: onboardingCreate },
  { method: 'get', path: '/hr/onboarding/:id', roles: ['ADMIN'], handler: onboardingDetail },
  { method: 'post', path: '/hr/onboarding/:id/steps/:key/complete', roles: ['ADMIN'], handler: onboardingStepComplete },
  { method: 'post', path: '/hr/onboarding/:id/reject', roles: ['ADMIN'], handler: onboardingReject },
  { method: 'get', path: '/hr/offboarding', roles: ['ADMIN'], handler: offboardingList },
  { method: 'post', path: '/hr/offboarding', roles: ['ADMIN'], handler: offboardingCreate },
  { method: 'get', path: '/hr/offboarding/:id', roles: ['ADMIN'], handler: offboardingDetail },
  {
    method: 'post',
    path: '/hr/offboarding/:id/steps/:key/complete',
    roles: ['ADMIN'],
    handler: offboardingStepComplete
  },
  { method: 'post', path: '/hr/offboarding/:id/reject', roles: ['ADMIN'], handler: offboardingReject }
]
