<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createConfigOption, updateConfigOption } from '../../../api/syncConfigCenter.js'
import { CLOCK_END_OPTIONS, CLOCK_OPTIONS, EXTRA_ATTR_FIELDS, validateOptionKey } from '../utils/configCenter.js'
import { useSilentSubmit } from '../composables/useSilentSubmit.js'

/**
 * 选项编辑弹窗（设计 B.2.4 / A.2.1）
 * 附加属性按选项集约定的字段描述渲染（间隔分钟 / 起止时间），不写死业务分支。
 * 选项 Key 编辑时锁定：Key 是稳定标识，CSV 与驿站覆盖都以它为准（A.2）。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  // 路由以配置项 Key 定位选项集（后端 resolveOptionSetKey），故两个 Key 都要传
  configItemKey: { type: String, default: '' },
  setKey: { type: String, default: '' },
  setName: { type: String, default: '' },
  option: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'saved'])

const { submitting, errorTip, run, clearError } = useSilentSubmit()

const isEdit = computed(() => !!props.option)
const formRef = ref(null)
const form = reactive({ optionKey: '', label: '', sort: 10, enabled: true, remark: '' })
const extraAttrs = reactive({})

const attrFields = computed(() => EXTRA_ATTR_FIELDS[props.setKey] || [])

function resetForm() {
  const option = props.option
  form.optionKey = option ? option.optionKey : ''
  form.label = option ? option.label : ''
  form.sort = option ? option.sort : 10
  form.enabled = option ? option.enabled : true
  form.remark = option ? option.remark || '' : ''
  Object.keys(extraAttrs).forEach((key) => delete extraAttrs[key])
  attrFields.value.forEach((field) => {
    const raw = option && option.extraAttrs ? option.extraAttrs[field.key] : null
    extraAttrs[field.key] = raw == null || raw === '' ? (field.type === 'NUMBER' ? null : '') : raw
  })
  clearError()
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) resetForm()
  }
)

const rules = {
  optionKey: [
    {
      validator: (rule, value, callback) => {
        if (isEdit.value) return callback()
        const message = validateOptionKey(value)
        message ? callback(new Error(message)) : callback()
      },
      trigger: 'blur'
    }
  ],
  label: [
    { required: true, message: '请填写「显示名」', trigger: 'blur' },
    { min: 1, max: 20, message: '显示名须为 1-20 字符', trigger: 'blur' }
  ]
}

/** 附加属性校验：字段约定为「有则必填」，文案与服务端 normalizeExtraAttrs 保持一致 */
function extraAttrsError() {
  if (!attrFields.value.length) return ''
  const missing = attrFields.value.find((field) => extraAttrs[field.key] == null || extraAttrs[field.key] === '')
  if (missing) return `请填写附加属性「${missing.label}」`
  if (props.setKey === 'time_template') {
    const [start, end] = [extraAttrs.startTime, extraAttrs.endTime]
    if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(String(start))) return '时段模板的开始时间格式须为 HH:mm'
    if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(String(end)) && end !== '24:00') return '时段模板的结束时间格式须为 HH:mm'
    if (start >= end && end !== '24:00') return '时段模板的结束时间须晚于开始时间'
  }
  return ''
}

async function handleSubmit() {
  const valid = await (formRef.value ? formRef.value.validate().catch(() => false) : true)
  if (!valid) return
  const attrsError = extraAttrsError()
  if (attrsError) {
    errorTip.value = attrsError
    return
  }
  const attrs = attrFields.value.length
    ? attrFields.value.reduce((acc, field) => {
        acc[field.key] = field.type === 'NUMBER' ? Number(extraAttrs[field.key]) : extraAttrs[field.key]
        return acc
      }, {})
    : null
  const payload = { label: form.label, extraAttrs: attrs, sort: form.sort, enabled: form.enabled, remark: form.remark }
  const result = await run(() =>
    isEdit.value
      ? updateConfigOption(props.configItemKey, props.option.optionKey, payload)
      : createConfigOption(props.configItemKey, { optionKey: form.optionKey, ...payload })
  )
  if (!result) return
  ElMessage.success(isEdit.value ? '选项已更新' : '选项已新增')
  emit('saved')
  emit('update:modelValue', false)
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="isEdit ? `编辑选项 · ${form.label}` : `新增选项 · ${setName || setKey}`"
    width="480px"
    append-to-body
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-alert v-if="errorTip" class="dialog-error" type="error" :closable="false" show-icon :title="errorTip" />

    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" @submit.prevent>
      <el-form-item label="选项 Key" prop="optionKey">
        <el-input
          v-model.trim="form.optionKey"
          :disabled="isEdit"
          maxlength="40"
          placeholder="如 EVERY_30M"
          :title="isEdit ? '选项 Key 是稳定标识，编辑时不可修改' : ''"
        />
      </el-form-item>

      <el-form-item label="显示名" prop="label">
        <el-input v-model.trim="form.label" maxlength="20" show-word-limit placeholder="如 每 30 分钟" />
      </el-form-item>

      <!-- 附加属性：按所属选项集约定渲染对应控件 -->
      <el-form-item v-for="field in attrFields" :key="field.key" :label="field.label">
        <div class="dialog-row">
          <el-input-number
            v-if="field.type === 'NUMBER'"
            v-model="extraAttrs[field.key]"
            :min="field.min"
            :max="field.max"
            :step="field.step"
            :aria-label="field.label"
          />
          <el-select
            v-else-if="field.type === 'TIME'"
            v-model="extraAttrs[field.key]"
            class="dialog-row__field"
            :aria-label="field.label"
          >
            <el-option
              v-for="time in field.allow2400 ? CLOCK_END_OPTIONS : CLOCK_OPTIONS"
              :key="time"
              :label="time"
              :value="time"
            />
          </el-select>
          <span v-if="field.unit" class="dialog-row__unit">{{ field.unit }}</span>
        </div>
      </el-form-item>

      <el-form-item label="排序">
        <el-input-number v-model="form.sort" :min="0" :max="9999" aria-label="排序" />
      </el-form-item>

      <el-form-item label="启用">
        <el-switch v-model="form.enabled" aria-label="选项启用开关" />
      </el-form-item>

      <el-form-item label="备注">
        <el-input v-model.trim="form.remark" maxlength="100" show-word-limit placeholder="可留空" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">{{ isEdit ? '保存' : '新增' }}</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.dialog-error {
  margin-bottom: var(--sp-4);
}

.dialog-row {
  display: flex;
  align-items: center;
  gap: var(--sp-2);

  &__field {
    flex: 1;
  }

  &__unit {
    font-size: var(--fs-caption);
    color: var(--text-3);
  }
}
</style>
