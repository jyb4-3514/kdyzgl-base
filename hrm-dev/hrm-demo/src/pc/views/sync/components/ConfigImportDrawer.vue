<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { parseCsv } from '@/shared/domain/csv.js'
import { importSyncConfig } from '../../../api/syncConfigCenter.js'
import { SYNC_CONFIG_CSV_HEADER } from '../utils/configCenter.js'
import { useSilentSubmit } from '../composables/useSilentSubmit.js'
import { downloadCsv } from '../../../utils/csv.js'
import StateBlock from '../../../components/StateBlock.vue'
import StatusTag from '../../../components/StatusTag.vue'

/**
 * 导入配置四步向导（设计 B.7.2 / E.1 Organism）
 * ① 上传 → ② 解析预览（dryRun=true）→ ③ 冲突策略 → ④ 确认导入（dryRun=false）→ 结果报告
 * 预览与落库共用服务端同一套解析（dryRun 语义），前端不用自写一份「猜测式」校验；
 * 本地只多解析一次原文，用于补出预览表里契约未返回的「配置值」列。
 * 步骤条 4 段与 step 一一对应，落库成功后进入 finished 结果报告（不再占步骤位）。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'template', 'imported'])

const { submitting, errorTip, run, clearError } = useSilentSubmit()

const STRATEGIES = [
  { value: 'OVERWRITE', label: '覆盖', description: '同键的已存在记录用导入值替换（含显示名 / 附加属性 / 启用）' },
  { value: 'SKIP', label: '跳过', description: '同键已存在则保留原值，适合增量补充' },
  { value: 'APPEND', label: '追加', description: '仅新增不存在的键；已存在则报冲突，不静默修改' }
]

const step = ref(1)
const finished = ref(false)
const fileName = ref('')
const fileError = ref('')
const content = ref('')
const parsedRows = ref([])
const onConflict = ref('OVERWRITE')
const preview = ref(null)
const result = ref(null)
const expandedFailed = ref(false)

/** 重置整个向导（关闭或重新开始时调用，避免上一轮状态残留） */
function reset() {
  step.value = 1
  finished.value = false
  fileName.value = ''
  fileError.value = ''
  content.value = ''
  parsedRows.value = []
  onConflict.value = 'OVERWRITE'
  preview.value = null
  result.value = null
  expandedFailed.value = false
  clearError()
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) reset()
  }
)

const failedRows = computed(() => (preview.value ? preview.value.rows.filter((row) => row.level === 'FAILED') : []))
const plan = computed(() => (preview.value ? preview.value.plan : { create: 0, update: 0, skip: 0, conflict: 0 }))
/** 预览有数据行才允许进入策略步（纯表头文件属空态） */
const hasData = computed(() => !!preview.value && preview.value.summary.total > 0)
/** 追加策略语义就是「不改已有数据」，存在冲突时禁止继续（B.7.2 ③） */
const appendBlocked = computed(() => onConflict.value === 'APPEND' && plan.value.conflict > 0)

/** 预览表「配置值」列：契约 rows 未返回取值，本地按行号从原文补出（同一解析器，口径一致） */
function valueOfRow(row) {
  const fields = parsedRows.value[row.rowNo - 1]
  if (!fields) return '—'
  const value = fields[14]
  return value == null || value === '' ? '—' : String(value)
}

function rowClassOf({ row }) {
  if (row.level === 'FAILED') return 'is-failed'
  if (row.level === 'WARNING') return 'is-warning'
  return 'is-ok'
}

/** 级别标签字典：文字 + 颜色双通道（设计 B.7 明细需「行号 + 级别 + 原因」，不能只靠颜色） */
const LEVEL_DICT = {
  OK: { label: '通过', type: 'success' },
  WARNING: { label: '警告', type: 'warning' },
  FAILED: { label: '失败', type: 'danger' }
}

/** 原因列只放「为什么」：结论已由「级别」列承载，避免「通过 ：通过」这类前缀重复 */
function reasonTextOf(row) {
  const message = row.message || ''
  if (row.level === 'OK' && message === '通过') return '—'
  return message || '—'
}

/** 读文件：扩展名 / 空文件就地拦下（B.7.2 ①），通过后停在第一步由用户点「解析预览」 */
async function applyFile(raw) {
  fileError.value = ''
  clearError()
  content.value = ''
  parsedRows.value = []
  preview.value = null
  if (!raw) return
  fileName.value = raw.name
  if (!/\.csv$/i.test(raw.name)) {
    fileError.value = '仅支持 .csv 文件，请使用「下载导入模板」生成的文件'
    return
  }
  try {
    const text = await raw.text()
    if (!String(text).trim()) {
      fileError.value = '文件中没有可导入的数据行'
      return
    }
    content.value = text
    parsedRows.value = parseCsv(text)
  } catch (e) {
    fileError.value = '文件读取失败，请重新选择'
  }
}

