<script setup>
import { computed } from 'vue'
import { Box, Lock, WarningFilled } from '@element-plus/icons-vue'

/**
 * 空态 / 错误态 / 无权限态（C-P7 / T07）
 * 为什么必须单独建：改前包裹、同步、工单、通知 4 页把接口异常当成"没有数据"渲染（P14），
 * 用户会把系统故障误判成业务为空；这里把三态做成互斥的独立组件，让页面无法再"顺手"用空态糊过去。
 */
const props = defineProps({
  variant: { type: String, default: 'empty' }, // empty | error | denied
  title: { type: String, default: '' },
  // 错误态给可操作建议（如"请检查网络后重试"），空态给下一步引导
  description: { type: String, default: '' },
  actionText: { type: String, default: '' },
  actionLoading: { type: Boolean, default: false }
})

const emit = defineEmits(['action'])

const DEFAULT_TEXT = {
  empty: { title: '暂无数据', description: '' },
  error: { title: '数据加载失败', description: '请检查网络后重试' },
  denied: { title: '无访问权限', description: '请联系管理员开通对应权限' }
}

const icon = computed(() => (props.variant === 'error' ? WarningFilled : props.variant === 'denied' ? Lock : Box))
const mainText = computed(() => props.title || DEFAULT_TEXT[props.variant].title)
const subText = computed(() => props.description || DEFAULT_TEXT[props.variant].description)
// 错误态必须给出口，否则用户只能刷新整页；空态/无权限态按需给
const btnText = computed(() => props.actionText || (props.variant === 'error' ? '重试' : ''))
</script>

<template>
  <div class="state-block" :class="`state-block--${variant}`" :role="variant === 'error' ? 'alert' : undefined">
    <el-icon class="state-block__icon" :size="48" aria-hidden="true"><component :is="icon" /></el-icon>
    <p class="state-block__title">{{ mainText }}</p>
    <p v-if="subText" class="state-block__desc">{{ subText }}</p>
    <el-button
      v-if="btnText"
      class="state-block__action"
      type="primary"
      plain
      :loading="actionLoading"
      @click="emit('action')"
    >
      {{ btnText }}
    </el-button>
  </div>
</template>

<style scoped lang="scss">
.state-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: var(--sp-10) var(--sp-4);
  text-align: center;

  &__icon {
    color: var(--text-disabled);
  }

  &__title {
    margin: var(--sp-3) 0 0;
    font-size: var(--fs-body);
    line-height: var(--lh-body);
    color: var(--text-2);
  }

  &__desc {
    margin: var(--sp-1) 0 0;
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    color: var(--text-3);
  }

  &__action {
    margin-top: var(--sp-4);
  }

  // 错误态图标取危险 500 档（仅图标与描边允许用 500 档）
  &--error .state-block__icon {
    color: var(--color-danger-icon);
  }

  &--denied .state-block__icon {
    /* 无权限图标用 400 档弱化前景：语义既非占位文本也非禁用文本，保留 L1 直引（P2-4 已登记） */
    color: var(--c-neutral-400);
  }
}
</style>
