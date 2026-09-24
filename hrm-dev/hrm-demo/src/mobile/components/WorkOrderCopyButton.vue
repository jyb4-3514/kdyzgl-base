<script setup>
import { ref } from 'vue'
import { showFailToast, showSuccessToast } from 'vant'
import { copyText } from '@/shared/domain/text.js'
import { buildWorkOrderText } from '@/shared/domain/workOrderText.js'

/**
 * 工单复制按钮（移动端：员工端列表 / 管理端列表 / 共用详情页三处复用）
 *
 * 为什么抽组件：三处同一套「拼文本 → 复制 → 成功/失败反馈」，各写一份必然分叉失败文案与热区尺寸。
 *
 * B0.2 七态登记：
 * - 默认：待点击（图标按钮 44×44）
 * - 加载：点击后置 busy 禁点，避免连点弹多次 toast（copyText 是异步的）
 * - 禁用：disabled（父级按权限/状态传入时生效）
 * - 空 / 错误 / 无权限 / 边界：不适用 —— 数据由父组件传入，本组件不取数、不渲染空态；
 *   拼装文本为空与复制通道失败合并为同一条失败提示
 *
 * 图标说明：Vant 4.10 图标清单中无「复制」语义图标（已核对 node_modules/vant/es/icon），
 * 取 description-o（文档）表达「复制这条工单的内容」，语义由 aria-label 承担。
 */
const props = defineProps({
  /** 工单列表 / 详情 VO */
  order: { type: Object, default: null },
  /** 可读文案：列表与详情的语义不同（详情=复制详情，列表=复制该行工单） */
  label: { type: String, default: '复制工单详情' },
  disabled: { type: Boolean, default: false }
})

const busy = ref(false)

async function onCopy() {
  if (busy.value || props.disabled) return
  busy.value = true
  try {
    const ok = await copyText(buildWorkOrderText(props.order))
    // 两条剪贴板通道都失败时必须明确告知：安卓壳 file:// 下 clipboard API 不可用是常态
    if (ok) showSuccessToast('已复制工单详情')
    else showFailToast('复制失败，请长按选中文本手动复制')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <!-- click/keydown 都阻断冒泡：列表行自身是 role=button，键盘 Enter 落在复制按钮上时不能顺带打开详情 -->
  <button
    type="button"
    class="copy-btn"
    :class="{ 'copy-btn--disabled': disabled }"
    :disabled="disabled || busy"
    :aria-label="label"
    @click.stop="onCopy"
    @keydown.stop
  >
    <van-icon name="description-o" aria-hidden="true" />
  </button>
</template>

<style scoped>
/* 44×44 热区：一线员工戴手套点按，不能只按图标大小给命中区 */
.copy-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  font-size: var(--fs-h2);
  color: var(--color-primary-icon);
  background: none;
  border: none;
}

.copy-btn:disabled {
  color: var(--text-disabled);
}
</style>
