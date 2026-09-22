import { ref } from 'vue'
import { defineStore } from 'pinia'
import { getAttendanceStatus } from '../api/attendance.js'
import { readToken } from '../utils/authStorage.js'

/**
 * 今日打卡状态快照（首页一键打卡 + 打卡页共用）
 *
 * 为什么单独成 store：两页消费同一端点 /attendance/status，各拉一遍必然出现
 * 「首页说已完成 1/2、打卡页说 0/2」的口径漂移，且首屏请求翻倍（与 stores/todo.js 同构理由）。
 *
 * 失败语义：接口失败才置 error；`hasSchedule === false` 是业务空态（当天确实没排班），
 * 两者绝不可互相顶替（UI 规范 §6.4 硬规则 1）。
 * 不持有：演示辅助开关、定位结果、补卡弹层表单 —— 只被打卡页消费，留在页内。
 */
export const useAttendanceStore = defineStore('mobileAttendance', () => {
  /** /attendance/status 快照：{ shift, rule, periods, hasSchedule }；null = 尚未取到 */
  const status = ref(null)
  const loading = ref(false)
  const error = ref('')

  async function refresh() {
    if (!readToken()) {
      status.value = null
      error.value = ''
      return
    }
    loading.value = true
    error.value = ''
    try {
      status.value = await getAttendanceStatus()
    } catch (e) {
      // 失败必须与「无排班」分开：状态置空 + 记原因，消费方才能渲染重试而不是「今天不用打卡」
      status.value = null
      error.value = e.message || '出勤状态获取失败'
    } finally {
      loading.value = false
    }
  }

  /** 打卡成功后就地回写（不整页重拉）：只覆盖变化的槽位，其余字段保持原值 */
  function update(patch) {
    status.value = { ...(status.value || {}), ...patch }
  }

  function clear() {
    status.value = null
    error.value = ''
  }

  return { status, loading, error, refresh, update, clear }
})
