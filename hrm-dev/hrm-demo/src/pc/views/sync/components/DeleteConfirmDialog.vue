<script setup>
import { ref, watch } from 'vue'

/**
 * 删除影响面确认框（设计 B.6 / C.4）
 * 为什么不用 ElMessageBox：正文需要按「受影响驿站数量」展开完整列表（>3 时折叠），
 * 且「全局默认引用」场景的主按钮要改为「改为停用」，MessageBox 表达不了可切换的主操作。
 * 文案规范沿用 B0.3：标题动词短语、正文说清影响与不可逆、按钮用具体动词。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '删除' },
  message: { type: String, default: '' },
  stations: { type: Array, default: () => [] },
  confirmText: { type: String, default: '确认删除' },
  cancelText: { type: String, default: '再想想' },
  // 替代主操作（如「改为停用」）；传空则不渲染
  altText: { type: String, default: '' },
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'confirm', 'alt'])

const expanded = ref(false)
// 超过 3 个才需要展开入口：3 个以内正文已列全
const expandable = () => props.stations.length > 3

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) expanded.value = false
  }
)
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="520px"
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
  >
    <p class="impact__message">{{ message }}</p>

    <template v-if="stations.length">
      <el-button v-if="expandable()" link type="primary" class="impact__toggle" @click="expanded = !expanded">
        {{ expanded ? '收起受影响驿站' : '查看受影响驿站' }}
      </el-button>
      <ul v-if="expanded || !expandable()" class="impact__list">
        <li v-for="station in stations" :key="station.stationId">{{ station.stationName }}</li>
      </ul>
    </template>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">{{ cancelText }}</el-button>
      <el-button v-if="altText" type="primary" :loading="loading" @click="emit('alt')">{{ altText }}</el-button>
      <el-button v-else type="danger" :loading="loading" @click="emit('confirm')">{{ confirmText }}</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.impact {
  &__message {
    margin: 0;
    font-size: var(--fs-body);
    line-height: var(--lh-body);
    color: var(--text-1);
  }

  &__toggle {
    margin-top: var(--sp-2);
  }

  &__list {
    margin: var(--sp-2) 0 0;
    padding: var(--sp-3) var(--sp-4) var(--sp-3) var(--sp-6);
    max-height: 180px;
    overflow: auto;
    list-style: disc;
    background-color: var(--surface-sub);
    border: 1px solid var(--border-line);
    border-radius: var(--r-sm);
    font-size: var(--fs-caption);
    color: var(--text-2);
  }
}
</style>
