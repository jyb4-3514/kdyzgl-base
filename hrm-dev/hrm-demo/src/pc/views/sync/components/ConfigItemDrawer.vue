<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createConfigItem, updateConfigItem } from '../../../api/syncConfigCenter.js'
import { VALUE_TYPE_OPTIONS, emptyValueOf, validateItemKey, validateValue } from '../utils/configCenter.js'
import { useSilentSubmit } from '../composables/useSilentSubmit.js'
import ConfigValueField from './ConfigValueField.vue'

/**
 * 配置项表单抽屉（设计 B.2.4 / E.1 Molecule）
 * 新增可填项 Key 与值类型；编辑两者锁定——改 Key 等于换一个项，改类型会破坏存量值（A.1）。
 * 约束区随值类型切换字段，默认值控件复用 ConfigValueField，避免两处各画一套 5 种控件。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  item: { type: Object, default: null },
  optionSets: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue', 'saved'])

const { submitting, errorTip, run, clearError } = useSilentSubmit()

const isEdit = computed(() => !!props.item)
const formRef = ref(null)

const form = reactive({
  itemKey: '',
  name: '',
  description: '',
  valueType: 'SINGLE_SELECT',
  required: true,
  defaultValue: null,
  unit: '',
  optionSetKey: '',
  scope: 'STATION',
  sort: 100,
  enabled: true
})

const constraints = reactive({})

function defaultConstraints(valueType) {
  if (valueType === 'NUMBER') return { min: 0, max: 100, step: 1, integerOnly: true, precision: 0 }
  if (valueType === 'TEXT') return { minLen: 1, maxLen: 20, pattern: '', patternHint: '' }
  if (valueType === 'TIME') return { min: null, max: null }
  if (valueType === 'TIME_RANGE') return { allowEnd2400: true }
  return {}
}

function applyConstraints(raw, valueType) {
  Object.keys(constraints).forEach((key) => delete constraints[key])
  Object.assign(constraints, defaultConstraints(valueType), raw || {})
}

function resetForm() {
  const item = props.item
  form.itemKey = item ? item.itemKey : ''
  form.name = item ? item.name : ''
  form.description = item ? item.description || '' : ''
  form.valueType = item ? item.valueType : 'SINGLE_SELECT'
  form.required = item ? item.required : true
  form.defaultValue = item ? item.defaultValue : emptyValueOf(form.valueType)
  form.unit = item ? item.unit || '' : ''
  form.optionSetKey = item ? item.optionSetKey || '' : props.optionSets[0]?.setKey || ''
  form.scope = item ? item.scope : 'STATION'
  form.sort = item ? item.sort : 100
  form.enabled = item ? item.enabled : true
  applyConstraints(item ? item.constraints : null, form.valueType)
  clearError()
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) resetForm()
  }
)

/** 切换值类型：默认值与约束都换了形态，必须重置（否则会提交与类型不符的脏约束） */
function handleTypeChange(valueType) {
  form.defaultValue = emptyValueOf(valueType)
  if (valueType === 'SINGLE_SELECT' && !form.optionSetKey) form.optionSetKey = props.optionSets[0]?.setKey || ''
  applyConstraints(null, valueType)
}

const currentOptionSet = computed(() => props.optionSets.find((set) => set.setKey === form.optionSetKey) || null)

/** 校验用的草稿项：与提交流程同源，避免校验口径与提交结构不一致 */
const draftItem = computed(() => ({
  itemKey: form.itemKey,
  name: form.name,
  valueType: form.valueType,
  required: form.required,
  unit: form.unit,
  constraints: constraints,
  optionSetKey: form.optionSetKey
}))

const rules = {
  itemKey: [
    {
      validator: (rule, value, callback) => {
        if (isEdit.value) return callback()
        const message = validateItemKey(value)
        message ? callback(new Error(message)) : callback()
      },
      trigger: 'blur'
    }
  ],
  name: [
    { required: true, message: '请填写「显示名」', trigger: 'blur' },
    { min: 1, max: 20, message: '显示名须为 1-20 字符', trigger: 'blur' }
  ],
  defaultValue: [
    {
      validator: (rule, value, callback) => {
        const message = validateValue(draftItem.value, value, currentOptionSet.value)
        message ? callback(new Error(message)) : callback()
      },
      trigger: 'change'
    }
  ]
}

/** 归一化约束：只提交本值类型认识的键（与 store.normalizeConstraints 同口径） */
function normalizedConstraints() {
  if (form.valueType === 'NUMBER') {
    return {
      min: constraints.min,
      max: constraints.max,
      step: constraints.step || 1,
      integerOnly: !!constraints.integerOnly,
      precision: 0
    }
  }
  if (form.valueType === 'TEXT') {
    return {
      minLen: constraints.minLen,
      maxLen: constraints.maxLen,
      pattern: constraints.pattern || null,
      patternHint: constraints.patternHint || null
    }
  }
  if (form.valueType === 'TIME') return { min: constraints.min || null, max: constraints.max || null }
  if (form.valueType === 'TIME_RANGE') return { allowEnd2400: constraints.allowEnd2400 !== false }
  return null
}

