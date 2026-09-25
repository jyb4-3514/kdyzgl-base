<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DEMO_CODE } from '@kdyzgl/shared/constants/errorCode'
import { COLLECT_FREQUENCY, dictLabel } from '@kdyzgl/shared/constants/dict'
import { getSyncConfig, saveSyncConfig } from '../../../api/syncConfig.js'
import { CLOCK_END_OPTIONS, CLOCK_OPTIONS, valueTextOf, validateValue } from '../utils/configCenter.js'
import { useSyncConfigMeta } from '../composables/useSyncConfigMeta.js'
import { useSilentSubmit } from '../composables/useSilentSubmit.js'
import StateBlock from '../../../components/StateBlock.vue'
import ConfigValueField from './ConfigValueField.vue'
import SourceBadge from './SourceBadge.vue'

/**
 * 改造要点：数据源 / 采集频率 / 采集时段模板原先硬编码（前者自由文本，后两者取本地字典），
 * 现在统一从 GET /sync/config-items 的选项集渲染下拉——「配置管理里新增一个数据源 / 频率档位，
 * 回到本抽屉立即可选」这条链路靠的就是这里读同一份元数据。
 *
 * 读写口径（A.4 新模型）：读优先用 values（itemKey → 生效值），展示来源用 sources；
 * 写用 overrides / resetKeys，不再走旧字段 frequency / dataSource（旧码兼容由服务端承担）。
 *
 * 打开即拉单站配置：未配置（6002）进「立即配置」引导，其它失败才进错误态（B1.5）。
 * TODO(扩展): 下方「只读回退视图」是为「站长视角 + 元数据未就绪」保留的兼容分支；
 *   若后续给站长开放只读元数据（或采集配置改动收敛为仅 ADMIN 可打开），可删除该分支与其本地字典依赖
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  stationId: { type: [Number, String], default: null },
  stationName: { type: String, default: '' },
  canWrite: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'saved'])

const meta = useSyncConfigMeta()
const { submitting, errorTip, run, clearError } = useSilentSubmit()

const formRef = ref(null)
const loading = ref(false)
const loadError = ref(false)
const missing = ref(false)
const detail = ref(null)
const readonly = ref(false)
const fieldErrors = reactive({})

const form = reactive({ enabled: true, values: {}, template: '', collectStartTime: '08:00', collectEndTime: '20:00' })
const sources = ref({})

/**
 * 动态渲染的配置项：排除「采集时段模板」——它在本抽屉里的作用是「选模板自动填起止」，
 * 起止时间仍由用户逐格调整并可保存（E.2 ③），不随 overrides 一起提交，避免模板与起止两套口径打架。
 */
const dynamicItems = computed(() => meta.enabledItems().filter((item) => item.itemKey !== 'time_template'))

/** 何时用动态表单：ADMIN 且元数据已就绪；否则退化为只读的旧字段视图（站长/元数据异常时） */
const useDynamic = computed(() => props.canWrite && meta.items.value.length > 0)

const timeTemplates = computed(() => meta.optionsOf('time_template'))

/** 数据源为必填时，前端即时校验沿用既有文案「启用采集前须先选择数据源」（C.3 口径一致） */
function messageOf(item, value) {
  if (item.itemKey === 'data_source' && form.enabled && (value == null || String(value).trim() === '')) {
    return '启用采集前须先选择数据源'
  }
  return validateValue(item, value, meta.getOptionSet(item.optionSetKey))
}

/** 值控件的即时校验结论（失焦/变更）落到字段红字位；提交时再按 messageOf 整体校验一次兜底 */
function setFieldError(key, message) {
  if (message) fieldErrors[key] = message
  else delete fieldErrors[key]
}

function fill(vo) {
  detail.value = vo
  sources.value = vo.sources || {}
  form.enabled = vo.enabled
  form.collectStartTime = vo.collectStartTime || '08:00'
  form.collectEndTime = vo.collectEndTime || '20:00'
  Object.keys(form.values).forEach((key) => delete form.values[key])
  // 生效值来自 values（含全局默认回退），缺失时才退回旧字段
  meta.enabledItems().forEach((item) => {
    form.values[item.itemKey] = vo.values ? vo.values[item.itemKey] : null
  })
  form.template = (vo.values && vo.values.time_template) || ''
  readonly.value = vo.status === 0
  Object.keys(fieldErrors).forEach((key) => delete fieldErrors[key])
  clearError()
}

async function load() {
  loading.value = true
  loadError.value = false
  missing.value = false
  try {
    // 元数据与单站配置同时拉：选项集不到位时动态控件渲染不出候选项
    if (props.canWrite) await meta.loadMeta()
    fill(await getSyncConfig(props.stationId))
  } catch (e) {
    detail.value = null
    if (e && e.code === DEMO_CODE.SYNC_CONFIG_NOT_EXISTS) missing.value = true
    else loadError.value = true
  } finally {
    loading.value = false
  }
}

