import request from '../utils/http.js'

/**
 * 认证（api.md 4.1）· 网页端
 * 路径与入参严格对齐 @kdyzgl/mock 的 routes/auth.js；登录态由本端 stores/auth.js 持有，本文件只做请求封装。
 * silent：登录/短信相关错误由登录页就地渲染，不弹全局提示。
 */
export const login = (data) => request.post('/auth/login', data, { silent: true })
export const logout = () => request.post('/auth/logout')
export const getMe = () => request.get('/auth/me')
export const updatePassword = (data) => request.put('/auth/password', data, { silent: true })

export const sendSms = (data) => request.post('/auth/sms/send', data, { silent: true })
export const smsLogin = (data) => request.post('/auth/sms/login', data, { silent: true })
export const verifyDevice = (data) => request.post('/auth/device/verify', data, { silent: true })
