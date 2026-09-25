/**
 * 权限判定单点化：路由守卫与页面共用同一纯函数，避免 roles 判定散落多处。
 * 移动端已用 currentUser 空对象兜底（见 mobile/stores/auth.js），此函数把同一语义固化下来。
 */
export function canAccess(roles, user) {
  if (!roles || !roles.length) return true
  return !!user && roles.includes(user.role)
}
