/**
 * @kdyzgl/mock 公共出口
 *
 * 装配契约与演示工程既有 `shared/mock/install.js` 完全一致：
 * `installMock(...axiosInstances)` 只改实例 `defaults.adapter`，返回卸载函数，页面代码零改动。
 */
export { installMock, createMockAdapter } from './install.js'
export { routes } from './routes/index.js'
