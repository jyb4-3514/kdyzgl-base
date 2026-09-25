<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { saveGlobalConfig } from '../../../api/syncConfigCenter.js'
import { SCOPE_LABEL, validateValue, valueTextOf } from '../utils/configCenter.js'
import { useSilentSubmit } from '../composables/useSilentSubmit.js'
import ConfigValueField from './ConfigValueField.vue'
import StateBlock from '../../../components/StateBlock.vue'

/**
 * 全局默认配置区（设计 B.3 / E.1 Organism）
 * 按启用中的配置项动态渲染，控件由 ConfigValueField 决定，本组件只负责
 * 「取值 → 校验 → 局部提交（只提改动项）」与「被 N 个驿站继承 / M 个驿站覆盖」的口径展示。
 * 为什么只提交改动项：全局默认含种子里刻意留空的必填项（data_source），全量提交会触发 9507。
 */
const props = defineProps({
  items: { type: Array, default: () => [] },
  globalValues: { type: Object, default: () => ({}) },
  // 各驿站配置 VO：用于统计继承/覆盖数（数据源是服务端返回的 sources，不在前端重算口径）
  configs: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  error: { type: Boolean, default: false },
  optionSetOf: { type: Function, default: null }
})

const emit = defineEmits(['retry', 'saved', 'locate'])

const { submitting, errorTip, run, clearError } = useSilentSubmit()

const formRef = ref(null)
const working = reactive({})
const fieldErrors = reactive({})
let snapshot = '{}'

const clone = (value) => (value === undefined || value === null ? null : JSON.parse(JSON.stringify(value)))

function syncFromProps() {
  Object.keys(working).forEach((key) => delete working[key])
  props.items.forEach((item) => {
    working[item.itemKey] = clone(props.globalValues[item.itemKey])
  })
  snapshot = JSON.stringify(working)
  Object.keys(fieldErrors).forEach((key) => delete fieldErrors[key])
  clearError()
}

watch(() => [props.items, props.globalValues], syncFromProps, { immediate: true, deep: true })

const dirtyKeys = computed(() => {
  const base = JSON.parse(snapshot)
  return props.items
    .map((item) => item.itemKey)
    .filter((key) => JSON.stringify(working[key]) !== JSON.stringify(base[key]))
})

/** 继承 / 覆盖计数：直接读服务端 sources（B.3 的「当前被 N 个驿站继承 / M 个驿站覆盖」） */
function countOf(itemKey) {
  let inherited = 0
  let overridden = 0
  props.configs.forEach((config) => {
    if (config.sources && config.sources[itemKey] === 'OVERRIDE') overridden += 1
    else inherited += 1
  })
  return { inherited, overridden }
}

/** 全局默认预览文本（图片列不展示，但无障碍播报与边界态用得到） */
const previewOf = (item) =>
  valueTextOf(item, working[item.itemKey], props.optionSetOf ? props.optionSetOf(item.optionSetKey) : null)

/** 值控件的即时校验结论（失焦/变更）落到字段红字位；提交时再整体校验一次兜底（C.2 两层分工） */
function setFieldError(key, message) {
  if (message) fieldErrors[key] = message
  else delete fieldErrors[key]
}

async function handleSave() {
  Object.keys(fieldErrors).forEach((key) => delete fieldErrors[key])
  let firstErrorKey = ''
  dirtyKeys.value.forEach((key) => {
    const item = props.items.find((row) => row.itemKey === key)
    const message = validateValue(item, working[key], props.optionSetOf ? props.optionSetOf(item.optionSetKey) : null)
    if (message) {
      fieldErrors[key] = message
      if (!firstErrorKey) firstErrorKey = key
    }
  })
  if (firstErrorKey) {
    // 提交时校验失败：滚动定位到首个错误项，不弹 toast（C.2 前端提交时的反馈方式）
    if (formRef.value) formRef.value.scrollToField(firstErrorKey)
    return
  }
  if (!dirtyKeys.value.length) {
    ElMessage.info('全局默认没有改动')
    return
  }
  const payload = dirtyKeys.value.reduce((acc, key) => {
    acc[key] = working[key]
    return acc
  }, {})
  const result = await run(() => saveGlobalConfig(payload))
  if (!result) return
  ElMessage.success('全局默认已保存')
  emit('saved')
}