function handleFileChange(uploadFile) {
  if (uploadFile) applyFile(uploadFile.raw)
}

/**
 * limit=1 且隐藏文件列表时会走 on-exceed：直接把本次选择当作替换。
 * 否则用户选错一次就再也换不了文件（列表不可见、没有移除入口），等于卡死在第一步。
 */
function handleExceed(files) {
  if (files && files.length) applyFile(files[0])
}

async function runDryRun(strategy = onConflict.value) {
  return run(() => importSyncConfig({ content: content.value, onConflict: strategy, dryRun: true }))
}

/** ① → ②：解析整体失败（5001 / 5002 / 9508）时停在第一步，文案由顶部 alert 原样展示 */
async function goPreview() {
  if (fileError.value) return
  if (!content.value) {
    fileError.value = '请先选择要导入的 .csv 文件'
    return
  }
  const data = await runDryRun()
  if (!data) return
  preview.value = data
  step.value = 2
}

async function changeStrategy(value) {
  onConflict.value = value
  // 冲突计数随策略变化，必须重跑一次预览，保证「策略区预览」与「即将执行的动作」一致
  const data = await runDryRun(value)
  if (data) preview.value = data
}

async function confirmImport() {
  if (appendBlocked.value) return
  const p = plan.value
  try {
    await ElMessageBox.confirm(
      `将新增 ${p.create} 条、更新 ${p.update} 条、跳过 ${p.skip} 条（失败行 ${failedRows.value.length} 条），导入后立即生效。此操作会修改配置项定义与驿站覆盖值。`,
      '导入同步配置',
      { confirmButtonText: '确认导入', cancelButtonText: '再想想', type: 'warning' }
    )
  } catch (e) {
    return
  }
  const data = await run(() =>
    importSyncConfig({ content: content.value, onConflict: onConflict.value, dryRun: false })
  )
  if (!data) return
  result.value = data
  finished.value = true
  const applied = data.applied || { created: 0, updated: 0, skipped: 0 }
  ElMessage.success(`已导入：新增 ${applied.created} 条，更新 ${applied.updated} 条，跳过 ${applied.skipped} 条`)
  emit('imported')
}

/** 下载失败明细：仅含失败行原文（16 列，可直接修正后再导入）；行号取自服务端结论
 * TODO(扩展): 行号→原文按物理行切分，若某失败行位于「含换行的引号单元格」之后，行号会错位；
 *   待契约在 preview rows 里直接回带原始字段后改为按契约还原
 */
function downloadFailed() {
  const rows = failedRows.value.map((row) => parsedRows.value[row.rowNo - 1]).filter(Boolean)
  if (!rows.length) {
    ElMessage.info('没有可下载的失败明细')
    return
  }
  const day = new Date()
  const stamp = `${day.getFullYear()}${String(day.getMonth() + 1).padStart(2, '0')}${String(day.getDate()).padStart(2, '0')}`
  downloadCsv(`同步配置_导入失败明细_${stamp}.csv`, SYNC_CONFIG_CSV_HEADER, rows)
}

