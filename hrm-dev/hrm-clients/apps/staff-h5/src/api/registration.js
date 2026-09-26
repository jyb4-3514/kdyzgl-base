import http from '../utils/http.js'

/**
 * 员工自助注册（api.md §4.11.2 R-2，公开端点）
 *
 * silent：注册失败由页面就地渲染中性文案，不弹全局 Toast（避免与页内提示重复）。
 * 提交体严格取字段白名单（registration-design §11.5 表 A）—— 不夹带 role/deptId/薪资等审批侧字段，
 * 服务端 FAIL_ON_UNKNOWN_PROPERTIES 收口（夹带未知字段 → 400）。
 * 依 Q2 裁定：不发 password（U-02 定：注册密码不作员工口令，前端已整体移除该字段）。
 */
export const submitRegistration = (data) => http.post('/registration', data, { silent: true })
