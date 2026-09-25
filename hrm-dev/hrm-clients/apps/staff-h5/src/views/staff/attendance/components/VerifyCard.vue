<script setup>
import { computed } from 'vue'
import MiniChip from '@/components/MiniChip.vue'

/**
 * 打卡前自查卡通用骨架（WiFi / 定位 / 规则三张同构卡共用）
 *
 * 三张卡的「标题 + 标记 / 主值 / 明细行」结构完全同构，是页内最大的重复面；给定数据即可复用。
 * 标记用 MiniChip（非交互属性标记），不用 StatusTag —— 后者表达业务状态流转，语义不同不可共用。
 * 未取到定位时主值给「尚未获取到」而不是空白：空白会被读成「加载中」，与「确实没取到」不是一回事。
 */
const props = defineProps({
  title: { type: String, required: true },
  /** { text, tone, note }；无标记时传 null，此时用 headExtra 渲染卡头右侧说明 */
  badge: { type: Object, default: null },
  /** 主值；空串表示该卡无主值行（如规则摘要卡） */
  value: { type: String, default: '' },
  /** 主值是否按数字排版（坐标 / 距离类需等宽数字对齐） */
  numeric: { type: Boolean, default: false },
  /** 明细行：[{ text, numeric }] 或字符串数组 */
  hints: { type: Array, default: () => [] },
  /** 标记胶囊：[{ text, on, tone }] */
  chips: { type: Array, default: () => [] },
  /** 错误行（role="alert"），如定位失败原因 */
  errorText: { type: String, default: '' },
  /** 无标记时卡头右侧的说明文案（如规则名） */
  headExtra: { type: String, default: '' }
})

/** 允许明细行传字符串（纯文案）或对象（带数字排版标记），统一成对象后模板只认一种形态 */
const hintList = computed(() =>
  props.hints.map((hint) => (typeof hint === 'string' ? { text: hint, numeric: false } : hint))
)
</script>

<template>
  <div class="card verify">
    <div class="verify__head">
      <span class="verify__label">{{ title }}</span>
      <MiniChip v-if="badge" :text="badge.text" :on="true" :tone="badge.tone" :role="badge.note ? 'note' : undefined" />
      <span v-else-if="headExtra" class="verify__hint">{{ headExtra }}</span>
    </div>

    <div v-if="value" class="verify__value" :class="{ 'tabular-nums': numeric }">{{ value }}</div>

    <p v-for="(hint, index) in hintList" :key="index" class="verify__hint" :class="{ 'tabular-nums': hint.numeric }">
      {{ hint.text }}
    </p>

    <div v-if="chips.length" class="verify__chips">
      <MiniChip v-for="chip in chips" :key="chip.text" :text="chip.text" :on="chip.on" :tone="chip.tone" />
    </div>

    <p v-if="errorText" class="verify__error" role="alert">{{ errorText }}</p>

    <slot />
  </div>
</template>

<style scoped>
/* 相邻自查卡之间留一档间距（.card + .card 亦给同名间距，二者同值不冲突） */
.verify + .verify {
  margin-top: var(--sp-3);
}

.verify__head {
  display: flex;
  gap: var(--sp-2);
  align-items: center;
  justify-content: space-between;
}

.verify__label {
  font-size: var(--fs-body);
  font-weight: var(--fw-medium);
  color: var(--text-1);
}

.verify__value {
  margin-top: var(--sp-2);
  font-size: var(--fs-num-sm);
  font-weight: var(--fw-semibold);
  word-break: break-all;
}

.verify__hint {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

.verify__error {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--color-danger);
}

.verify__chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-1);
  margin-top: var(--sp-2);
}

/* 插槽内容由父组件渲染，作用域标识挂在父组件上，故必须用 :deep 才能命中 */
.verify :deep(.verify__btn) {
  min-height: var(--touch-min);
  margin-top: var(--sp-3);
}

/* 演示辅助整行可点（高度接触控下限），开关自身仍是可聚焦控件，键盘可操作 */
.verify :deep(.assist) {
  display: flex;
  gap: var(--sp-3);
  align-items: center;
  min-height: var(--touch-min);
  padding-top: var(--sp-3);
  margin-top: var(--sp-3);
  border-top: 1px solid var(--border-line);
  cursor: pointer;
}

.verify :deep(.assist__text) {
  flex: 1;
  min-width: 0;
}

.verify :deep(.assist__title) {
  margin: 0;
  font-size: var(--fs-body);
  font-weight: var(--fw-medium);
  color: var(--color-warning);
}

.verify :deep(.assist__hint) {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}
</style>
