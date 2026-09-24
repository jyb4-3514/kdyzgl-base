import request from '@admin/utils/request'

/**
 * 网页端认证（登录体系改造）
 * 复用一期 request 实例（Demo 的 Mock 适配器挂在同一 axios 实例上，见 src/pc/main.js），
 * 路径与入参严格对齐 shared/mock/routes/auth.js；登录态由 @admin/stores/auth 持有。
 * silent：登录/短信相关错误由登录页就地渲染，不弹全局提示。
 */
export const login = (data) => request.post('/auth/login', data, { silent: true })
export const sendSms = (data) => request.post('/auth/sms/send', data, { silent: true })
export const smsLogin = (data) => request.post('/auth/sms/login', data, { silent: true })
export const verifyDevice = (data) => request.post('/auth/device/verify', data, { silent: true })
