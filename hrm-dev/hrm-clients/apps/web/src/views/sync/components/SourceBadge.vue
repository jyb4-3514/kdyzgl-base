<script setup>
import { computed } from 'vue'
import StatusTag from '../../../components/StatusTag.vue'

/**
 * 继承 / 覆盖徽标（设计 E.1 Atom / B.5）
 * 复用 StatusTag：继承 = outline 中性描边，已覆盖 = soft 主色浅底。
 * 语义由文字承载（不只靠颜色），title 补充完整含义。
 */
const props = defineProps({
  source: { type: String, default: 'INHERIT' } // INHERIT | OVERRIDE
})

const dict = computed(() =>
  props.source === 'OVERRIDE'
    ? { OVERRIDE: { label: '已覆盖', type: 'primary' } }
    : { INHERIT: { label: '继承', type: 'info' } }
)

const title = computed(() =>
  props.source === 'OVERRIDE' ? '该值已在驿站单独覆盖，不随全局默认变化' : '继承自全局默认'
)
</script>

<template>
  <StatusTag :dict="dict" :value="source" :variant="source === 'OVERRIDE' ? 'soft' : 'outline'" :title="title" />
</template>