function handleReset() {
  syncFromProps()
}
</script>

<template>
  <section class="global-form">
    <el-alert
      class="global-form__alert"
      type="info"
      :closable="false"
      show-icon
      title="这里的值是所有驿站的默认值；驿站若未单独覆盖，将自动继承此处的设置。"
    />

    <StateBlock v-if="error" variant="error" title="全局默认加载失败" @action="emit('retry')" />

    <StateBlock
      v-else-if="!loading && !items.length"
      variant="empty"
      title="暂无可配置项"
      description="请先在「配置项与选项集」中新增并启用配置项"
    />

    <template v-else>
      <el-alert v-if="errorTip" class="global-form__error" type="error" :closable="false" show-icon :title="errorTip" />

      <el-form ref="formRef" :model="working" label-width="140px" @submit.prevent>
        <el-form-item v-for="item in items" :key="item.itemKey" :prop="item.itemKey" :error="fieldErrors[item.itemKey]">
          <template #label>
            <span class="global-form__label">
              {{ item.name }}
              <span v-if="item.required" class="global-form__required">必填</span>
            </span>
          </template>
          <div class="global-form__row">
            <div class="global-form__field">
              <ConfigValueField
                v-model="working[item.itemKey]"
                :item="item"
                :option-set="optionSetOf ? optionSetOf(item.optionSetKey) : null"
                :loading="loading"
                @validate="(message) => setFieldError(item.itemKey, message)"
              />
              <p class="global-form__desc">
                {{ item.description || '—' }}
                <span class="global-form__scope">（{{ SCOPE_LABEL[item.scope] || item.scope }}）</span>
              </p>
              <button
                type="button"
                class="global-form__counts"
                :title="`跳转到「驿站覆盖」并定位到「${item.name}」`"
                @click="emit('locate', item.itemKey)"
              >
                当前被 {{ countOf(item.itemKey).inherited }} 个驿站继承 /
                {{ countOf(item.itemKey).overridden }} 个驿站覆盖
              </button>
            </div>
            <span class="global-form__badge">全局默认</span>
            <span class="visually-hidden">{{ item.name }} 全局默认值 {{ previewOf(item) }}</span>
          </div>
        </el-form-item>
      </el-form>

      <div class="global-form__footer">
        <el-button :disabled="submitting" @click="handleReset">恢复为上次保存</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSave">保存全局默认</el-button>
      </div>
    </template>
  </section>
</template>

<style scoped lang="scss">
.global-form {
  &__alert {
    margin-bottom: var(--sp-4);
  }

  &__error {
    margin-bottom: var(--sp-4);
  }

  &__label {
    display: inline-flex;
    align-items: center;
    gap: var(--sp-2);
  }

  &__required {
    padding: 0 var(--sp-1);
    border: 1px solid var(--state-warning-border);
    border-radius: var(--r-xs);
    background-color: var(--state-warning-bg);
    color: var(--state-warning-fg);
    font-size: var(--fs-micro);
    line-height: var(--lh-micro);
  }

  &__row {
    display: flex;
    align-items: flex-start;
    gap: var(--sp-3);
    width: 100%;
  }

  &__field {
    flex: 1;
    min-width: 0;
  }

  &__desc {
    margin: var(--sp-1) 0 0;
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    color: var(--text-3);
  }

  &__scope {
    color: var(--text-3);
  }

  &__counts {
    padding: 0;
    border: 0;
    background: none;
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    color: var(--color-primary-strong);
    text-decoration: underline;
    cursor: pointer;
  }

  &__badge {
    flex: none;
    padding: 2px var(--sp-2);
    border-radius: var(--r-xs);
    background-color: var(--state-primary-bg);
    color: var(--state-primary-fg);
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
  }

  &__footer {
    display: flex;
    justify-content: flex-end;
    gap: var(--sp-2);
    padding-top: var(--sp-3);
    border-top: 1px solid var(--border-line);
  }
}
</style>
