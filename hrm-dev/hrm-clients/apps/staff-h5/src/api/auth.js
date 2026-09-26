import http from '../utils/http.js'

/**
 * 认证（api.md 4.1）
 * 路径与入参严格对齐 shared/mock/routes/*；登录态由 stores/auth.js 持有，本文件只做请求封装。
 */

/** silent：登录/改密/短信相关错误由页面就地渲染，不弹全局 Toast */
export const login = (data) => http.post('/auth/login', data, { silent: true })
export const logout = () => http.post('/auth/logout')
export const getMe = () => http.get('/auth/me')
export const updatePassword = (data) => http.put('/auth/password', data, { silent: true })

/* 登录体系改造新增（multi-client-architecture.md §4.1.2）：A1 发码 / A2 短信登录 / B2 设备验证 */
export const sendSms = (data) => http.post('/auth/sms/send', data, { silent: true })
/**
 * 注册发码（api.md §4.11.1 R-1）：复用 /auth/sms/send，仅新增 scene=REGISTER。
 * scene 固定在函数内，避免调用方传入 LOGIN 造成「发码场景与提交校验场景不一致」。
 */
export const sendRegisterSms = (data) => sendSms({ ...data, scene: 'REGISTER' })
export const smsLogin = (data) => http.post('/auth/sms/login', data, { silent: true })
export const verifyDevice = (data) => http.post('/auth/device/verify', data, { silent: true })
export const listDevices = () => http.get('/auth/devices')
export const revokeDevice = (deviceId) => http.delete(`/auth/devices/${encodeURIComponent(deviceId)}`)
