<script setup>
import { computed } from 'vue'
import { COLLECT_FREQUENCY, COLLECT_STATE, SYNC_STATUS, dictLabel } from '@kdyzgl/shared/constants/dict'
import { useSyncConfigMeta } from '../composables/useSyncConfigMeta.js'
import StateBlock from '../../../components/StateBlock.vue'
import StatusTag from '../../../components/StatusTag.vue'

/**
 * 采集配置表（需求1，B1.2 ③ / Organism 定位）
 * 只做展示与事件上报，写操作（切换开关 / 打开抽屉）由页面统一处理，避免同一份 API 调用散在两个组件里。
 * 空态按 A6-5 明确不适用：8 个驿站恒有配置行，真正的「空」是单站 UNCONFIGURED，用行内标签表达。
 *
 * 本次动态化（设计 E.2 ②）：频次 / 数据源列改读配置中心的选项集显示名——
 * 旧码（HOURLY 等）由元数据的 legacyCodes 映射，过渡期不出现裸 Key；元数据未就绪时退回本地字典。
 */
const props = defineProps({
  configs: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  error: { type: Boolean, default: false },
  canWrite: { type: Boolean, default: false },
  // 正在切换开关的驿站：只让该行转圈，避免整表都在 loading（A6-2 同类问题）
  actingStationId: { type: [Number, String], default: null }
})

const emit = defineEmits(['retry', 'edit', 'toggle'])

const meta = useSyncConfigMeta()
const metaReady = computed(() => meta.items.value.length > 0)

/** 排序权重：异常 → 未配置 → 正常 → 已停用（B1.2 ③），同权重按驿站号升序保持稳定 */
const STATE_ORDER = { ABNORMAL: 0, UNCONFIGURED: 1, NORMAL: 2, DISABLED: 3 }

const sortedConfigs = computed(() =>
  [...props.configs].sort((a, b) => {
    const diff = (STATE_ORDER[a.collectState] ?? 9) - (STATE_ORDER[b.collectState] ?? 9)
    return diff !== 0 ? diff : a.stationId - b.stationId
  })
)

const stateTagOf = (row) => COLLECT_STATE[row.collectState] || null

/** 频次显示名：优先选项集（含旧码映射），元数据未就绪时退回本地字典 */
const frequencyText = (row) =>
  metaReady.value
    ? meta.labelOf('collect_frequency', row.frequency, '—')
    : dictLabel(COLLECT_FREQUENCY, row.frequency, '—')

/** 数据源显示名：旧字段已是中文名，有新模型时以 optionKey 反查显示名，避免改名后回显旧名 */
const dataSourceText = (row) => {
  const key = row.values ? row.values.data_source : null
  if (metaReady.value && key) return meta.labelOf('data_source', key, row.dataSource || '—')
  return row.dataSource || '—'
}

/**
 * 数值型配置项列（重试 / 超时）：字段未纳入接口返回时显示「—」并给 title，
 * 不渲染成「0」误导用户（E.2 ① 的字段缺口降级口径）。
 */
const numberText = (row, itemKey, unit) => {
  const value = row.values ? row.values[itemKey] : undefined
  return value == null || value === '' ? '—' : `${value}${unit ? ` ${unit}` : ''}`
}
const numberTitle = (row, itemKey) => {
  const value = row.values ? row.values[itemKey] : undefined
  return value == null || value === '' ? '该配置项尚未启用或未纳入接口返回' : ''
}

/** 时段为空时给「未设置」而不是「-」，与「08:00 - 20:00」形成明确对比 */
const timeRangeOf = (row) => {
  if (!row.collectStartTime && !row.collectEndTime) return '未设置'
  return `${row.collectStartTime || '未设置'} - ${row.collectEndTime || '未设置'}`
}

const lastCollectText = (row) =>
  row.lastCollectStatus === 'NEVER' || !row.lastCollectTime ? '从未采集' : row.lastCollectTime

