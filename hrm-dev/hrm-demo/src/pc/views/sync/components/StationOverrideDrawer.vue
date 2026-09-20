<script setup>
import { reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { saveSyncConfig } from '../../../api/syncConfig.js'
import { valueTextOf, validateValue } from '../utils/configCenter.js'
import { useSilentSubmit } from '../composables/useSilentSubmit.js'
import ConfigValueField from './ConfigValueField.vue'
import SourceBadge from './SourceBadge.vue'
import StateBlock from '../../../components/StateBlock.vue'

/**
 * 单站覆盖抽屉（设计 B.4 编辑视图 / B.5）
 * 逐项一行：配置项名 | 来源标记 | 当前值(控件) | 覆盖 / 恢复继承。
 * 未覆盖项控件禁用并展示全局默认值（灰字），点「覆盖」后控件启用、按钮变「恢复继承」；
 * 恢复继承在覆盖值 ≠ 全局默认值时要二次确认（值会丢失），文案沿用 B0.3 规范。
 * 保存走既有 PUT /sync/configs/:stationId 的 overrides / resetKeys 入参（契约 A.4 新模型）。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  station: { type: Object, default: null },
  items: { type: Array, default: () => [] },
  globalValues: { type: Object, default: () => ({}) },
  optionSetOf: { type: Function, default: null }
})

const emit = defineEmits(['update:modelValue', 'saved'])

const { submitting, errorTip, run, clearError } = useSilentSubmit()

const formRef = ref(null)
const rows = ref([])
const fieldErrors = reactive({})

const isBlank = (value) => value == null || (typeof value === 'string' && value.trim() === '')
const clone = (value) => (value === undefined || value === null ? null : JSON.parse(JSON.stringify(value)))

/** 覆盖范围：仅 STATION 允许按站覆盖；全局项只读展示（A.1 生效范围） */
const overridable = (item) => item.scope === 'STATION'

function resetRows() {
  const station = props.station || {}
  const overrides = station.overrides || {}
  const values = station.values || {}
  rows.value = props.items.map((item) => {
    const overridden = Object.prototype.hasOwnProperty.call(overrides, item.itemKey)
    return {
      item,
      overridden,
      originalOverridden: overridden,
      originalValue: overridden ? clone(overrides[item.itemKey]) : null,
      // 未覆盖项展示全局默认（灰字），已覆盖项展示覆盖值
      value: overridden ? clone(overrides[item.itemKey]) : clone(props.globalValues[item.itemKey]),
      // 配置项未纳入接口返回时（A.1.2 后两项的字段缺口）该行降级为不可用
      supported: Object.prototype.hasOwnProperty.call(values, item.itemKey)
    }
  })
  Object.keys(fieldErrors).forEach((key) => delete fieldErrors[key])
  clearError()
}

watch(
  () => [props.modelValue, props.station],
  ([visible]) => {
    if (visible) resetRows()
  },
  { immediate: false }
)

function startOverride(row) {
  if (!overridable(row.item)) return
  row.overridden = true
  if (isBlank(row.value)) row.value = clone(props.globalValues[row.item.itemKey])
}

/** 值控件的即时校验结论（失焦/变更）落到字段红字位；提交时再整体校验一次兜底（C.2 两层分工） */
function setFieldError(key, message) {
  if (message) fieldErrors[key] = message
  else delete fieldErrors[key]
}

/** 恢复继承：仅当原覆盖值确实存在且与全局默认不同才提示会丢值（B.5） */
async function restoreInherit(row) {
  const differs =
    row.originalOverridden && JSON.stringify(row.originalValue) !== JSON.stringify(props.globalValues[row.item.itemKey])
  if (differs) {
    const set = props.optionSetOf ? props.optionSetOf(row.item.optionSetKey) : null
    const globalText = valueTextOf(row.item, props.globalValues[row.item.itemKey], set)
    const currentText = valueTextOf(row.item, row.originalValue, set)
    try {
      await ElMessageBox.confirm(
        `将「${props.station.stationName}」的「${row.item.name}」恢复为全局默认「${globalText}」，本项当前覆盖值「${currentText}」将丢失。`,
        '恢复继承',
        { confirmButtonText: '确认恢复', cancelButtonText: '再想想', type: 'warning' }
      )
    } catch (e) {
      return
    }
  }
  row.overridden = false
  row.value = clone(props.globalValues[row.item.itemKey])
}

async function handleSave() {
  Object.keys(fieldErrors).forEach((key) => delete fieldErrors[key])
  const overrides = {}
  const resetKeys = []
  let firstErrorKey = ''
  rows.value.forEach((row) => {
    if (!row.supported || !overridable(row.item)) return
    if (row.overridden) {
      const message = validateValue(
        row.item,
        row.value,
        props.optionSetOf ? props.optionSetOf(row.item.optionSetKey) : null
      )
      if (message) {
        fieldErrors[row.item.itemKey] = message
        if (!firstErrorKey) firstErrorKey = row.item.itemKey
        return
      }
      overrides[row.item.itemKey] = row.value
    } else if (row.originalOverridden) {
      resetKeys.push(row.item.itemKey)
    }
  })
  if (firstErrorKey) {
    if (formRef.value) formRef.value.scrollToField(firstErrorKey)
    return
  }
  if (!Object.keys(overrides).length && !resetKeys.length) {
    ElMessage.info('本驿站覆盖没有改动')
    return
  }
  const result = await run(() => saveSyncConfig(props.station.stationId, { overrides, resetKeys }))
  if (!result) return
  ElMessage.success('驿站覆盖已保存')
  emit('saved')
  emit('update:modelValue', false)
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    :title="`驿站覆盖 · ${station ? station.stationName : ''}`"
    size="min(var(--drawer-w), 92vw)"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="override-drawer">
      <el-alert
        v-if="errorTip"
        class="override-drawer__alert"
        type="error"
        :closable="false"
        show-icon
        :title="errorTip"
      />

      <StateBlock
        v-if="!rows.length"
        variant="empty"
        title="暂无可覆盖的配置项"
        description="请先在「配置项与选项集」中新增并启用可覆盖配置项"
      />

      <el-form v-else ref="formRef" :model="{}" label-width="120px" @submit.prevent>
        <el-form-item
          v-for="row in rows"
          :key="row.item.itemKey"
          :prop="row.item.itemKey"
          :error="fieldErrors[row.item.itemKey]"
        >
          <template #label>
            <span class="override-drawer__label">{{ row.item.name }}</span>
          </template>
          <div class="override-drawer__row">
            <SourceBadge :source="row.overridden ? 'OVERRIDE' : 'INHERIT'" />
            <div class="override-drawer__field">
              <ConfigValueField
                v-model="row.value"
                :item="row.item"
                :option-set="optionSetOf ? optionSetOf(row.item.optionSetKey) : null"
                :disabled="!row.overridden || !row.supported"
                :aria-label="`${row.item.name} ${row.overridden ? '覆盖值' : '全局默认值'}`"
                @validate="(message) => setFieldError(row.item.itemKey, message)"
              />
              <p v-if="!row.overridden" class="override-drawer__hint">
                继承全局默认{{ row.supported ? '' : '（该配置项暂不支持）' }}
              </p>
            </div>
            <el-button
              v-if="!row.overridden"
              link
              type="primary"
              :disabled="!row.supported"
              :title="row.supported ? '' : '该配置项暂不支持设置'"
              @click="startOverride(row)"
              >覆盖</el-button
            >
            <el-button v-else link type="danger" @click="restoreInherit(row)">恢复继承</el-button>
          </div>
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <div class="override-drawer__footer">
        <el-button @click="emit('update:modelValue', false)">关闭</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSave">保存</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<style scoped lang="scss">
.override-drawer {
  &__alert {
    margin-bottom: var(--sp-4);
  }

  &__label {
    font-weight: var(--fw-medium);
  }

  &__row {
    display: flex;
    align-items: flex-start;
    gap: var(--sp-2);
    width: 100%;
  }

  &__field {
    flex: 1;
    min-width: 0;
  }

  &__hint {
    margin: var(--sp-1) 0 0;
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    color: var(--text-3);
  }

  &__footer {
    display: flex;
    justify-content: flex-end;
    gap: var(--sp-2);
  }
}
</style>
