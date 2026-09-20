<script setup>
/**
 * 驿站选择弹层（老板端复用）
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
  title: { type: String, default: '选择驿站' }
})

const emit = defineEmits(['update:show', 'select'])

/** 选中即关闭：这是一次性选择而不是查询面板，留在屏幕上会挡住刚筛选出来的内容 */
function pick(id) {
  emit('update:show', false)
  emit('select', id)
}
</script>

<template>
  <van-popup :show="show" round position="bottom" safe-area-inset-bottom @update:show="emit('update:show', $event)">
    <div class="sheet">
      <div class="sheet__title">{{ title }}</div>
      <button v-if="allowAll" type="button" class="sheet__item" :aria-pressed="modelValue === null" @click="pick(null)">
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
</style>
