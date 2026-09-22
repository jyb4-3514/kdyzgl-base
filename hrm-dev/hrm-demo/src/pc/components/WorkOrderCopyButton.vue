<script setup>
import { ElMessage } from 'element-plus'
import { DocumentCopy } from '@element-plus/icons-vue'
import { copyText } from '@/shared/domain/text.js'
import { buildWorkOrderText } from '@/shared/domain/workOrderText.js'

/**
 * 工单复制按钮（PC：列表行内 + 详情抽屉底两处复用）
 *
 * 为什么抽组件：两处同一套「拼文本 → 复制 → 成功/失败反馈」，各写一份必然在失败文案与字段顺序上分叉。
 * 文本拼装只走 shared/domain/workOrderText.js，本组件不重排字段、不改标签。
 * 列表行内的图标态由列表页样式补足可点区域（.wo-copy）。
 */
const props = defineProps({
  /** 工单列表 / 详情 VO */
  order: { type: Object, default: null },
  /** icon = 列表行内图标按钮（带 tooltip）；text = 详情抽屉底的文字按钮 */
  variant: { type: String, default: 'icon' },
  /** 可读文案：列表与详情的语义不同，故由调用方给 */
  label: { type: String, default: '复制工单详情' }
})

async function onCopy() {
  const ok = await copyText(buildWorkOrderText(props.order))
  // 两条剪贴板通道都失败时必须明确告知，不做静默失败
  if (ok) ElMessage.success('已复制工单详情')
  else ElMessage.error('复制失败，请手动选中文本后复制')
}
</script>

<template>
  <el-tooltip v-if="variant === 'icon'" :content="label" placement="top">
    <el-button link class="wo-copy" :icon="DocumentCopy" :aria-label="label" @click="onCopy" />
  </el-tooltip>
  <el-button v-else :icon="DocumentCopy" plain @click="onCopy">复制详情</el-button>
</template>
