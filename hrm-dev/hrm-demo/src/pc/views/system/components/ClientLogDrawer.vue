<script setup>
import { computed, ref, watch } from 'vue'
import { DocumentCopy } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { CLIENT_LOG_SOURCE, SYNC_LOG_LEVEL, dictLabel } from '@/shared/constants/dict'
import StatusTag from '../../../components/StatusTag.vue'

/**
 * 运行日志详情抽屉（P7）
 *
 * 为什么 stack 要折叠：堆栈动辄上百行，默认全展开会把请求上下文挤到首屏之外，
 * 而排障时先看「哪条接口、什么状态码」再看堆栈。折叠阈值按 200 字（约 3 行）给，超过才给展开入口。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  // 列表行的 ClientLogVO（含 db 侧追加的 count / firstTime / lastTime）
  row: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue'])

/** 级别字典按 label 取值：SYNC_LOG_LEVEL 的键是 0/1/2，而日志 VO 的 level 是字符串 */
const LOG_LEVEL = Object.fromEntries(Object.values(SYNC_LOG_LEVEL).map((item) => [item.label, item]))
const STACK_PREVIEW = 200

const expanded = ref(false)

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) expanded.value = false
  }
)

const stack = computed(() => (props.row && props.row.stack) || '')
const needFold = computed(() => stack.value.length > STACK_PREVIEW)
const stackText = computed(() =>
  expanded.value || !needFold.value ? stack.value : `${stack.value.slice(0, STACK_PREVIEW)}…`
)
const requestText = computed(() => {
  const row = props.row
  if (!row || !row.method) return '—'
  return `${row.method} ${row.path || ''}`
})

async function handleCopy() {
  const row = props.row
  if (!row) return
  const text = [`[${row.time}] ${row.level} ${requestText.value}`, row.message, stack.value || ''].join('\n')
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制到剪贴板')
  } catch (e) {
    // 非 HTTPS / 旧内核下 clipboard 不可用：不静默失败，明确告诉用户复制失败
    ElMessage.warning('当前浏览器不支持自动复制，请手动选中文本复制')
  }
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    title="运行日志详情"
    :size="520"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-if="row" class="log-detail">
      <div class="log-detail__head">
        <StatusTag :dict="LOG_LEVEL" :value="row.level" />
        <span class="log-detail__time">{{ row.time }}</span>
        <el-button :icon="DocumentCopy" text type="primary" @click="handleCopy">复制</el-button>
      </div>

      <el-descriptions :column="2" size="small" border>
        <el-descriptions-item label="上报端">{{ dictLabel(CLIENT_LOG_SOURCE, row.source) }}</el-descriptions-item>
        <el-descriptions-item label="员工">{{
          row.employeeId == null ? '—' : `#${row.employeeId}`
        }}</el-descriptions-item>
        <el-descriptions-item label="路由" :span="2">{{ row.route || '—' }}</el-descriptions-item>
        <el-descriptions-item label="请求" :span="2">{{ requestText }}</el-descriptions-item>
        <el-descriptions-item label="HTTP 状态">{{ row.status == null ? '—' : row.status }}</el-descriptions-item>
        <el-descriptions-item label="业务码">{{ row.code == null ? '—' : row.code }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{
          row.duration == null ? '—' : `${row.duration} ms`
        }}</el-descriptions-item>
        <el-descriptions-item label="出现次数">{{ row.count || 1 }}</el-descriptions-item>
        <el-descriptions-item label="首次出现">{{ row.firstTime || row.time }}</el-descriptions-item>
        <el-descriptions-item label="末次出现">{{ row.lastTime || row.time }}</el-descriptions-item>
        <el-descriptions-item label="UA" :span="2">{{ row.ua || '—' }}</el-descriptions-item>
      </el-descriptions>

      <h3 class="log-detail__title">错误信息</h3>
      <pre class="log-detail__block">{{ row.message || '—' }}</pre>

      <h3 class="log-detail__title">
        堆栈
        <el-button v-if="needFold" link type="primary" @click="expanded = !expanded">
          {{ expanded ? '收起' : '展开全部' }}
        </el-button>
      </h3>
      <pre class="log-detail__block log-detail__block--stack">{{ stackText || '（无堆栈）' }}</pre>
    </div>
  </el-drawer>
</template>

<style scoped lang="scss">
.log-detail {
  &__head {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: var(--sp-2);
    margin-bottom: var(--sp-4);
  }

  &__time {
    flex: 1;
    font-size: var(--fs-caption);
    color: var(--text-2);
  }

  &__title {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
    margin: var(--sp-5) 0 var(--sp-2);
    font-size: var(--fs-h3);
    font-weight: var(--fw-semibold);
    color: var(--text-1);
  }

  /* 长文本块：等宽 + 自动换行，避免横向滚动条把堆栈读成断行 */
  &__block {
    max-height: 320px;
    margin: 0;
    padding: var(--sp-3);
    overflow: auto;
    border-radius: var(--r-sm);
    background-color: var(--surface-subtle);
    font-family: monospace;
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    color: var(--text-2);
    white-space: pre-wrap;
    word-break: break-all;

    &--stack {
      max-height: 420px;
    }
  }
}
</style>
