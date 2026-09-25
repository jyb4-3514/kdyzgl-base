import { createMockAdapter } from './engine.js'

/**
 * Mock 挂载入口
 * 用法：installMock(axiosInstance[, axiosInstance2, ...])
 * 只改实例的 defaults.adapter，不触碰请求/响应拦截器，因此一期 utils/request.js 与移动端 http.js 均可零改动接入
 */
export function installMock(...instances) {
  const list = instances.flat().filter(Boolean)
  const mockAdapter = createMockAdapter()
  const original = list.map((instance) => instance.defaults.adapter)

  list.forEach((instance) => {
    instance.defaults.adapter = mockAdapter
  })

  // 返回卸载函数：关闭 Mock 即回到真实 HTTP，页面代码零改动（demo-design.md 第 2 章核心约束）
  return function uninstallMock() {
    list.forEach((instance, index) => {
      instance.defaults.adapter = original[index]
    })
  }
}

export { createMockAdapter }