/** 结果报告里的失败行（落库后才有的最终结论） */
const resultFailed = computed(() => (result.value ? result.value.rows.filter((row) => row.level === 'FAILED') : []))
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    title="导入配置"
    size="min(var(--drawer-w-lg), 92vw)"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="import-drawer">
      <el-steps :active="finished ? 4 : step - 1" simple finish-status="success" class="import-drawer__steps">
        <el-step title="上传文件" />
        <el-step title="解析预览" />
        <el-step title="冲突策略" />
        <el-step title="确认导入" />
      </el-steps>
      <p class="visually-hidden" aria-live="polite">
        {{ finished ? '导入已完成，显示结果报告' : `当前步骤 ${step} / 4` }}
      </p>

      <el-alert
        v-if="errorTip"
        class="import-drawer__error"
        type="error"
        :closable="false"
        show-icon
        :title="errorTip"
      />

      <!-- ① 上传 -->
      <template v-if="step === 1 && !finished">
        <el-upload
          drag
          accept=".csv"
          :limit="1"
          :auto-upload="false"
          :show-file-list="false"
          :on-change="handleFileChange"
          :on-exceed="handleExceed"
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">将 .csv 文件拖到此处，或<em>点击上传</em></div>
          <template #tip>
            <div class="el-upload__tip">
              仅支持 .csv（UTF-8，可用「下载导入模板」生成），数据行不超过 1000 行；再次选择会替换当前文件
            </div>
          </template>
        </el-upload>

        <p v-if="fileName" class="import-drawer__file">已选择：{{ fileName }}</p>
        <p v-if="fileError" class="import-drawer__file-error" role="alert">{{ fileError }}</p>
        <p v-else-if="!content" class="import-drawer__file-hint">尚未选择文件，选择后点「解析预览」查看逐行校验结果</p>

        <el-button link type="primary" class="import-drawer__tpl" @click="emit('template')">下载导入模板</el-button>
      </template>

      <!-- ② 解析预览 -->
      <template v-else-if="step === 2 && !finished">
        <p class="import-drawer__summary" aria-live="polite">
          成功解析 {{ preview.summary.ok }} 条 · 失败 {{ preview.summary.failed }} 条 · 警告
          {{ preview.summary.warning }} 条 · 共 {{ preview.summary.total }} 行
        </p>

        <StateBlock
          v-if="!hasData"
          variant="empty"
          title="文件中没有可导入的数据行"
          description="请在下方的 CSV 中补齐数据行后重新选择文件"
        />

        <el-table
          v-else
          class="import-drawer__table"
          :data="preview.rows"
          border
          max-height="360"
          :row-class-name="rowClassOf"
        >
          <el-table-column prop="rowNo" label="行号" width="70" align="center" />
          <el-table-column prop="type" label="记录类型" width="100" />
          <el-table-column prop="itemKey" label="配置项Key" min-width="140" show-overflow-tooltip />
          <el-table-column prop="optionKey" label="选项Key" min-width="120" show-overflow-tooltip />
          <el-table-column prop="stationName" label="驿站" min-width="110" show-overflow-tooltip />
          <el-table-column label="配置值" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ valueOfRow(row) }}</template>
          </el-table-column>
          <el-table-column label="级别" width="90" align="center">
            <template #default="{ row }">
              <StatusTag :dict="LEVEL_DICT" :value="row.level" />
            </template>
          </el-table-column>
          <el-table-column label="原因" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="import-drawer__reason" :class="`is-${row.level.toLowerCase()}`">{{
                reasonTextOf(row)
              }}</span>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <!-- ③ 冲突策略 -->
      <template v-else-if="step === 3 && !finished">
        <el-radio-group v-model="onConflict" class="import-drawer__strategy" @change="changeStrategy">
          <el-radio
            v-for="item in STRATEGIES"
            :key="item.value"
            :value="item.value"
            class="import-drawer__strategy-item"
          >
            <span class="import-drawer__strategy-label">{{ item.label }}</span>
            <span class="import-drawer__strategy-desc">{{ item.description }}</span>
          </el-radio>
        </el-radio-group>

        <el-alert
          class="import-drawer__plan"
          :type="appendBlocked ? 'warning' : 'info'"
          :closable="false"
          show-icon
          :title="`按当前策略，将新增 ${plan.create} 条、更新 ${plan.update} 条、跳过 ${plan.skip} 条、冲突 ${plan.conflict} 条`"
          :description="appendBlocked ? '「追加」策略不允许覆盖已存在记录，请先修正冲突行或改用「覆盖 / 跳过」。' : ''"
        />
      </template>

      <!-- ④ 确认导入 -->
      <template v-else-if="!finished">
        <el-alert
          class="import-drawer__confirm"
          type="info"
          :closable="false"
          show-icon
          title="请确认本次导入范围"
          description="点击「确认导入」后会再次弹出确认框；确认后立即修改配置项定义与驿站覆盖值，此操作不可撤销。"
        />
        <ul class="import-drawer__confirm-list">
          <li>新增 {{ plan.create }} 条</li>
          <li>更新 {{ plan.update }} 条</li>
          <li>跳过 {{ plan.skip }} 条</li>
          <li>失败（将被跳过）{{ failedRows.length }} 条</li>
        </ul>
      </template>

      <!-- 结果报告 -->
      <template v-else>
        <el-alert
          class="import-drawer__result"
          type="success"
          :closable="false"
          show-icon
          :title="`已导入：新增 ${result && result.applied ? result.applied.created : 0} 条，更新 ${result && result.applied ? result.applied.updated : 0} 条，跳过 ${result && result.applied ? result.applied.skipped : 0} 条`"
        />
        <p class="import-drawer__summary" aria-live="polite">
          共处理 {{ result ? result.summary.total : 0 }} 行 · 失败 {{ resultFailed.length }} 行
        </p>

        <template v-if="resultFailed.length">
          <ul class="import-drawer__failed">
            <li v-for="row in expandedFailed ? resultFailed : resultFailed.slice(0, 5)" :key="row.rowNo">
              第 {{ row.rowNo }} 行：{{ row.message }}
            </li>
          </ul>
          <el-button v-if="resultFailed.length > 5" link type="primary" @click="expandedFailed = !expandedFailed">
            {{ expandedFailed ? '收起' : `查看全部 ${resultFailed.length} 条失败行` }}
          </el-button>
          <el-button link type="primary" @click="downloadFailed">下载失败明细</el-button>
        </template>
      </template>
    </div>

    <template #footer>
      <div class="import-drawer__footer">
        <p v-if="!finished && step === 2 && failedRows.length" class="import-drawer__footer-tip">
          失败行将被跳过，不影响成功行导入
        </p>
        <p v-else-if="!finished && step === 3 && appendBlocked" class="import-drawer__footer-tip">
          「追加」策略不允许覆盖已存在记录，请先修正冲突行或改用「覆盖 / 跳过」
        </p>

        <template v-if="finished">
          <el-button type="primary" @click="emit('update:modelValue', false)">完成</el-button>
        </template>
        <template v-else-if="step === 1">
          <el-button @click="emit('update:modelValue', false)">取消</el-button>
          <el-button type="primary" :loading="submitting" :disabled="!content || !!fileError" @click="goPreview"
            >解析预览</el-button
          >
        </template>
        <template v-else-if="step === 2">
          <el-button @click="step = 1">上一步</el-button>
          <el-button :type="failedRows.length ? 'default' : 'primary'" :disabled="!hasData" @click="step = 3">
            下一步{{ failedRows.length ? `（${failedRows.length} 行失败将被跳过）` : '' }}
          </el-button>
        </template>
        <template v-else-if="step === 3">
          <el-button @click="step = 2">上一步</el-button>
          <el-button type="primary" :disabled="appendBlocked" @click="step = 4">下一步</el-button>
        </template>
        <template v-else>
          <el-button @click="step = 3">上一步</el-button>
          <el-button type="primary" :loading="submitting" @click="confirmImport">确认导入</el-button>
        </template>
      </div>
    </template>
  </el-drawer>
