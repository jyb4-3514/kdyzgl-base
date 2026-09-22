import http from '../utils/http.js'

/**
 * 认证（api.md 4.1）
 * 路径与入参严格对齐 shared/mock/routes/*；登录态由 stores/auth.js 持有，本文件只做请求封装。
 */

/** silent：登录/改密的错误由页面就地渲染，不弹全局 Toast */
export const login = (data) => http.post('/auth/login', data, { silent: true })
export const logout = () => http.post('/auth/logout')
export const getMe = () => http.get('/auth/me')
export const updatePassword = (data) => http.put('/auth/password', data, { silent: true })
