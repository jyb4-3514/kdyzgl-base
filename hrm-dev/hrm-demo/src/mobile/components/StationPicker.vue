<script setup>
/**
 * 驿站选择弹层（管理端复用）
 * 为什么抽成组件：考勤记录 / 排班 / 工单三处都要「选一个驿站」这一步，内联各写一份会出现
 * popup + 列表 + 选中态 + 样式四处重复，且「是否给『全部驿站』选项」的差异很快就会分叉成两套。
 * 这里用 allowAll 一个开关表达差异，其余全部收口。
 */
defineProps({
  show: { type: Boolean, default: false },
  stations: { type: Array, default: () => [] },
  /** 选中的驿站 id；null 表示全部驿站（allowAll 为 true 时才出现该项） */
  modelValue: { type: Number, default: null },
  allowAll: { type: Boolean, default: true },
  title: { type: String, default: '选择驿站' },
  /** 取数中：给骨架而不是空列表，否则「还没加载完」会被读成「没有驿站」 */
  loading: { type: Boolean, default: false },
  /** 取数失败原因：区块级错误 + 重试，不把整个弹层替换成错误页（弹层仍是选择面板） */
  error: { type: String, default: '' },
  /** 确实没有可选驿站时的说明，与错误态文案必须不同 */
  emptyText: { type: String, default: '暂无可选驿站' }
})

const emit = defineEmits(['update:show', 'select', 'retry'])

/** 选中即关闭：这是一次性选择而不是查询面板，留在屏幕上会挡住刚筛选出来的内容 */
function pick(id) {
  emit('update:show', false)
  emit('select', id)
}
</script>

<template>
  <van-popup :show="show" round position="bottom" safe-area-inset-bottom @update:show="emit('update:show', $event)">
    <div class="sheet" :aria-busy="loading || undefined">
      <div class="sheet__title">{{ title }}</div>

      <!-- 取数中：等高骨架（3 行 × 48 与真实行同高），数据到达不跳版 -->
      <div v-if="loading" class="sheet__state" aria-hidden="true">
        <span v-for="i in 3" :key="i" class="sheet__sk" />
      </div>

      <!-- 失败不整层替换：标题与容器仍在，仅列表区给原因 + 重试 -->
      <div v-else-if="error" class="sheet__state sheet__state--error" role="alert">
        <p class="sheet__error">{{ error }}</p>
        <button type="button" class="sheet__retry" @click="emit('retry')">重新加载</button>
      </div>

      <template v-else>
        <button
          v-if="allowAll"
          type="button"
          class="sheet__item"
          :aria-pressed="modelValue === null"
          @click="pick(null)"
        >
          <span>全部驿站</span>
          <van-icon v-if="modelValue === null" name="passed" aria-hidden="true" />
        </button>
        <button
          v-for="item in stations"
          :key="item.id"
          type="button"
          class="sheet__item"
          :aria-pressed="item.id === modelValue"
          @click="pick(item.id)"
        >
          <span>{{ item.stationName }}</span>
          <van-icon v-if="item.id === modelValue" name="passed" aria-hidden="true" />
        </button>
        <!-- 空态与错误态文案分离：这里是「确实没有」，不是「取不到」 -->
        <p v-if="!stations.length" class="sheet__empty">{{ emptyText }}</p>
      </template>
    </div>
  </van-popup>
</template>

<style scoped>
.sheet {
  padding: var(--sp-5) 0 var(--sp-6);
}

.sheet__title {
  margin-bottom: var(--sp-3);
  font-size: var(--fs-h2);
  font-weight: var(--fw-semibold);
  text-align: center;
}

/* 列表项 48px：弹层内是手指点选目标，顶着 44 下限不如留余量（7.4） */
.sheet__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-height: 48px;
  padding: 0 var(--sp-4);
  font-size: var(--fs-body-strong);
  color: var(--text-1);
  background: none;
  border: none;
  border-top: 1px solid var(--border-line);
}

.sheet__item[aria-pressed='true'] {
  color: var(--color-primary);
  background: var(--color-primary-surface);
}

/* 列表区三态共用容器：左右内边距与列表项对齐，视觉上仍属同一张面板 */
.sheet__state {
  padding: 0 var(--sp-4);
}

.sheet__state--error {
  padding-bottom: var(--sp-4);
  text-align: center;
}

/* 骨架行高 48 与真实列表项一致，避免数据到达时高度跳动 */
.sheet__sk {
  display: block;
  height: 48px;
  margin-top: var(--sp-2);
  background: var(--surface-sunken);
  border-radius: var(--r-sm);
  animation: skeleton-pulse 1.2s var(--ease-std) infinite;
}

.sheet__error {
  margin: 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--color-danger);
}

/* 重试为次要控件：描边取 500 档，与 .page-state__action 同口径，高度 44 满足触控 */
.sheet__retry {
  display: block;
  width: 100%;
  min-height: 44px;
  margin-top: var(--sp-3);
  font-size: var(--fs-body);
  color: var(--color-primary);
  background: var(--surface-card);
  border: 1px solid var(--color-primary-icon);
  border-radius: var(--r-sm);
}

.sheet__empty {
  margin: var(--sp-5) var(--sp-4);
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
  text-align: center;
}
</style>
