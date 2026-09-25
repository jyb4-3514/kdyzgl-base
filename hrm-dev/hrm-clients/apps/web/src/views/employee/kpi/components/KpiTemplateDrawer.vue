<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Plus } from '@element-plus/icons-vue'
import { KPI_DIRECTION, KPI_METRIC_TYPE, KPI_ROLE_SCOPE, KPI_SCORE_MODE } from '@kdyzgl/shared/constants/dict.js'
import {
  createKpiMetric,
  deleteKpiMetric,
  getKpiMetrics,
  saveKpiMetricBatch,
  updateKpiMetric
} from '../../../../api/kpi.js'
import StateBlock from '../../../../components/StateBlock.vue'

/**
 * KPI 指标模板编辑器（B7.4，需求7 的重点）
 *
 * 设计目标：让「权重合计 ≠ 100%」这件事在用户改的时候就看得见，而不是点保存才被拒。
 * 因此权重合计条常驻不折叠、合计不为 100% 时保存按钮直接禁用并写明差额。
 *
 * 为什么是「草稿 + 一次性保存」而不是逐条即时保存：
 * 契约的单指标接口每次都会校验启用指标合计 = 100%，调权重/切启用天生是多步操作（如 30/20 → 40/10、
 * 停用一个权重 5 的指标），任何中间态都不等于 100%。所以草稿在本地攒完，保存时按固定次序提交：
 * ① 新增指标（权重 0、停用态，不破坏既有合计）→ ② 批量提交权重与启用态（一次原子校验）→
 * ③ 逐条提交非权重字段 → ④ 删除标记项（此时其权重已置 0 且停用，删除后合计仍为 100）。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  // 只读降级：非 ADMIN 时整个编辑器改为纯展示（不渲染保存），而不是让用户点了才被 403 拦下
  canWrite: { type: Boolean, default: true }
})

const emit = defineEmits(['update:modelValue', 'saved'])

const MAX_ITEMS = 10

const loading = ref(false)
const error = ref(false)
const saving = ref(false)
const rows = ref([])

let uidSeed = 0
const nextUid = () => (uidSeed += 1)

/** 懒加载：把服务端结构摊平成可编辑的草稿行（scoreRule 拆成两个字段，避免深层对象在表单里到处 copy） */
async function load() {
  loading.value = true
  error.value = false
  try {
    const data = await getKpiMetrics()
    rows.value = (data.list || []).map((item, index) => {
      const row = {
        uid: nextUid(),
        id: item.id,
        metricKey: item.metricKey,
        metricName: item.metricName,
        metricType: item.metricType,
        targetValue: item.targetValue,
        unit: item.unit,
        direction: item.direction,
        scoreRuleMode: (item.scoreRule || {}).mode || 'LINEAR',
        scoreRuleFullScore: (item.scoreRule || {}).fullScore === undefined ? 100 : (item.scoreRule || {}).fullScore,
        roleScope: item.roleScope ? [...item.roleScope] : [],
        weight: item.weight,
        enabled: item.enabled === 1,
        sortOrder: item.sortOrder,
        remark: item.remark || '',
        deleted: false
      }
      // 快照用于「只提交真正改过的行」，避免一次保存把全部指标都 PUT 一遍
      row.original = finalize(row, index)
      return row
    })
  } catch (e) {
    error.value = true
  } finally {
    loading.value = false
  }
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) load()
  },
  { immediate: true }
)

const aliveRows = computed(() => rows.value.filter((row) => !row.deleted))
const enabledRows = computed(() => aliveRows.value.filter((row) => row.enabled))
const weightSum = computed(() => enabledRows.value.reduce((sum, row) => sum + Number(row.weight || 0), 0))
const weightDiff = computed(() => weightSum.value - 100)

/** 权重合计三态（B7.4）：不足 warning、超出 danger、达标 success */
const weightState = computed(() => {
  if (!enabledRows.value.length) return { tone: 'danger', text: '至少一项指标权重大于 0' }
  if (weightDiff.value === 0) return { tone: 'success', text: '已平衡' }
  if (weightDiff.value < 0) return { tone: 'warning', text: `还差 ${-weightDiff.value}%` }
  return { tone: 'danger', text: `超出 ${weightDiff.value}%` }
})

/** 任一启用指标的权重非法时不提交：字段级已在输入框上提示，这里只挡提交 */
const invalidRows = computed(() =>
  aliveRows.value.filter(
    (row) =>
      !row.metricName.trim() ||
      row.targetValue === null ||
      row.targetValue === '' ||
      !Number.isFinite(Number(row.targetValue))
  )
)

