<script setup>
import { computed } from 'vue'

/**
 * 考勤导出确认弹窗（需求5，B5.2）
 *
 * 只回显范围与行数，不自己拼请求参数——导出必须由页面把「和屏幕上完全一样的筛选对象」传下去，
 * 历史上最常见的 bug 就是导出时重新拼参数，结果导出了全量而不是筛选结果（B5.2 核心设计原则）。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  // 只读回显的筛选摘要：[{ label, value }]
  filters: { type: Array, default: () => [] },
  total: { type: Number, default: 0 },
  exporting: { type: Boolean, default: false },
  error: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue', 'confirm'])

/** 空态（无可导出数据）与加载态互斥：没数据时不给点，点了也知道为什么 */
const empty = computed(() => props.total === 0)
const disabledReason = computed(() => (empty.value ? '当前筛选无数据可导出' : ''))

function close() {
  emit('update:modelValue', false)
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="导出考勤记录"
    width="460px"
    :close-on-click-modal="false"
    @update:model-value="close"
  >
    <div class="export-body">
      <p class="export-lead">导出范围（与当前筛选条件一致）</p>
      <el-descriptions :column="1" size="small" border>
        <el-descriptions-item v-for="item in filters" :key="item.label" :label="item.label">
          {{ item.value }}
        </el-descriptions-item>
      </el-descriptions>

      <p class="export-count" :class="{ 'is-empty': empty }">
        预计行数：{{ empty ? '0 条（无可导出数据）' : `约 ${total} 条` }}
      </p>
      <p class="export-tip">
        CSV（UTF-8 BOM，Excel 可直接打开）；固定 13 列，含 WiFi 与距离用于异常排查。数据量较大时生成可能需数秒。
      </p>
      <p v-if="error" class="export-error" role="alert">{{ error }}</p>
    </div>

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button
        type="primary"
        :loading="exporting"
        :disabled="empty"
        :title="disabledReason || '按当前筛选导出全部记录'"
        @click="emit('confirm')"
      >
        {{ exporting ? '导出中…' : '导出 CSV' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.export-body {
  .export-lead {
    margin: 0 0 var(--sp-2);
    font-size: var(--fs-caption);
    color: var(--text-3);
  }

  .export-count {
    margin: var(--sp-4) 0 0;
    font-size: var(--fs-body-strong);
    font-weight: var(--fw-medium);
    color: var(--text-1);
    font-variant-numeric: tabular-nums;

    &.is-empty {
      color: var(--text-3);
    }
  }

  .export-tip {
    margin: var(--sp-1) 0 0;
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    color: var(--text-3);
  }

  .export-error {
    margin: var(--sp-2) 0 0;
    font-size: var(--fs-caption);
    color: var(--state-danger-fg);
  }
}
</style>
