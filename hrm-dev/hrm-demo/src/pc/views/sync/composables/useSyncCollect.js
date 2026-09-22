import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getSyncConfigs, getSyncOverview, saveSyncConfig } from '../../../api/syncConfig.js'

/**
 * 采集配置视图（总览计数 + 驿站采集开关 + 抽屉开合）
 *
 * 总览计数与配置明细必须同时刻刷新，否则计数与表格对不上，故一个 fetchCollect 里 Promise.all 拉齐。
 * 竞态守卫同列表页：切 Tab 或连点刷新时，旧响应不得覆盖新数据。
 * @param markUpdated 取数成功后的时间戳回调（页头副信息共用一份 updatedAt）
 */
export function useSyncCollect({ markUpdated = () => {} } = {}) {
  const collectLoading = ref(false)
  const collectError = ref(false)
  const overview = ref({ total: 0, counts: null })
  const configs = ref([])
  const collectFilter = ref(null)
  const actingStationId = ref(null)
  const configVisible = ref(false)
  const configStation = ref({ id: null, name: '' })

  let collectSeq = 0

  const attentionCount = computed(() => {
    const counts = overview.value.counts
    return counts ? counts.abnormal + counts.unconfigured : 0
  })

  const attentionText = computed(() => {
    const counts = overview.value.counts || { abnormal: 0, unconfigured: 0 }
    const parts = []
    if (counts.abnormal) parts.push(`${counts.abnormal} 个驿站采集异常`)
    if (counts.unconfigured) parts.push(`${counts.unconfigured} 个驿站未配置采集`)
    return parts.join('、')
  })

  /** 「查看列表」只收敛到待处理两项，其余状态仍完整可达（清除筛选即恢复） */
  const visibleConfigs = computed(() => {
    if (collectFilter.value !== 'attention') return configs.value
    return configs.value.filter((item) => item.collectState === 'ABNORMAL' || item.collectState === 'UNCONFIGURED')
  })

  async function fetchCollect() {
    const seq = (collectSeq += 1)
    collectLoading.value = true
    collectError.value = false
    try {
      const [ov, listData] = await Promise.all([getSyncOverview(), getSyncConfigs()])
      if (seq !== collectSeq) return
      overview.value = ov
      configs.value = listData
      markUpdated()
    } catch (e) {
      if (seq !== collectSeq) return
      overview.value = { total: 0, counts: null }
      configs.value = []
      collectError.value = true
    } finally {
      if (seq === collectSeq) collectLoading.value = false
    }
  }

  function openConfig(row) {
    configStation.value = { id: row.stationId, name: row.stationName }
    configVisible.value = true
  }

  function closeConfig() {
    configVisible.value = false
  }

  /** 表格开关：关闭需二次确认（在途批次不受影响）；开启前若缺数据源由服务端 400 兜底并给可操作文案 */
  async function handleToggle(row, value) {
    if (!value) {
      try {
        await ElMessageBox.confirm(
          `关闭「${row.stationName}」的采集后，该驿站将停止包裹采集，在途批次不受影响。`,
          '关闭采集',
          { confirmButtonText: '确认关闭', cancelButtonText: '再想想', type: 'warning' }
        )
      } catch (e) {
        return
      }
    }
    actingStationId.value = row.stationId
    try {
      await saveSyncConfig(row.stationId, { enabled: value ? 1 : 0 }, { silent: true })
      ElMessage.success(value ? '采集已开启，按配置频次生效' : '采集已关闭')
      await fetchCollect()
    } catch (e) {
      ElMessage.error((e && e.message) || '采集开关保存失败，请重试')
    } finally {
      actingStationId.value = null
    }
  }

  function focusAttention() {
    collectFilter.value = 'attention'
  }

  function clearFilter() {
    collectFilter.value = null
  }

  return {
    collectLoading,
    collectError,
    overview,
    configs,
    collectFilter,
    actingStationId,
    configVisible,
    configStation,
    attentionCount,
    attentionText,
    visibleConfigs,
    fetchCollect,
    openConfig,
    closeConfig,
    handleToggle,
    focusAttention,
    clearFilter
  }
}