</template>

<style scoped lang="scss">
.import-drawer {
  &__steps {
    margin-bottom: var(--sp-4);
  }

  &__error {
    margin-bottom: var(--sp-4);
  }

  &__file {
    margin: var(--sp-4) 0 0;
    font-size: var(--fs-body);
    color: var(--text-1);
  }

  &__file-hint {
    margin: var(--sp-4) 0 0;
    font-size: var(--fs-caption);
    color: var(--text-3);
  }

  &__file-error {
    margin: var(--sp-2) 0 0;
    font-size: var(--fs-caption);
    color: var(--state-danger-fg);
  }

  &__tpl {
    margin-top: var(--sp-2);
  }

  &__summary {
    margin: 0 0 var(--sp-3);
    font-size: var(--fs-body);
    color: var(--text-2);
    font-variant-numeric: tabular-nums;
  }

  &__table {
    width: 100%;
  }

  // 原因文字颜色随级别，与「级别」标签形成冗余通道，不只靠颜色区分
  &__reason {
    &.is-failed {
      color: var(--state-danger-fg);
    }

    &.is-warning {
      color: var(--state-warning-fg);
    }
  }

  &__strategy {
    display: flex;
    flex-direction: column;
    gap: var(--sp-3);

    &-item {
      height: auto;
      align-items: flex-start;
      margin-right: 0;
    }

    &-label {
      color: var(--text-1);
    }

    &-desc {
      margin-left: var(--sp-2);
      font-size: var(--fs-caption);
      color: var(--text-3);
    }
  }

  &__plan {
    margin-top: var(--sp-4);
  }

  &__confirm {
    margin-bottom: var(--sp-3);
  }

  &__confirm-list {
    margin: 0;
    padding-left: var(--sp-6);
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    color: var(--text-2);
    font-variant-numeric: tabular-nums;
  }

  &__result {
    margin-bottom: var(--sp-3);
  }

  &__failed {
    margin: 0 0 var(--sp-3);
    padding: var(--sp-3) var(--sp-4) var(--sp-3) var(--sp-6);
    list-style: disc;
    background-color: var(--state-danger-bg);
    border: 1px solid var(--state-danger-border);
    border-radius: var(--r-sm);
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    color: var(--state-danger-fg);
  }

  &__footer {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: var(--sp-2);
  }

  // 按钮旁的说明：占满左侧剩余空间，让按钮始终右对齐
  &__footer-tip {
    margin: 0 auto 0 0;
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    color: var(--text-3);
  }

  // 失败行整行浅底 + 文字原因双通道（不只靠颜色）
  :deep(.el-table__row.is-failed) {
    background-color: var(--state-danger-bg);
  }

  :deep(.el-table__row.is-warning) {
    background-color: var(--state-warning-bg);
  }
}
</style>
