import request from '@admin/utils/request'

/**
 * 财务接口封装（需求9 工资单）
 * 路径与入参逐条对齐 shared/mock/routes/finance.js（即未来后端契约）。
 *
 * 权限口径：规则维护 / 生成 / 提交 / 审核 / 发布 / 改人工项仅管理员（ADMIN）；
 * 查询本人工资单与确认 / 提异议任意角色可调，但服务端一律按登录身份过滤，不接受前端传 employeeId。
 *
 * 核心设计约束：算薪结果不由前端推导——金额全部来自服务端的规则项计算，
 * 前端只负责「配置规则项参数 + 提交 + 展示服务端算出的明细」。
 */

/* ==================== 计薪规则 ==================== */

// GET /api/v1/finance/payroll-rules 规则列表（仅 ADMIN）→ { list }，每项含 items（规则项数组）
export function getPayrollRules() {
  return request.get('/finance/payroll-rules')
}

// GET /api/v1/finance/payroll-rules/{id} 规则详情（仅 ADMIN）
export function getPayrollRule(id) {
  return request.get(`/finance/payroll-rules/${id}`)
}

// POST /api/v1/finance/payroll-rules 新建规则（仅 ADMIN）
// items 每项：{ key(大写标识,1-30), name(1-20), type(ADDITION|DEDUCTION), source(FIXED|ATTENDANCE|KPI|MANUAL), params(对象), enabled, sortOrder }
export function createPayrollRule(data) {
  return request.post('/finance/payroll-rules', data)
}

// PUT /api/v1/finance/payroll-rules/{id} 编辑规则（仅 ADMIN）：items 传入即为整体覆盖
export function updatePayrollRule(id, data) {
  return request.put(`/finance/payroll-rules/${id}`, data)
}

// DELETE /api/v1/finance/payroll-rules/{id} 删除规则（仅 ADMIN）：已被工资单引用时返回 9403，只能改为停用
export function deletePayrollRule(id) {
  return request.delete(`/finance/payroll-rules/${id}`)
}

/* ==================== 工资单 ==================== */

// GET /api/v1/finance/payrolls 工资单列表（仅 ADMIN，分页）：month / stationId / employeeId / status / billType / keyword
// 返回体额外带 counts（同月同驿站各状态计数），页面状态标签页直接用它，不必再拉全量自己数
export function getPayrolls(params) {
  return request.get('/finance/payrolls', { params })
}

// GET /api/v1/finance/payrolls/{id} 工资单详情：ADMIN 全量；其余角色仅本人且仅已发布/已确认
// 出参含 items（逐项金额与计算说明）与 actions（当前状态允许的动作，由服务端下发，前端不自行维护状态机）
export function getPayroll(id) {
  return request.get(`/finance/payrolls/${id}`)
}

// POST /api/v1/finance/payrolls/generate 按月批量生成草稿（仅 ADMIN）
// 幂等口径：同月同员工已有草稿/驳回单会被覆盖重建；该月已存在已提交或已发布的单据则整批拒绝（9405）
export function generatePayrolls(data) {
  return request.post('/finance/payrolls/generate', data)
}

// POST /api/v1/finance/payrolls/submit 批量提交审核（仅 ADMIN，ids 非空数组）：草稿/已驳回 → 待审核
export function submitPayrolls(ids) {
  return request.post('/finance/payrolls/submit', { ids })
}

// POST /api/v1/finance/payrolls/publish 批量发布（仅 ADMIN）：ids 或「month + stationId」二选一
// 只有「已通过」的单据会被发布，其余计入 skipped
export function publishPayrolls(data) {
  return request.post('/finance/payrolls/publish', data)
}

// POST /api/v1/finance/payrolls/{id}/approve 审核（仅 ADMIN）：approved 布尔值，驳回时 approveRemark 记入意见
export function approvePayroll(id, data) {
  return request.post(`/finance/payrolls/${id}/approve`, data)
}

// PUT /api/v1/finance/payrolls/{id}/items 修改人工项金额（仅 ADMIN）
// 只有 MANUAL 来源项可改，且单据须处于草稿/已驳回；改动后服务端重算应发/扣款/实发合计
export function updatePayrollItems(id, items) {
  return request.put(`/finance/payrolls/${id}/items`, { items })
}

/* ==================== 员工侧（本人） ==================== */

// GET /api/v1/finance/payrolls/my 我的工资单：只返回本人已发布/已确认的单据
export function getMyPayrolls(params) {
  return request.get('/finance/payrolls/my', { params })
}

// POST /api/v1/finance/payrolls/{id}/confirm 员工确认（只认本人 + 已发布）
export function confirmPayroll(id) {
  return request.post(`/finance/payrolls/${id}/confirm`)
}

// POST /api/v1/finance/payrolls/{id}/objection 员工提异议（原因 2-200 字）：单据退回待审核，由管理员重新核定
export function objectPayroll(id, reason) {
  return request.post(`/finance/payrolls/${id}/objection`, { reason })
}
