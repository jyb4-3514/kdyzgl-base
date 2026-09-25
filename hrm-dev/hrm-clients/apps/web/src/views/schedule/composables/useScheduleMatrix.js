import { computed, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useOrgStore } from '../../../stores/org.js'
import { ATTENDANCE_CODE } from '@kdyzgl/shared/constants/errorCode'
import { addDays, formatDate, mondayOf } from '@kdyzgl/shared/domain/time.js'
import { getSchedules, getShifts, saveSchedulesBatch } from '../../../api/attendance.js'
import { buildOriginal, buildRows, cellKey, filledCellsOf, localDate, stamp } from '../utils/schedule.js'

/**
 * 排班矩阵数据编排（取数 / 本地增量 / 保存 / 周与驿站切换）
 *
 * 交互取舍：单元格内直接放班次下拉，而不是「点开弹层再选」——排班是高频批量操作，
 * 一周 7 天 × 全站员工要一次性铺完，每次多一次弹层点击会翻倍操作成本。
 * 所有改动先在本地累积（dirty），点「保存排班」才批量提交，避免每改一格发一次请求
 * （服务端单次上限 200 条，正好覆盖一整屏矩阵）。
 *
 * 竞态守卫：切周/切驿站会连发多个请求，只认最后一次的结果。
 * 生效前提是「发起时自增、回来时比对」，只在切周时自增会漏掉首屏与切站之间的竞争。
 */
