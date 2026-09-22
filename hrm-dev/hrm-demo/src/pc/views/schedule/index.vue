<template>
  <div class="schedule-page">
    <PageHeader title="排班管理" :sub="headerSub" :loading="loading">
      <template #actions>
        <ScheduleHeaderActions
          :is-admin="isAdmin"
          :stations="stations"
          :station-id="stationId"
          :can-write="canWrite"
          :dirty-count="dirtyCount"
          :saving="saving"
          @station-change="handleStationChange"
          @batch-command="handleBatchCommand"
          @discard="discardChanges"
          @save="handleSave"
          @refresh="refreshPage"
        />
      </template>
    </PageHeader>

    <ScheduleWeekGrid
      :rows="rows"
      :dates="dates"
      :shifts="shifts"
      :loading="loading"
      :matrix-error="matrixError"
      :can-write="canWrite"
      :week-label="weekLabel"
      :filled-cells="filledCells"
      :total-cells="totalCells"
      :today="today"
      @refresh="refreshPage"
      @prev-week="changeWeek(-1)"
      @current-week="goCurrentWeek"
      @next-week="changeWeek(1)"
      @batch-row="openBatch('row', $event)"
      @batch-column="openBatch('column', null, $event)"
      @shift-change="handleShiftChange"
    />

    <ShiftManager
      :station-id="effectiveStationId"
      :station-name="currentStationName"
      :can-write="canWrite"
      @changed="handleShiftChanged"
    />

    <BatchSpreadDialog
      v-model="batchVisible"
      :mode="batchMode"
      :station-id="effectiveStationId"
      :station-name="currentStationName"
      :week-start="weekStart"
      :dates="dates"
      :rows="rows"
      :shifts="shifts"
      :seed="batchSeed"
      @spread-done="fetchMatrix"
      @apply="applyBatchChanges"
    />
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import PageHeader from '../../components/PageHeader.vue'
import ShiftManager from './components/ShiftManager.vue'
import BatchSpreadDialog from './components/BatchSpreadDialog.vue'
import ScheduleHeaderActions from './components/ScheduleHeaderActions.vue'
import ScheduleWeekGrid from './components/ScheduleWeekGrid.vue'
import { useScheduleMatrix } from './composables/useScheduleMatrix.js'
import { useScheduleBatch } from './composables/useScheduleBatch.js'

/**
 * 排班管理（行 = 员工，列 = 周一至周日）页面壳
 *
 * 只做装配与生命周期：取数/本地增量/保存/周与驿站切换在 useScheduleMatrix，
 * 批量工具的预览确认状态机在 useScheduleBatch，周矩阵与页头操作区各一个展示组件。
 */
const {
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
} = useScheduleMatrix()

const { batchVisible, batchMode, batchSeed, handleBatchCommand, openBatch, applyBatchChanges } = useScheduleBatch({
  canWrite,
  dirtyCount,
  confirmDiscard,
  discardChanges,
  rows,
  markDirty
})

onMounted(async () => {
  if (isAdmin.value) await loadStations()
  fetchMatrix()
  window.addEventListener('beforeunload', handleBeforeUnload)
})

onBeforeUnmount(() => window.removeEventListener('beforeunload', handleBeforeUnload))

/** 离开页面的路由守卫：与切周/切驿站的确认同口径（A10-3） */
onBeforeRouteLeave(() => (dirtyCount.value ? confirmDiscard() : true))
</script>