/** 约束编辑的允许区间（编辑态不靠 el-input-number 的 min/max 静默钳制，改由下方红字显式提示） */
const CONSTRAINT_LIMITS = {
  min: { label: '最小值', min: 0, max: 99999 },
  max: { label: '最大值', min: 0, max: 99999 },
  step: { label: '步进', min: 1, max: 1000 },
  minLen: { label: '最小长度', min: 0, max: 500 },
  maxLen: { label: '最大长度', min: 1, max: 500 }
}

/**
 * 约束字段的即时校验（越界 / 非整数 / 先后关系），文案说清「字段 + 要求 + 当前值」（C.3）。
 * 与提交时的 constraintConflict 同源，避免即时提示与提交拦截两套口径。
 */
const constraintErrors = computed(() => {
  const errors = {}
  Object.entries(CONSTRAINT_LIMITS).forEach(([key, rule]) => {
    const value = constraints[key]
    if (value == null || value === '') return
    if (!Number.isInteger(Number(value)) || Number(value) < rule.min || Number(value) > rule.max) {
      errors[key] = `「${rule.label}」须为 ${rule.min}-${rule.max} 的整数，当前为 ${value}`
    }
  })
  if (!errors.min && !errors.max && form.valueType === 'NUMBER' && Number(constraints.min) > Number(constraints.max)) {
    errors.max = `「最大值」不得小于最小值，当前为 ${constraints.min}-${constraints.max}`
  }
  if (
    !errors.minLen &&
    !errors.maxLen &&
    form.valueType === 'TEXT' &&
    Number(constraints.minLen) > Number(constraints.maxLen)
  ) {
    errors.maxLen = `「最大长度」不得小于最小长度，当前为 ${constraints.minLen}-${constraints.maxLen}`
  }
  if (form.sort == null || !Number.isInteger(Number(form.sort)) || form.sort < 0 || form.sort > 9999) {
    errors.sort = `「排序」须为 0-9999 的整数，当前为 ${form.sort == null ? '空' : form.sort}`
  }
  return errors
})

/** 提交前拦一次（服务端仍会兜底），取首个约束问题作为整体错误 */
function constraintConflict() {
  return Object.values(constraintErrors.value)[0] || ''
}

/** 默认值控件的即时校验：借 el-form 已声明的规则（trigger=change），文案与提交时同一套（C.3） */
function validateDefaultValue() {
  if (formRef.value) formRef.value.validateField('defaultValue').catch(() => {})
}

