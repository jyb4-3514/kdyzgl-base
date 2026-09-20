<script setup>
import { computed } from 'vue'
import { CLOCK_END_OPTIONS, CLOCK_OPTIONS, validateValue } from '../utils/configCenter.js'

/**
 * 动态值控件（设计 E.1 Atom / A.1.1）
 * 按配置项的 5 种值类型渲染对应控件：单选项下拉 / 数字输入 / 文本输入 / 时间下拉 / 起止双下拉。
 * 只负责「值 → 控件 + 字段级校验结论上报」，错误文案的落位由父级 el-form-item 决定（C.2 前端即时层）。
 *
 * 为什么不让 el-input-number 自己设 min/max/precision：Element 会据此静默钳制输入（99 → 10、3.5 → 4），
 * 用户看不到任何提示却存入了别的值。这里一律不设界，改为失焦/变更时用 validateValue 给显式红字。
 */
const props = defineProps({
  item: { type: Object, required: true },
  modelValue: { type: [String, Number, Array], default: null },
  // 单选项的候选来源；非单选项传 null 即可
  optionSet: { type: Object, default: null },
  disabled: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  ariaLabel: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue', 'validate'])

const constraints = computed(() => props.item.constraints || {})

const options = computed(() =>
  props.optionSet ? [...props.optionSet.options].filter((option) => option.enabled).sort((a, b) => a.sort - b.sort) : []
)

/** 空值判定：时间段的两段都空才算空（与 validateValue 的 isBlank 口径对齐） */
function isBlankValue(value) {
  if (Array.isArray(value)) return value.every((part) => part == null || String(part).trim() === '')
  return value == null || (typeof value === 'string' && value.trim() === '')
}

/**
 * 即时校验上报：空值不上报「必填」，避免未触碰的空白字段一进表单就飘红（必填仍由提交时兜底）。
 * 上报后由父级写入 fieldErrors，落在 el-form-item 的红字位。
 */
function notifyValidate(value) {
  if (props.disabled) return
  emit('validate', isBlankValue(value) ? '' : validateValue(props.item, value, props.optionSet))
}

function update(value) {
  emit('update:modelValue', value)
}

/* 时间段用两个下拉表达，值统一为 [开始, 结束] */
const range = computed(() => (Array.isArray(props.modelValue) ? props.modelValue : ['', '']))
function updateRange(index, value) {
  const next = [...range.value]
  next[index] = value
  update(next)
  notifyValidate(next)
}
</script>

<template>
  <el-skeleton v-if="loading" :rows="1" animated />

  <el-select
    v-else-if="item.valueType === 'SINGLE_SELECT'"
    class="value-field"
    :model-value="modelValue"
    :disabled="disabled"
    :aria-label="ariaLabel || item.name"
    clearable
    placeholder="请选择"
    @update:model-value="update"
    @change="notifyValidate"
  >
    <el-option v-for="option in options" :key="option.optionKey" :label="option.label" :value="option.optionKey" />
  </el-select>

  <!-- 不设 min/max/precision：让越界与非整数原样进入校验，而不是被控件静默改写 -->
  <div v-else-if="item.valueType === 'NUMBER'" class="value-field value-field--number">
    <el-input-number
      :model-value="modelValue"
      :step="constraints.step || 1"
      :disabled="disabled"
      :aria-label="ariaLabel || item.name"
      @update:model-value="update"
      @change="notifyValidate"
    />
    <span v-if="item.unit" class="value-field__unit">{{ item.unit }}</span>
  </div>

  <el-input
    v-else-if="item.valueType === 'TEXT'"
    class="value-field"
    :model-value="modelValue"
    :maxlength="constraints.maxLen == null ? undefined : constraints.maxLen"
    :show-word-limit="constraints.maxLen != null"
    :disabled="disabled"
    :aria-label="ariaLabel || item.name"
    :placeholder="constraints.patternHint || '请输入'"
    @update:model-value="update"
    @change="notifyValidate"
  />

  <el-select
    v-else-if="item.valueType === 'TIME'"
    class="value-field"
    :model-value="modelValue"
    :disabled="disabled"
    :aria-label="ariaLabel || item.name"
    clearable
    placeholder="请选择时间"
    @update:model-value="update"
    @change="notifyValidate"
  >
    <el-option v-for="time in CLOCK_OPTIONS" :key="time" :label="time" :value="time" />
  </el-select>

  <!-- 时间段：结束允许 24:00（allowEnd2400 默认 true，与 store.isEndClock 口径一致） -->
  <div v-else-if="item.valueType === 'TIME_RANGE'" class="value-field value-field--range">
    <el-select
      class="value-field__part"
      :model-value="range[0]"
      :disabled="disabled"
      :aria-label="`${ariaLabel || item.name}开始时间`"
      placeholder="开始"
      @update:model-value="updateRange(0, $event)"
    >
      <el-option v-for="time in CLOCK_OPTIONS" :key="time" :label="time" :value="time" />
    </el-select>
    <span class="value-field__sep">至</span>
    <el-select
      class="value-field__part"
      :model-value="range[1]"
      :disabled="disabled"
      :aria-label="`${ariaLabel || item.name}结束时间`"
      placeholder="结束"
      @update:model-value="updateRange(1, $event)"
    >
      <el-option
        v-for="time in constraints.allowEnd2400 === false ? CLOCK_OPTIONS : CLOCK_END_OPTIONS"
        :key="time"
        :label="time"
        :value="time"
      />
    </el-select>
  </div>

  <span v-else class="value-field__unsupported">暂不支持该值类型</span>
</template>

<style scoped lang="scss">
.value-field {
  width: 100%;

  &--number {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
  }

  &--range {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
  }

  &__part {
    flex: 1;
  }

  &__sep {
    color: var(--text-3);
    font-size: var(--fs-caption);
  }

  &__unit {
    font-size: var(--fs-caption);
    color: var(--text-3);
  }

  &__unsupported {
    font-size: var(--fs-caption);
    color: var(--text-3);
  }
}
</style>
