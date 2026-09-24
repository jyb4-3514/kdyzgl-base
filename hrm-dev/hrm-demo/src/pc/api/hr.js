import request from '@admin/utils/request'

/**
 * 人事接口封装（需求8 人事档案与定薪 + 需求10 入职/离职流程）
 * 路径与入参逐条对齐 shared/mock/routes/hr.js（即未来后端契约）。
 *
 * 权限口径：档案 / 定薪 / 流程的读写全部是 ADMIN，唯一例外是「本人可查自己的档案与定薪」
 * （详情接口不挂 roles，由服务端按登录人收敛）；因此列表类接口只有管理员能调，前端只在 ADMIN 页面渲染。
 * 离职流程的「薪资结算」步骤由服务端编排财务域生成结算单并把单号回写流程，前端只传 remark。
 */

/* ==================== 人事档案 ==================== */

// GET /api/v1/hr/profiles 档案列表（仅 ADMIN，分页）：deptId / stationId / keyword
// 出参已脱敏：银行卡与紧急联系人手机号由服务端返回掩码值，前端不得回显完整卡号
export function getHrProfiles(params) {
  return request.get('/hr/profiles', { params })
}

// GET /api/v1/hr/profiles/{employeeId} 档案详情：附带 salary 定薪摘要；员工可查本人
export function getHrProfile(employeeId) {
  return request.get(`/hr/profiles/${employeeId}`)
}

// PUT /api/v1/hr/profiles/{employeeId} 维护档案（仅 ADMIN）：学历 / 合同起止 / 试用期 / 社保基数 / 紧急联系人 / 银行卡
// 员工已离职（profile.leaveDate 非空）时返回 9302，前端应先禁用表单而不是「提交后才报错」
export function updateHrProfile(employeeId, data) {
  return request.put(`/hr/profiles/${employeeId}`, data)
}

/* ==================== 定薪档案 ==================== */

// GET /api/v1/hr/salary-structures 定薪列表（仅 ADMIN，分页）：返回每人当前的薪资构成与合计
export function getHrSalaries(params) {
  return request.get('/hr/salary-structures', { params })
}

// GET /api/v1/hr/salary-structures/{employeeId} 定薪详情 → { current, histories }，histories 为调薪留痕（倒序）
export function getHrSalary(employeeId) {
  return request.get(`/hr/salary-structures/${employeeId}`)
}

// PUT /api/v1/hr/salary-structures/{employeeId} 调整薪资（仅 ADMIN）：覆盖当前档案并追加一条调薪记录
// 生效日期早于当前生效日期 → 9303；员工已离职 → 9302；无档案 → 9305
export function updateHrSalary(employeeId, data) {
  return request.put(`/hr/salary-structures/${employeeId}`, data)
}

/* ==================== 入职流程 ==================== */

// GET /api/v1/hr/onboarding 入职流程列表（仅 ADMIN）：status / stationId / keyword + 分页
export function getOnboardings(params) {
  return request.get('/hr/onboarding', { params })
}

// GET /api/v1/hr/onboarding/{id} 入职流程详情：steps 数组可直接渲染步骤条，currentStepKey 为当前待办步骤
export function getOnboarding(id) {
  return request.get(`/hr/onboarding/${id}`)
}

// POST /api/v1/hr/onboarding 发起入职流程（仅 ADMIN）：候选人姓名 / 手机号 / 学历 / 岗位 / 归属驿站
export function createOnboarding(data) {
  return request.post('/hr/onboarding', data)
}

// POST /api/v1/hr/onboarding/{id}/steps/{key}/complete 办理步骤（仅 ADMIN）
// 各步骤的专属入参：建档（username/password/deptId/stationId）、分配（deptId/stationId/position/role）、
// 定薪（basicSalary/postSalary/performanceBase/allowances/effectiveDate）、完成（无）；remark 为通用备注
export function completeOnboardingStep(id, key, data) {
  return request.post(`/hr/onboarding/${id}/steps/${key}/complete`, data)
}

// POST /api/v1/hr/onboarding/{id}/reject 驳回入职流程（仅 ADMIN，原因 2-200 字）
export function rejectOnboarding(id, data) {
  return request.post(`/hr/onboarding/${id}/reject`, data)
}

/* ==================== 离职流程 ==================== */

// GET /api/v1/hr/offboarding 离职流程列表（仅 ADMIN）：status / stationId / keyword + 分页
export function getOffboardings(params) {
  return request.get('/hr/offboarding', { params })
}

// GET /api/v1/hr/offboarding/{id} 离职流程详情：含 settlementPayrollId/No/Amount（需求9 工资单引用关系）
export function getOffboarding(id) {
  return request.get(`/hr/offboarding/${id}`)
}

// POST /api/v1/hr/offboarding 发起离职流程（仅 ADMIN）：employeeId / type(RESIGN|DISMISS|RETIRE) / reason / lastWorkDate
// 该员工已有进行中的离职流程 → 9304（契约注释口径，即「不可重复发起」）
export function createOffboarding(data) {
  return request.post('/hr/offboarding', data)
}

// POST /api/v1/hr/offboarding/{id}/steps/{key}/complete 办理步骤（仅 ADMIN）
// SETTLEMENT 步骤由服务端生成离职结算单；LEAVE 步骤要求结算已完成，否则 9306
export function completeOffboardingStep(id, key, data) {
  return request.post(`/hr/offboarding/${id}/steps/${key}/complete`, data)
}

// POST /api/v1/hr/offboarding/{id}/reject 驳回离职流程（仅 ADMIN，原因 2-200 字）
export function rejectOffboarding(id, data) {
  return request.post(`/hr/offboarding/${id}/reject`, data)
}
