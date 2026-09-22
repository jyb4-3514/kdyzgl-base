import { ref } from 'vue'
import { getSyncTaskDetail, getSyncTaskLogs } from '../../../api/syncTask.js'

/**
 * 批次日志抽屉
 *
 * 日志是「每次状态流转追加一条」，所以触发/重试后要能就地看到追加记录：
 * 由编排层在列表动作成功后回调 reloadIfOpen，避免日志状态与列表状态互相直接依赖。
 */
export function useSyncBatchLogs() {
  const logVisible = ref(false)
  const logTask = ref({})
  const logs = ref([])
  const loadingLog = ref(false)
  const logError = ref(false)

  async function loadLogs(id) {
    if (!id) return
    loadingLog.value = true
    logError.value = false
    try {
      logs.value = await getSyncTaskLogs(id)
    } catch (e) {
      logs.value = []
      logError.value = true
    } finally {
      loadingLog.value = false
    }
  }

  function openLogs(task) {
    logTask.value = task
    logVisible.value = true
    logs.value = []
    loadLogs(task.id)
  }

  /** 通知中心跳转（?taskId=）时任务可能不在当前页，用详情接口取回任务信息再开日志 */
  async function openLogsById(id) {
    try {
      openLogs(await getSyncTaskDetail(id))
    } catch (e) {
      /* 拦截器已统一提示（跨驿站越权按 404 处理） */
    }
  }

  /** 抽屉正开着同一批次时刷新日志；否则不动（触发/重试后的联动入口） */
  async function reloadIfOpen(row) {
    if (row && logTask.value.id === row.id) await loadLogs(row.id)
  }

  return { logVisible, logTask, logs, loadingLog, logError, loadLogs, openLogs, openLogsById, reloadIfOpen }
}