/** 开关禁用原因：把「为什么点不动」写在 title 里，避免用户反复尝试 */
function switchTitle(row) {
  if (!props.canWrite) return '仅超级管理员可修改采集配置'
  if (row.status === 0) return '该驿站已停用，不可开启采集'
  return row.enabled ? '采集进行中，关闭后该驿站将停止包裹采集' : '开启后按配置频次采集包裹'
}
</script>

<template>
  <div class="collect-table">
    <StateBlock v-if="error" variant="error" title="采集配置加载失败" @action="emit('retry')" />

    <template v-else>
      <el-table v-loading="loading" class="sticky-table" :data="sortedConfigs" border>
        <el-table-column prop="stationName" label="驿站" min-width="110" fixed="left" show-overflow-tooltip />
        <el-table-column label="采集状态" width="100" align="center">
          <template #default="{ row }">
            <StatusTag
              :dict="COLLECT_STATE"
              :value="row.collectState"
              :variant="(stateTagOf(row) || {}).variant || 'soft'"
            />
          </template>
        </el-table-column>
        <el-table-column label="采集开关" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled"
              :loading="String(actingStationId) === String(row.stationId)"
              :disabled="!canWrite || row.status === 0"
              :title="switchTitle(row)"
              :aria-label="`${row.stationName}采集开关`"
              @change="(value) => emit('toggle', row, value)"
            />
          </template>
        </el-table-column>
        <el-table-column label="采集频次" width="110" align="center">
          <template #default="{ row }">{{ frequencyText(row) }}</template>
        </el-table-column>
        <el-table-column label="数据源" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ dataSourceText(row) }}</template>
        </el-table-column>
        <el-table-column label="采集时段" min-width="150">
          <template #default="{ row }">
            <span :class="{ 'is-muted': !row.collectStartTime && !row.collectEndTime }">{{ timeRangeOf(row) }}</span>
          </template>
        </el-table-column>
        <!-- 重试 / 超时来自配置中心，字段缺口时显示「—」并给 title（E.2 ①） -->
        <el-table-column label="重试" width="80" align="center">
          <template #default="{ row }">
            <span
              :class="{ 'is-muted': numberText(row, 'retry_times', '次') === '—' }"
              :title="numberTitle(row, 'retry_times')"
            >
              {{ numberText(row, 'retry_times', '次') }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="超时" width="90" align="center">
          <template #default="{ row }">
            <span
              :class="{ 'is-muted': numberText(row, 'timeout_minutes', '分钟') === '—' }"
              :title="numberTitle(row, 'timeout_minutes')"
            >
              {{ numberText(row, 'timeout_minutes', '分钟') }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="最后采集" min-width="160">
          <template #default="{ row }">
            <span :class="{ 'is-muted': lastCollectText(row) === '从未采集' }">{{ lastCollectText(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="最近批次" min-width="180">
          <template #default="{ row }">
            <div v-if="row.lastBatch" class="batch-cell">
              <span class="batch-cell__no">{{ row.lastBatch.batchNo }}</span>
              <StatusTag
                :dict="SYNC_STATUS"
                :value="row.lastBatch.status"
                :variant="row.lastBatch.status === 0 ? 'outline' : 'soft'"
              />
            </div>
            <span v-else class="is-muted">—</span>
          </template>
        </el-table-column>
        <!-- 只读角色不渲染操作列：不做「点了才知道没权限」的入口（B1.3 无权限态） -->
        <el-table-column v-if="canWrite" label="操作" width="90" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :aria-label="`配置 ${row.stationName} 的采集参数`"
              @click="emit('edit', row)"
            >
              配置
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <p v-if="!canWrite" class="collect-table__readonly">
        只读视角：采集配置的修改仅超级管理员可执行，站长可查看本站采集状态
      </p>
    </template>
  </div>
</template>

<style scoped lang="scss">
.collect-table {
  .batch-cell {
    display: flex;
    align-items: center;
    gap: var(--sp-2);

    &__no {
      font-variant-numeric: tabular-nums;
      color: var(--text-1);
    }
  }

  .is-muted {
    color: var(--text-3);
  }

  &__readonly {
    margin: var(--sp-3) 0 0;
    font-size: var(--fs-caption);
    color: var(--text-3);
  }
}
</style>