/** 未配置 → 直接给一份可编辑的默认表单，不要求用户先「新建」再「编辑」 */
function startCreate() {
  missing.value = false
  fill({ enabled: false, values: {}, sources: {}, collectStartTime: '08:00', collectEndTime: '20:00', status: 1 })
}

/** 选择时段模板 → 自动填充起止（起止仍可逐格调整） */
function handleTemplateChange(optionKey) {
  const option = timeTemplates.value.find((row) => row.optionKey === optionKey)
  if (!option || !option.extraAttrs) return
  form.collectStartTime = option.extraAttrs.startTime
  form.collectEndTime = option.extraAttrs.endTime
}

async function handleSwitchOff() {
  try {
    await ElMessageBox.confirm('关闭后该驿站将停止包裹采集，在途批次不受影响。', '关闭采集', {
      confirmButtonText: '确认关闭',
      cancelButtonText: '再想想',
      type: 'warning'
    })
    return true
  } catch (e) {
    form.enabled = true
    return false
  }
}

async function handleSubmit() {
  Object.keys(fieldErrors).forEach((key) => delete fieldErrors[key])
  const overrides = {}
  const resetKeys = []
  let firstErrorKey = ''
  dynamicItems.value.forEach((item) => {
    const value = form.values[item.itemKey]
    const blank = value == null || (typeof value === 'string' && value.trim() === '')
    if (blank) {
      // 关闭采集时允许留空 → 视为恢复继承；开启采集时必填项留空则就地报错
      if (form.enabled) {
        const message = messageOf(item, value)
        if (message) {
          fieldErrors[item.itemKey] = message
          if (!firstErrorKey) firstErrorKey = item.itemKey
        }
      } else if (sources.value[item.itemKey] === 'OVERRIDE') {
        resetKeys.push(item.itemKey)
      }
      return
    }
    const message = messageOf(item, value)
    if (message) {
      fieldErrors[item.itemKey] = message
      if (!firstErrorKey) firstErrorKey = item.itemKey
      return
    }
    overrides[item.itemKey] = value
  })
  if (firstErrorKey) {
    if (formRef.value) formRef.value.scrollToField(firstErrorKey)
    return
  }
  if (!form.enabled && detail.value && detail.value.enabled) {
    const confirmed = await handleSwitchOff()
    if (!confirmed) return
  }
  const payload = {
    enabled: form.enabled ? 1 : 0,
    collectStartTime: form.collectStartTime,
    collectEndTime: form.collectEndTime,
    overrides,
    resetKeys
  }
  const vo = await run(() => saveSyncConfig(props.stationId, payload))
  if (!vo) return
  fill(vo)
  ElMessage.success('采集配置已保存，下次采集周期生效')
  emit('saved', vo)
}

function close() {
  emit('update:modelValue', false)
}

/** 只读回退视图用的展示值（站长 / 元数据未就绪时） */
const legacyFrequency = computed(() => dictLabel(COLLECT_FREQUENCY, detail.value ? detail.value.frequency : '', '—'))