const saveDisabledReason = computed(() => {
  if (!props.canWrite) return '仅超级管理员可编辑指标模板'
  if (!enabledRows.value.length) return '至少一项指标权重大于 0'
  if (weightDiff.value !== 0) return `权重合计需为 100%（当前 ${weightSum.value}%）`
  if (invalidRows.value.length) return '存在未填写完整或目标值非法的指标'
  if (!aliveRows.value.length) return '至少保留一项指标'
  return ''
})

const canSave = computed(() => !saveDisabledReason.value)

/** 单指标自动 100%：只有一项指标时让用户去凑权重没有意义，直接锁定（B7.4） */
const isSingleMetric = computed(() => enabledRows.value.length === 1 && aliveRows.value.length === 1)

watch(isSingleMetric, (single) => {
  if (single && aliveRows.value[0]) {
    aliveRows.value[0].weight = 100
    aliveRows.value[0].enabled = true
  }
})

function addRow() {
  if (aliveRows.value.length >= MAX_ITEMS) {
    ElMessage.warning(`指标项最多 ${MAX_ITEMS} 项`)
    return
  }
  rows.value.push({
    uid: nextUid(),
    id: null,
    // 指标标识由前端生成：契约要求大写字母/数字/下划线且全局唯一，界面上让用户填一串标识属于无效负担
    metricKey: `CUSTOM_${nextUid()}`,
    metricName: '',
    metricType: 'OTHER',
    targetValue: 0,
    unit: '',
    direction: 'UP',
    scoreRuleMode: 'LINEAR',
    scoreRuleFullScore: 100,
    roleScope: [],
    weight: 0,
    enabled: true,
    sortOrder: rows.value.length + 1,
    remark: '',
    deleted: false
  })
}

function removeRow(row) {
  ElMessageBox.confirm(
    `删除指标「${row.metricName || '未命名指标'}」后，已生成的历史结果不受影响，但未生成的周期将按新模板计算。`,
    '删除指标',
    { confirmButtonText: '确认删除', cancelButtonText: '再想想', type: 'warning' }
  )
    .then(() => {
      if (row.id) row.deleted = true
      else rows.value = rows.value.filter((item) => item.uid !== row.uid)
    })
    .catch(() => {})
}

/** 拖拽排序不引入新依赖，用上移/下移（B7.4 明确约定）：直接换 rows 里的位置，顺序即提交顺序 */
function moveRow(row, step) {
  const list = aliveRows.value
  const index = list.indexOf(row)
  const target = index + step
  if (index < 0 || target < 0 || target >= list.length) return
  const from = rows.value.indexOf(list[index])
  const to = rows.value.indexOf(list[target])
  const current = rows.value[from]
  rows.value[from] = rows.value[to]
  rows.value[to] = current
}

/** 目标值必须是非负数字；空串与非法值都挡在提交前 */
function normalizeTarget(row) {
  const value = Number(row.targetValue)
  return Number.isFinite(value) && value >= 0 ? value : null
}

/** 非权重字段（指标标识不可改、权重与启用态由批量接口承担） */
function payloadOf(row) {
  return {
    metricName: row.metricName.trim(),
    metricType: row.metricType,
    targetValue: normalizeTarget(row),
    unit: row.unit,
    direction: row.direction,
    scoreRule: { mode: row.scoreRuleMode, fullScore: Number(row.scoreRuleFullScore) || 100 },
    roleScope: row.roleScope.length ? [...row.roleScope] : null,
    remark: row.remark ? row.remark : null
  }
}

/** 「非权重字段 + 排序」的归一化快照：与加载时的快照比对，决定这一行要不要发 PUT */
const finalize = (row, index) => JSON.stringify({ ...payloadOf(row), sortOrder: index + 1 })