export function useScheduleMatrix() {
  const authStore = useAuthStore()
  // 驿站名册跨页共享，取数收口到 org store；本页的「选中驿站」仍是页面状态
  const orgStore = useOrgStore()
  const { stations } = storeToRefs(orgStore)

  const isAdmin = computed(() => !!authStore.user && authStore.user.role === 'ADMIN')
  const currentUser = computed(() => authStore.user || {})
  // 排班保存与班次维护在服务端仅放行 ADMIN，非 ADMIN 进入只读视角（数据范围仍由服务端强制收敛本站）
  const canWrite = computed(() => isAdmin.value)

  const loading = ref(false)
  const saving = ref(false)
  const matrixError = ref(false)
  const updatedAt = ref('')

  const stationId = ref(null)
  const weekStart = ref(formatDate(mondayOf(new Date())))
  const dates = ref([])
  const shifts = ref([])
  const rows = ref([])
  /** 服务端当前值（key = employeeId_workDate），用于判断某格是否真的改过、以及撤销 */
  const original = ref(new Map())
  /** 待提交改动（key 同上，value = 提交体），Map 去重保证同一格反复改只提交最后一次 */
  const dirty = ref(new Map())

  const today = formatDate(new Date())

  let matrixSeq = 0

  const effectiveStationId = computed(() => (isAdmin.value ? stationId.value : currentUser.value.stationId || null))

  const currentStationName = computed(() => {
    if (!isAdmin.value) return currentUser.value.stationName || ''
    const hit = stations.value.find((item) => item.id === stationId.value)
    return hit ? hit.stationName : ''
  })

  const dirtyCount = computed(() => dirty.value.size)

  const totalCells = computed(() => rows.value.length * dates.value.length)
  const filledCells = computed(() => filledCellsOf(rows.value))

  const weekLabel = computed(() => (dates.value.length ? `${dates.value[0]} ~ ${dates.value[6]}` : weekStart.value))

  const headerSub = computed(() => {
    const scope = isAdmin.value ? '数据范围：全域（可切换驿站）' : `数据范围：本站 ${currentStationName.value || ''}`
    const readonly = canWrite.value ? '' : ' · 只读视角'
    return `${scope} · 周 ${weekLabel.value} · 更新于 ${updatedAt.value || '—'}${readonly}`
  })

  async function loadStations() {
    try {
      await orgStore.loadStations()
      // 默认选中第一个驿站：演示的排班种子数据只投给城东驿站
      if (!stationId.value && stations.value.length) stationId.value = stations.value[0].id
    } catch (e) {
      /* 拦截器已统一提示；驿站未选定时排班表会进入错误态并给出重试 */
    }
  }

  async function fetchMatrix() {
    if (effectiveStationId.value == null) return
    const seq = (matrixSeq += 1)
    loading.value = true
    matrixError.value = false
    try {
      const matrix = await getSchedules({ stationId: effectiveStationId.value, weekStart: weekStart.value })
      // 请求期间用户又切了周/驿站：本次结果已过期，连 loading 也交给更新的那次收尾
      if (seq !== matrixSeq) return
      dates.value = matrix.dates
      shifts.value = matrix.shifts
      rows.value = buildRows(matrix.employees)
      // 服务端会把 weekStart 归一到周一，回写以保证界面显示与请求口径一致
      weekStart.value = matrix.weekStart
      original.value = buildOriginal(matrix.employees)
      dirty.value = new Map()
      updatedAt.value = stamp()
    } catch (e) {
      if (seq !== matrixSeq) return
      rows.value = []
      dates.value = []
      matrixError.value = true
    } finally {
      if (seq === matrixSeq) loading.value = false
    }
  }

  function markDirty(row, day) {
    const key = cellKey(row.employeeId, day.workDate)
    const originId = original.value.get(key) ?? null
    if ((day.shiftId ?? null) === originId) dirty.value.delete(key)
    else dirty.value.set(key, { employeeId: row.employeeId, workDate: day.workDate, shiftId: day.shiftId ?? null })
  }

  /** 单元格下拉改动：矩阵组件只回抛意图，落值与增量判定统一在这里做，避免两处各写一套 */
  function handleShiftChange({ row, index, shiftId }) {
    const day = row.days[index]
    if (!day) return
    day.shiftId = shiftId
    markDirty(row, day)
  }

  /** 丢弃本地改动：回写服务端值并清空待提交队列 */
  function discardChanges() {
    rows.value.forEach((row) => {
      row.days.forEach((day) => {
        day.shiftId = original.value.get(cellKey(row.employeeId, day.workDate)) ?? null
      })
    })
    dirty.value = new Map()
  }

  /** 切换周/驿站会丢弃未保存改动，先确认再执行 */
  async function confirmDiscard() {
    if (!dirtyCount.value) return true
    try {
      await ElMessageBox.confirm(
        `当前有 ${dirtyCount.value} 处排班改动未保存，继续操作将丢弃这些改动。`,
        '未保存的修改',
        { confirmButtonText: '丢弃并继续', cancelButtonText: '返回修改', type: 'warning' }
      )
      return true
    } catch (e) {
      return false
    }
  }

  async function handleSave() {
    if (!dirtyCount.value) return
    saving.value = true
    try {
      // silent：错误提示按 91xx 业务码在这里给针对性文案，不让拦截器先弹一条通用提示
      const result = await saveSchedulesBatch(
        { stationId: effectiveStationId.value, items: [...dirty.value.values()] },
        { silent: true }
      )
      ElMessage.success(`已保存 ${result.saved} 处排班${result.removed ? `，清空 ${result.removed} 处` : ''}`)
      await fetchMatrix()
    } catch (e) {
      const code = e && e.code
      if (code === ATTENDANCE_CODE.SHIFT_UNAVAILABLE) {
        ElMessage.warning('提交的班次已被删除或停用（9106），已刷新排班表，请重新选择班次')
        await fetchMatrix()
      } else {
        ElMessage.error((e && e.message) || '排班保存失败，请重试')
      }
    } finally {
      saving.value = false
    }
  }

  async function changeWeek(delta) {
    if (!(await confirmDiscard())) return
    weekStart.value = formatDate(addDays(localDate(weekStart.value), delta * 7))
    fetchMatrix()
  }

  async function goCurrentWeek() {
    if (!(await confirmDiscard())) return
    weekStart.value = formatDate(mondayOf(new Date()))
    fetchMatrix()
  }

  async function handleStationChange(nextStationId) {
    if (nextStationId === stationId.value) return
    // 未保存改动未确认时直接返回：stationId 不变，界面与数据保持一致
    if (!(await confirmDiscard())) return
    stationId.value = nextStationId
    discardChanges()
    fetchMatrix()
  }

  /**
   * 班次变化：只同步图例与下拉选项，不整体重拉矩阵——
   * 重拉会把排班表里未保存的改动一起冲掉，而班次增删改并不影响这些格子指向的 shiftId
   */
  async function handleShiftChanged() {
    if (effectiveStationId.value == null) return
    try {
      shifts.value = await getShifts(effectiveStationId.value)
    } catch (e) {
      /* 拦截器已统一提示，图例保持旧值不影响排班编辑 */
    }
  }

  async function refreshPage() {
    if (!(await confirmDiscard())) return
    fetchMatrix()
  }

  /** 关闭标签页 / 刷新时兜底提示：56 格编辑成本高，误关一次就是几分钟白干（A10-3） */
  function handleBeforeUnload(event) {
    if (!dirtyCount.value) return
    event.preventDefault()
    event.returnValue = ''
  }

  return {
    isAdmin,
    canWrite,
    loading,
    saving,
    matrixError,
    stations,
    stationId,
    weekStart,
    dates,
    shifts,
    rows,
    today,
    effectiveStationId,
    currentStationName,
    dirtyCount,
    totalCells,
    filledCells,
    weekLabel,
    headerSub,
    loadStations,
    fetchMatrix,
    markDirty,
    handleShiftChange,
    discardChanges,
    confirmDiscard,
    handleSave,
    changeWeek,
    goCurrentWeek,
    handleStationChange,
    handleShiftChanged,
    refreshPage,
    handleBeforeUnload
  }
}