watch(
  () => [props.modelValue, props.stationId],
  ([visible]) => {
    if (!visible) return
    load()
  }
)
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    :title="`采集配置 · ${stationName || ''}`"
    size="min(var(--drawer-w), 92vw)"
    destroy-on-close
    @update:model-value="close"
  >
    <div v-loading="loading" class="drawer-body">
      <StateBlock v-if="loadError" variant="error" title="采集配置加载失败" @action="load" />

      <StateBlock
        v-else-if="missing"
        variant="empty"
        title="该驿站尚未配置采集"
        description="选择数据源与采集时段后即可开启自动采集"
        action-text="立即配置"
        @action="startCreate"
      />

      <template v-else-if="detail || !loading">
        <el-alert v-if="errorTip" class="drawer-error" type="error" :closable="false" show-icon :title="errorTip" />

        <!-- 动态表单：控件与候选项全部来自配置中心（配置管理里改数据，这里立即生效） -->
        <el-form
          v-if="useDynamic"
          ref="formRef"
          :model="form"
          label-width="100px"
          :disabled="readonly || !canWrite"
          @submit.prevent
        >
          <el-form-item label="采集开关">
            <el-switch v-model="form.enabled" :aria-label="`${stationName}采集开关`" />
          </el-form-item>

          <el-form-item
            v-for="item in dynamicItems"
            :key="item.itemKey"
            :prop="item.itemKey"
            :error="fieldErrors[item.itemKey]"
          >
            <template #label>
              <span class="drawer-label">
                {{ item.name }}
                <span v-if="item.required" class="drawer-label__required" aria-hidden="true">*</span>
              </span>
            </template>
            <div class="drawer-dynamic">
              <ConfigValueField
                v-model="form.values[item.itemKey]"
                :item="item"
                :option-set="meta.getOptionSet(item.optionSetKey)"
                :aria-label="item.name"
                @validate="(message) => setFieldError(item.itemKey, message)"
              />
              <SourceBadge :source="sources[item.itemKey] || 'INHERIT'" />
            </div>
          </el-form-item>

          <el-form-item label="采集时段模板">
            <div class="drawer-dynamic">
              <el-select
                v-model="form.template"
                clearable
                class="drawer-field"
                placeholder="选择模板自动填充起止"
                @change="handleTemplateChange"
              >
                <el-option
                  v-for="option in timeTemplates"
                  :key="option.optionKey"
                  :label="option.label"
                  :value="option.optionKey"
                />
              </el-select>
              <SourceBadge :source="sources.time_template || 'INHERIT'" />
            </div>
          </el-form-item>

          <el-form-item label="采集时段">
            <!-- 时段自建选项以覆盖 24:00 收班档（store.isEndClock 口径） -->
            <div class="time-range">
              <el-select v-model="form.collectStartTime" class="time-range__item" aria-label="采集开始时间">
                <el-option v-for="time in CLOCK_OPTIONS" :key="time" :label="time" :value="time" />
              </el-select>
              <span class="time-range__sep">至</span>
              <el-select v-model="form.collectEndTime" class="time-range__item" aria-label="采集结束时间">
                <el-option v-for="time in CLOCK_END_OPTIONS" :key="time" :label="time" :value="time" />
              </el-select>
            </div>
          </el-form-item>
        </el-form>

        <!-- 只读回退：站长视角或元数据未就绪时按旧字段展示，不渲染不可用按钮 -->
        <el-form v-else label-width="100px" disabled>
          <el-form-item label="采集开关">
            <el-switch :model-value="form.enabled" :aria-label="`${stationName}采集开关`" />
          </el-form-item>
          <el-form-item label="数据源">
            <el-input :model-value="detail ? detail.dataSource || '未配置' : ''" />
          </el-form-item>
          <el-form-item label="采集频次">
            <el-input :model-value="legacyFrequency" />
          </el-form-item>
          <el-form-item label="采集时段">
            <el-input :model-value="`${form.collectStartTime} 至 ${form.collectEndTime}`" />
          </el-form-item>
        </el-form>

        <el-descriptions v-if="detail" :column="1" size="small" border class="drawer-readonly">
          <el-descriptions-item label="最后采集">{{ detail.lastCollectTime || '从未采集' }}</el-descriptions-item>
          <el-descriptions-item label="最近批次">
            {{
              detail.lastBatch ? `${detail.lastBatch.batchNo} · 包裹 ${detail.lastBatch.parcelTotal} 件` : '暂无批次'
            }}
          </el-descriptions-item>
          <el-descriptions-item v-if="useDynamic" label="生效值预览">
            {{
              dynamicItems
                .map(
                  (item) =>
                    `${item.name} ${valueTextOf(item, form.values[item.itemKey], meta.getOptionSet(item.optionSetKey))}`
                )
                .join(' · ')
            }}
          </el-descriptions-item>
        </el-descriptions>

        <p v-if="!canWrite" class="drawer-tip">只读视角：采集配置仅超级管理员可修改。</p>
        <p v-else-if="readonly" class="drawer-tip">该驿站已停用，配置只读；如需采集请先在驿站管理中启用该驿站。</p>
      </template>
    </div>

    <template #footer>
      <div class="drawer-footer">
        <el-button @click="close">关闭</el-button>
        <el-button
          v-if="canWrite && useDynamic && !readonly && !missing"
          type="primary"
          :loading="submitting"
          @click="handleSubmit"
        >
          保存配置
        </el-button>
      </div>
    </template>
  </el-drawer>
</template>

<style scoped lang="scss">
.drawer-body {
  min-height: 200px;
}

.drawer-error {
  margin-bottom: var(--sp-4);
}

.drawer-field {
  width: 100%;
}

.drawer-dynamic {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  width: 100%;

  > :first-child {
    flex: 1;
    min-width: 0;
  }
}

.drawer-label {
  &__required {
    margin-left: 2px;
    color: var(--state-danger-fg);
  }
}

.time-range {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  width: 100%;

  &__item {
    flex: 1;
  }

  &__sep {
    color: var(--text-3);
    font-size: var(--fs-caption);
  }
}

.drawer-readonly {
  margin-top: var(--sp-5);
}

.drawer-tip {
  margin: var(--sp-3) 0 0;
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