async function handleSave() {
  if (!canSave.value) return
  saving.value = true
  try {
    // ① 新增：权重 0 且停用，既不改变启用集合、也不改变合计，服务端的合计校验必然通过
    const createdIds = new Map()
    for (const row of aliveRows.value.filter((item) => !item.id)) {
      const created = await createKpiMetric({ ...payloadOf(row), metricKey: row.metricKey, weight: 0, enabled: 0 })
      createdIds.set(row.uid, created.id)
    }
    const resolveId = (row) => row.id || createdIds.get(row.uid)

    // ② 批量提交最终权重与启用态：所有行的最终态合在一起只校验一次合计
    const batchItems = rows.value.map((row) => ({
      id: resolveId(row),
      weight: row.deleted ? 0 : Number(row.weight),
      enabled: row.deleted || !row.enabled ? 0 : 1
    }))
    await saveKpiMetricBatch(batchItems)

    // ③ 非权重字段：此时启用集合合计已是 100%，逐条提交不会再触发权重校验；只提交改过的行
    for (const [index, row] of aliveRows.value.entries()) {
      if (!row.id || finalize(row, index) === row.original) continue
      await updateKpiMetric(row.id, { ...payloadOf(row), sortOrder: index + 1 })
    }

    // ④ 删除：这些行已在 ② 中置为停用且权重 0，删除不会让合计偏离 100%
    for (const row of rows.value.filter((item) => item.deleted && item.id)) {
      await deleteKpiMetric(row.id)
    }

    ElMessage.success('指标模板已保存')
    emit('saved')
    emit('update:modelValue', false)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    title="指标模板"
    size="min(var(--drawer-w), 92vw)"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <StateBlock v-if="error" variant="error" title="指标模板加载失败" @action="load" />

    <StateBlock
      v-else-if="!loading && !aliveRows.length"
      variant="empty"
      title="暂无考核指标"
      description="至少添加一项指标并让启用权重合计为 100%"
      :action-text="canWrite ? '添加指标' : ''"
      @action="addRow"
    />

    <div v-else v-loading="loading" class="kpi-template">
      <p class="kpi-template__tip">
        权重只对「启用」的指标计入合计，合计必须为 100% 才能保存。适用角色留空表示全员适用。
      </p>

      <div class="kpi-template__list">
        <div v-for="(row, index) in aliveRows" :key="row.uid" class="kpi-template__row">
          <div class="kpi-template__row-head">
            <span class="kpi-template__row-index">{{ index + 1 }}</span>
            <el-input
              v-model="row.metricName"
              placeholder="指标名称（1-50 字）"
              maxlength="50"
              class="kpi-template__name"
            />
            <el-switch v-model="row.enabled" :disabled="!canWrite || isSingleMetric" active-text="启用" inline-prompt />
            <el-button-group class="kpi-template__move">
              <el-button
                size="small"
                :disabled="!canWrite || index === 0"
                aria-label="上移该指标"
                @click="moveRow(row, -1)"
              >
                上移
              </el-button>
              <el-button
                size="small"
                :disabled="!canWrite || index === aliveRows.length - 1"
                aria-label="下移该指标"
                @click="moveRow(row, 1)"
              >
                下移
              </el-button>
            </el-button-group>
            <el-button link type="danger" :icon="Delete" :disabled="!canWrite" @click="removeRow(row)">删除</el-button>
          </div>

          <div class="kpi-template__grid">
            <label class="kpi-template__field">
              <span>指标类型</span>
              <el-select v-model="row.metricType" :disabled="!canWrite" size="small">
                <el-option v-for="(item, key) in KPI_METRIC_TYPE" :key="key" :value="key" :label="item.label" />
              </el-select>
            </label>

            <label class="kpi-template__field">
              <span>方向</span>
              <el-select v-model="row.direction" :disabled="!canWrite" size="small">
                <el-option v-for="(item, key) in KPI_DIRECTION" :key="key" :value="key" :label="item.label" />
              </el-select>
            </label>

            <label class="kpi-template__field">
              <span>目标值</span>
              <el-input v-model="row.targetValue" :disabled="!canWrite" size="small" placeholder="不小于 0" />
            </label>

            <label class="kpi-template__field">
              <span>单位</span>
              <el-input v-model="row.unit" :disabled="!canWrite" size="small" maxlength="4" placeholder="件 / % / 分" />
            </label>

            <label class="kpi-template__field">
              <span>评分规则</span>
              <el-select v-model="row.scoreRuleMode" :disabled="!canWrite" size="small">
                <el-option v-for="(item, key) in KPI_SCORE_MODE" :key="key" :value="key" :label="item.label" />
              </el-select>
            </label>

            <label class="kpi-template__field">
              <span>满分</span>
              <el-input v-model="row.scoreRuleFullScore" :disabled="!canWrite" size="small" placeholder="0-100" />
            </label>

            <label class="kpi-template__field">
              <span>适用角色（留空=全员）</span>
              <el-select v-model="row.roleScope" multiple collapse-tags :disabled="!canWrite" size="small">
                <el-option v-for="(item, key) in KPI_ROLE_SCOPE" :key="key" :value="key" :label="item.label" />
              </el-select>
            </label>

            <label class="kpi-template__field">
              <span>权重（%）</span>
              <!-- 单指标时权重恒为 100，置灰避免用户去凑数（B7.4） -->
              <el-input
                v-model="row.weight"
                :disabled="!canWrite || isSingleMetric || !row.enabled"
                size="small"
                :title="
                  isSingleMetric ? '只有一项指标时权重固定为 100%' : !row.enabled ? '停用的指标不计入权重合计' : ''
                "
              />
            </label>
          </div>

          <p v-if="row.metricName !== '' && row.metricName.trim() === ''" class="kpi-template__error">
            指标名称不可为空白
          </p>
          <p v-else-if="normalizeTarget(row) === null" class="kpi-template__error">目标值须为不小于 0 的数字</p>
        </div>
      </div>

      <el-button :icon="Plus" :disabled="!canWrite || aliveRows.length >= MAX_ITEMS" @click="addRow">
        添加指标
      </el-button>
      <span v-if="aliveRows.length >= MAX_ITEMS" class="kpi-template__limit">已达 {{ MAX_ITEMS }} 项上限</span>

      <!-- 权重合计条：常驻不折叠，三态配色 + 差额文案（B7.4） -->
      <div class="kpi-template__sum" :class="`is-${weightState.tone}`">
        <span class="kpi-template__sum-label">权重合计</span>
        <span class="kpi-template__sum-value">{{ weightSum }}%</span>
        <span class="kpi-template__sum-track">
          <span class="kpi-template__sum-fill" :style="{ width: `${Math.min(100, weightSum)}%` }" />
        </span>
        <span class="kpi-template__sum-text">{{ weightState.text }}</span>
      </div>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button
        v-if="canWrite"
        type="primary"
        :loading="saving"
        :disabled="!canSave"
        :title="saveDisabledReason"
        @click="handleSave"
      >
        保存
      </el-button>
      <span v-else class="kpi-template__readonly">当前账号为只读，指标模板仅超级管理员可编辑</span>
    </template>
  </el-drawer>
</template>

<style scoped lang="scss">
.kpi-template {
  &__tip {
    margin: 0 0 var(--sp-4);
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    color: var(--text-3);
  }

  &__list {
    display: flex;
    flex-direction: column;
    gap: var(--sp-3);
    margin-bottom: var(--sp-4);
  }

  &__row {
    padding: var(--sp-3);
    border: 1px solid var(--border-line);
    border-radius: var(--r-md);
    background-color: var(--surface-sub);
  }

  &__row-head {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
    margin-bottom: var(--sp-3);
  }

  &__row-index {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 22px;
    height: 22px;
    flex-shrink: 0;
    border-radius: var(--r-full);
    background-color: var(--color-primary-surface);
    color: var(--color-primary-strong);
    font-size: var(--fs-caption);
    font-variant-numeric: tabular-nums;
  }

  &__name {
    flex: 1;
    min-width: 0;
  }

  &__grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--sp-2) var(--sp-3);
  }

  &__field {
    display: flex;
    flex-direction: column;
    gap: var(--sp-1);
    font-size: var(--fs-caption);
    color: var(--text-3);
  }

  &__error {
    margin: var(--sp-2) 0 0;
    font-size: var(--fs-caption);
    color: var(--state-danger-fg);
  }

  &__limit {
    margin-left: var(--sp-2);
    font-size: var(--fs-caption);
    color: var(--text-3);
  }

  &__sum {
    display: flex;
    align-items: center;
    gap: var(--sp-3);
    margin-top: var(--sp-4);
    padding: var(--sp-3);
    border: 1px solid var(--sum-border);
    border-radius: var(--r-md);
    background-color: var(--sum-bg);
    color: var(--sum-fg);

    &.is-success {
      --sum-bg: var(--state-success-bg);
      --sum-fg: var(--state-success-fg);
      --sum-border: var(--state-success-border);
    }

    &.is-warning {
      --sum-bg: var(--state-warning-bg);
      --sum-fg: var(--state-warning-fg);
      --sum-border: var(--state-warning-border);
    }

    &.is-danger {
      --sum-bg: var(--state-danger-bg);
      --sum-fg: var(--state-danger-fg);
      --sum-border: var(--state-danger-border);
    }
  }

  &__sum-label {
    font-size: var(--fs-caption);
  }

  &__sum-value {
    font-size: var(--fs-num-sm);
    font-weight: var(--fw-semibold);
    font-variant-numeric: tabular-nums;
  }

  &__sum-track {
    flex: 1;
    height: 6px;
    border-radius: var(--r-full);

    /* 进度条轨道白色底：语义是「轨道」而非卡面/反色，无对应 L2，保留 L1 直引（P2-4 已登记） */
    background-color: var(--c-neutral-0);
    overflow: hidden;
  }

  &__sum-fill {
    display: block;
    height: 100%;
    border-radius: var(--r-full);
    background-color: var(--sum-fg);
  }

  &__sum-text {
    font-size: var(--fs-caption);
  }

  &__readonly {
    font-size: var(--fs-caption);
    color: var(--text-3);
  }
}
</style>
