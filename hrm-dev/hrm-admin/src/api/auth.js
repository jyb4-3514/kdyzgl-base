import request from '../utils/request'

/**
 * 认证接口（api.md 4.1，4 个）
 * login 为白名单接口；logout / me / password 需登录态
 */

// POST /api/v1/auth/login 登录（silent：由登录页自行区分 1001/1002 文案）
export function login(data) {
  return request.post('/auth/login', data, { silent: true })
}

// POST /api/v1/auth/logout 退出登录（幂等）
export function logout() {
  return request.post('/auth/logout')
}

// GET /api/v1/auth/me 当前用户信息（手机号脱敏）
export function getMe() {
  return request.get('/auth/me')
}

// PUT /api/v1/auth/password 修改本人密码（成功后会话失效，需重新登录）
export function updatePassword(data) {
  return request.put('/auth/password', data)
}
