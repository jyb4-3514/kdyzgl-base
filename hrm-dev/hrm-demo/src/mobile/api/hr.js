import http from '../utils/http.js'

/** 人事档案与定薪（需求8） */
export const getHrProfiles = (params) => http.get('/hr/profiles', { params })
/** 档案详情：ADMIN 全量，员工仅本人（契约强制收口，前端不传 employeeId 之外的身份参数） */
export const getHrProfile = (employeeId) => http.get(`/hr/profiles/${employeeId}`)
export const getHrSalary = (employeeId) => http.get(`/hr/salary-structures/${employeeId}`)
/** 调薪：silent —— 9302（已离职）/9305（无定薪档案）要在表单内给针对性说明 */
export const updateHrSalary = (employeeId, data) =>
  http.put(`/hr/salary-structures/${employeeId}`, data, { silent: true })

/* ==================== 入离职流程（需求10） ==================== */
/* 契约口径：入离职流程接口仅 ADMIN 开放，移动端即管理端「审批」；员工端为无权限降级（见 staff/flow.vue） */

export const getOnboardingFlows = (params) => http.get('/hr/onboarding', { params })
export const getOnboardingFlow = (id) => http.get(`/hr/onboarding/${id}`)
/** 步骤办理：silent —— 9303 要说明「请先办理上一步/该步骤已完成」，通用 Toast 说不了这么细 */
export const completeOnboardingStep = (id, key, data) =>
  http.post(`/hr/onboarding/${id}/steps/${key}/complete`, data || {}, { silent: true })
export const rejectOnboardingFlow = (id, data) => http.post(`/hr/onboarding/${id}/reject`, data, { silent: true })
export const getOffboardingFlows = (params) => http.get('/hr/offboarding', { params })
export const getOffboardingFlow = (id) => http.get(`/hr/offboarding/${id}`)
export const completeOffboardingStep = (id, key, data) =>
  http.post(`/hr/offboarding/${id}/steps/${key}/complete`, data || {}, { silent: true })
export const rejectOffboardingFlow = (id, data) => http.post(`/hr/offboarding/${id}/reject`, data, { silent: true })
