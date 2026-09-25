<script setup>
import { onUnmounted, ref, watch } from 'vue'

/**
 * 列表/详情页状态包装（C-M6：加载中 / 失败可重试 / 空）
 * 错误态与空态必须文案不同且错误态必给重试 —— 现状 4 个页面 catch 后列表为空，
 * 用户看到「没有数据」会误判为业务为空而不是系统异常（修 P14 移动端版本）。
 * 四态之外的「无权限」用 variant="denied"：只读降级 + 说明，不渲染不可用按钮（UI 规范 4.3）。
 */
const props = defineProps({
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  empty: { type: Boolean, default: false },
  emptyText: { type: String, default: '暂无数据' },
  /** 错误态的次级说明，默认给出可操作建议 */
  errorHint: { type: String, default: '请检查网络后重试，若持续失败请联系管理员' },
  /** 'denied' = 只读降级（无权限）：只给说明，不渲染任何不可用按钮；空串 = 三个数据态照旧 */
  variant: { type: String, default: '' },
  deniedText: { type: String, default: '暂无查看权限' },
  /** 降级说明必须写清「谁能办、去哪办」，否则用户只会反复点空白页 */
  deniedHint: { type: String, default: '该内容由其他角色办理，如需查询请联系所在驿站站长或人事' },
  rows: { type: Number, default: 4 }
})

const emit = defineEmits(['retry'])

// 加载 <300ms 不显示骨架：避免快请求下骨架闪一下反而更「跳」（C-M6）
const SKELETON_DELAY = 200
const showSkeleton = ref(false)
let skeletonTimer = null

watch(
  () => props.loading,
  (value) => {
    clearTimeout(skeletonTimer)
    if (!value) {
      showSkeleton.value = false
      return
    }
    skeletonTimer = setTimeout(() => {
      showSkeleton.value = true
    }, SKELETON_DELAY)
  },
  { immediate: true }
)

onUnmounted(() => clearTimeout(skeletonTimer))
</script>

<template>
  <div class="page-state">
    <!-- 加载中一律不渲染默认插槽：数据未到位时插槽里的字段访问会空指针 -->
    <div v-if="loading" class="page-state__loading">
      <div v-if="showSkeleton" class="page-state__skeleton">
        <van-skeleton :row="rows" row-width="100%" />
      </div>
    </div>

    <div v-else-if="variant === 'denied'" class="page-state__block" role="status">
      <van-icon name="lock" class="page-state__icon" aria-hidden="true" />
      <p class="page-state__text">{{ deniedText }}</p>
      <p class="page-state__hint">{{ deniedHint }}</p>
    </div>

    <div v-else-if="error" class="page-state__block" role="alert">
      <van-icon name="warning-o" class="page-state__icon page-state__icon--error" aria-hidden="true" />
      <p class="page-state__text">{{ error }}</p>
      <p class="page-state__hint">{{ errorHint }}</p>
      <button type="button" class="page-state__action" @click="emit('retry')">重新加载</button>
    </div>

    <div v-else-if="empty" class="page-state__block">
      <van-icon name="logistics" class="page-state__icon" aria-hidden="true" />
      <p class="page-state__text">{{ emptyText }}</p>
      <slot name="empty-action" />
    </div>

    <slot v-else />
  </div>
</template>

<style scoped>
.page-state__skeleton {
  padding: var(--sp-4);
  background: var(--surface-card);
  border-radius: var(--r-lg);
}

.page-state__block {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 160px;
  padding: var(--sp-10) var(--sp-4);
  text-align: center;
}

.page-state__icon {
  font-size: 48px;
  color: var(--text-disabled);
}

.page-state__icon--error {
  font-size: 40px;
  color: var(--color-danger);
}

.page-state__text {
  margin: var(--sp-4) 0 0;
  font-size: var(--fs-body);
  line-height: var(--lh-body);
  color: var(--text-3);
}

.page-state__hint {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

/* 重试为次要控件，用描边式避免空态区出现强主色块；高度 44 满足触控要求。
 * 描边取 500 档 --color-primary-icon，与 .chip--active 等既有描边控件同口径（P2-3） */
.page-state__action {
  min-height: var(--touch-min);
  padding: 0 var(--sp-5);
  margin-top: var(--sp-4);
  font-size: var(--fs-body);
  color: var(--color-primary);
  background: var(--surface-card);
  border: 1px solid var(--color-primary-icon);
  border-radius: var(--r-full);
}
</style>
