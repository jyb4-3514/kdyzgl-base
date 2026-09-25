import { onMounted, onUnmounted, ref } from 'vue'

/**
 * 全局共享的「当前时间」引用（PC 与移动共用）
 *
 * 为什么放 shared/composables 而不是 shared/domain：domain 是展示端与 Mock 共用的纯工具层
 * （shared/mock/util.js 会 re-export 它），往里塞 Vue 依赖会把 Vue 拖进 Mock 的依赖图；
 * 本文件与 Vue 生命周期强相关，单独成层。
 *
 * 单定时器策略：所有倒计时/时间展示组件共用一个 interval，订阅数归零自动停表。
 * 否则一页 20 行工单就是 20 个 setInterval（P1-5）。
 */
const now = ref(Date.now())
let timer = null
let subscribers = 0

export function useNow(interval = 30000) {
  onMounted(() => {
    subscribers += 1
    if (!timer)
      timer = setInterval(() => {
        now.value = Date.now()
      }, interval)
  })
  onUnmounted(() => {
    subscribers -= 1
    if (subscribers <= 0) {
      clearInterval(timer)
      timer = null
      subscribers = 0
    }
  })
  return now
}