async function handleSubmit() {
  const valid = await (formRef.value ? formRef.value.validate().catch(() => false) : true)
  if (!valid) return
  const conflict = constraintConflict()
  if (conflict) {
    errorTip.value = conflict
    return
  }
  const payload = {
    name: form.name,
    description: form.description,
    required: form.required,
    defaultValue: form.defaultValue,
    unit: form.valueType === 'NUMBER' ? form.unit : '',
    constraints: normalizedConstraints(),
    optionSetKey: form.valueType === 'SINGLE_SELECT' ? form.optionSetKey : null,
    scope: form.scope,
    sort: form.sort,
    enabled: form.enabled
  }
  if (!isEdit.value) {
    payload.itemKey = form.itemKey
    payload.valueType = form.valueType
  }
  const result = await run(() =>
    isEdit.value ? updateConfigItem(props.item.itemKey, payload) : createConfigItem(payload)
  )
  if (!result) return
  ElMessage.success(isEdit.value ? '配置项已更新' : '配置项已新增')
  emit('saved')
  emit('update:modelValue', false)
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    :title="isEdit ? `编辑配置项 · ${form.name}` : '新增配置项'"
    size="min(var(--drawer-w), 92vw)"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-alert v-if="errorTip" class="drawer-error" type="error" :closable="false" show-icon :title="errorTip" />

    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" @submit.prevent>
      <el-form-item label="项 Key" prop="itemKey">
        <el-input
          v-model.trim="form.itemKey"
          :disabled="isEdit"
          maxlength="40"
          placeholder="如 collect_frequency"
          :title="isEdit ? '项 Key 是稳定标识，编辑时不可修改；改 Key 等于新建一项' : ''"
        />
      </el-form-item>

      <el-form-item label="显示名" prop="name">
        <el-input v-model.trim="form.name" maxlength="20" show-word-limit placeholder="如 采集频率" />
      </el-form-item>

      <el-form-item label="说明">
        <el-input v-model.trim="form.description" maxlength="100" show-word-limit placeholder="界面辅助说明，可留空" />
      </el-form-item>

      <el-form-item label="值类型">
        <el-select
          v-model="form.valueType"
          class="drawer-field"
          :disabled="isEdit"
          :title="isEdit ? '值类型决定控件与校验，修改会破坏存量值，故锁定' : ''"
          @change="handleTypeChange"
        >
          <el-option
            v-for="option in VALUE_TYPE_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </el-form-item>

      <el-form-item v-if="form.valueType === 'SINGLE_SELECT'" label="关联选项集">
        <el-select v-model="form.optionSetKey" class="drawer-field" placeholder="选择已有选项集">
          <el-option
            v-for="set in optionSets"
            :key="set.setKey"
            :label="`${set.name}（${set.setKey}）`"
            :value="set.setKey"
          />
        </el-select>
        <p class="drawer-hint">单选型的候选值由选项集提供，新增候选项请到右侧选项集面板操作</p>
      </el-form-item>

      <el-form-item :label="isEdit ? '默认值' : '默认值'" prop="defaultValue">
        <ConfigValueField
          v-model="form.defaultValue"
          :item="draftItem"
          :option-set="currentOptionSet"
          @validate="validateDefaultValue"
        />
        <p v-if="form.required" class="drawer-hint">
          必填项在驿站生效值上不可为空；全局默认可暂留空，未覆盖驿站将显示「未配置」
        </p>
      </el-form-item>

      <el-form-item v-if="form.valueType === 'NUMBER'" label="单位">
        <el-input v-model.trim="form.unit" class="drawer-field" maxlength="8" placeholder="如 次 / 分钟" />
      </el-form-item>

      <!-- 约束随值类型切换字段；数值输入不设 min/max（避免静默钳制），越界由 :error 红字显式提示 -->
      <template v-if="form.valueType === 'NUMBER'">
        <el-form-item label="取值范围" :error="constraintErrors.min || constraintErrors.max">
          <div class="drawer-row">
            <el-input-number v-model="constraints.min" :controls="false" aria-label="最小值" />
            <span class="drawer-row__sep">-</span>
            <el-input-number v-model="constraints.max" :controls="false" aria-label="最大值" />
            <span class="drawer-row__unit">最小 - 最大</span>
          </div>
        </el-form-item>
        <el-form-item label="步进" :error="constraintErrors.step">
          <div class="drawer-row">
            <el-input-number v-model="constraints.step" aria-label="步进" />
            <el-checkbox v-model="constraints.integerOnly">仅整数</el-checkbox>
          </div>
        </el-form-item>
      </template>

      <template v-else-if="form.valueType === 'TEXT'">
        <el-form-item label="长度约束" :error="constraintErrors.minLen || constraintErrors.maxLen">
          <div class="drawer-row">
            <el-input-number v-model="constraints.minLen" :controls="false" aria-label="最小长度" />
            <span class="drawer-row__sep">-</span>
            <el-input-number v-model="constraints.maxLen" :controls="false" aria-label="最大长度" />
            <span class="drawer-row__unit">字符</span>
          </div>
        </el-form-item>
        <el-form-item label="正则">
          <el-input v-model.trim="constraints.pattern" placeholder="可留空；如 ^[0-9]{6}$" />
        </el-form-item>
        <el-form-item label="格式说明">
          <el-input
            v-model.trim="constraints.patternHint"
            maxlength="50"
            placeholder="正则的人话示例，报错时展示给用户"
          />
        </el-form-item>
      </template>

      <template v-else-if="form.valueType === 'TIME'">
        <el-form-item label="可选区间">
          <div class="drawer-row">
            <el-input v-model.trim="constraints.min" placeholder="不早于，如 06:00" aria-label="最早时间" />
            <span class="drawer-row__sep">-</span>
            <el-input v-model.trim="constraints.max" placeholder="不晚于，如 23:00" aria-label="最晚时间" />
          </div>
        </el-form-item>
      </template>

      <el-form-item v-else-if="form.valueType === 'TIME_RANGE'" label="允许 24:00">
        <el-switch v-model="constraints.allowEnd2400" aria-label="结束时间是否允许 24:00" />
      </el-form-item>

      <el-form-item label="排序" :error="constraintErrors.sort">
        <el-input-number v-model="form.sort" aria-label="排序" />
      </el-form-item>

      <el-form-item label="生效范围">
        <el-radio-group v-model="form.scope">
          <el-radio value="STATION">可覆盖</el-radio>
          <el-radio value="GLOBAL">全局</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="启用">
        <el-switch v-model="form.enabled" aria-label="配置项启用开关" />
      </el-form-item>
    </el-form>

    <template #footer>
      <div class="drawer-footer">
        <el-button @click="emit('update:modelValue', false)">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">{{ isEdit ? '保存' : '新增' }}</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<style scoped lang="scss">
.drawer-error {
  margin-bottom: var(--sp-4);
}

.drawer-field {
  width: 100%;
}

.drawer-row {
  display: flex;
  align-items: center;
  gap: var(--sp-2);

  &__sep {
    color: var(--text-3);
    font-size: var(--fs-caption);
  }

  &__unit {
    font-size: var(--fs-caption);
    color: var(--text-3);
  }
}

.drawer-hint {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--sp-2);
}
</style>
