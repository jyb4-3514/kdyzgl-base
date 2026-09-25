<script setup>
import { ref, watch } from 'vue'

/**
 * 导出范围对话框（设计 B.7.1 / E.1 Molecule）
 * 三档范围：仅配置项定义 / 含全局默认（默认）/ 全部（含驿站覆盖）。
 * 导出中按钮 loading 且禁点，导出失败的就地提示（不关弹窗，允许改范围重试）。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  exporting: { type: Boolean, default: false },
  error: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue', 'confirm'])

const SCOPES = [
  { value: 'ITEMS', label: '仅配置项定义', description: '配置项 + 选项集，不含任何取值' },
  { value: 'ITEMS_GLOBAL', label: '含全局默认', description: '上一项 + 全局默认值（推荐）' },
  { value: 'ALL', label: '全部（含驿站覆盖）', description: '上一项 + 每个驿站的覆盖值，可改完再导回' }
]

const scope = ref('ITEMS_GLOBAL')

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) scope.value = 'ITEMS_GLOBAL'
  }
)
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="导出同步配置"
    width="520px"
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-alert v-if="error" class="export-error" type="error" :closable="false" show-icon :title="error" />

    <el-radio-group v-model="scope" class="export-scope">
      <el-radio v-for="item in SCOPES" :key="item.value" :value="item.value" class="export-scope__item">
        <span class="export-scope__label">{{ item.label }}</span>
        <span class="export-scope__desc">{{ item.description }}</span>
      </el-radio>
    </el-radio-group>

    <p class="export-tip">导出为 CSV（UTF-8 带 BOM），Excel 可直接打开；文件含中文表头，改完可原样导回。</p>

    <template #footer>
      <el-button :disabled="exporting" @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="exporting" @click="emit('confirm', scope)">导出</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.export-error {
  margin-bottom: var(--sp-4);
}

.export-scope {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);

  &__item {
    height: auto;
    align-items: flex-start;
    margin-right: 0;
  }

  &__label {
    color: var(--text-1);
  }

  &__desc {
    margin-left: var(--sp-2);
    font-size: var(--fs-caption);
    color: var(--text-3);
  }
}

.export-tip {
  margin: var(--sp-4) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}
</style>
