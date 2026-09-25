/**
 * 登录态存储工厂（ADR §3.5 第 2 项）
 *
 * 抽取来源：`hrm-demo/src/mobile/utils/authStorage.js`（逐字逻辑一致，只把写死的键名改为注入）。
 * 为什么工厂化：键名是各端共同的存储契约（同一浏览器需可并存多端登录态），
 * 本身属端差异，必须由端注入而非在共享包里写死一份。
 *
 * TODO(扩展): B2/B3 起键名口径改为 `hrm:{client}:token` 生成（ADR §3.5 第 2 项），
 * 并保留旧键的双键兼容读取，避免已登录用户被登出；B1 不切换，键名仍由端侧传入现值。
 *
 * @param {{ tokenKey: string, userKey: string }} keys
 */
export function createAuthStorage({ tokenKey, userKey }) {
  const hasStorage = () => typeof localStorage !== 'undefined'

  return {
    tokenKey,
    userKey,
    readToken: () => (hasStorage() ? localStorage.getItem(tokenKey) || '' : ''),
    readUser: () => {
      if (!hasStorage()) return null
      try {
        return JSON.parse(localStorage.getItem(userKey) || 'null')
      } catch (e) {
        // 存储被外部改写时按未登录处理，避免脏数据导致路由守卫崩溃
        return null
      }
    },
    writeAuth: (token, user) => {
      if (!hasStorage()) return
      localStorage.setItem(tokenKey, token)
      localStorage.setItem(userKey, JSON.stringify(user))
    },
    clearAuth: () => {
      if (!hasStorage()) return
      localStorage.removeItem(tokenKey)
      localStorage.removeItem(userKey)
    }
  }
}
