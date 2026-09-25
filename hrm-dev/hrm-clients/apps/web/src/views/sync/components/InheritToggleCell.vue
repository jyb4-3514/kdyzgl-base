<script setup>
import { computed } from 'vue'
import { SOURCE_LABEL } from '../utils/configCenter.js'
import SourceBadge from './SourceBadge.vue'

/**
 * 覆盖矩阵单元格（设计 E.1 Molecule）：值 + 来源徽标 + 覆盖竖线
 * 「继承 vs 已覆盖」三重冗余呈现（B.5）：来源标签 + 值文字深浅 + 单元格底色/竖线。
 * 只做展示，点击进入抽屉由表格的操作列承担（避免单元格误触改配置）。
 */
const props = defineProps({
  stationName: { type: String, default: '' },
  itemName: { type: String, default: '' },
  source: { type: String, default: 'INHERIT' },
  value: { type: String, default: '—' },
  supported: { type: Boolean, default: true }
})

const overridden = computed(() => props.source === 'OVERRIDE')

const ariaLabel = computed(() =>
  props.supported
    ? `${props.stationName} ${props.itemName} ${SOURCE_LABEL[props.source] || ''} ${props.value}`
    : `${props.stationName} ${props.itemName} 暂不支持`
)
</script>

<template>
  <div
    class="override-cell"
    :class="{ 'is-override': overridden && supported, 'is-unsupported': !supported }"
    :aria-label="ariaLabel"
  >
    <template v-if="supported">
      <SourceBadge :source="source" />
      <span class="override-cell__value" :class="{ 'is-muted': !overridden }" :title="value">{{ value }}</span>
    </template>
    <span v-else class="override-cell__value is-muted" :title="'该配置项尚未纳入接口返回，暂不支持设置'">暂不支持</span>
  </div>
</template>

<style scoped lang="scss">
.override-cell {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  box-sizing: border-box;
  height: 100%;
  min-height: var(--table-row-h);
  padding: 0 var(--sp-3);
  background-color: var(--surface-sub);

  // 已覆盖：白底 + 左侧 2px 主色竖线（纯装饰性强调，不承载白字，按硬规则走 500 档）
  &.is-override {
    background-color: var(--surface-card);
    border-left: 2px solid var(--color-primary-icon);
  }

  &__value {
    overflow: hidden;
    font-size: var(--fs-body);
    color: var(--text-1);
    text-overflow: ellipsis;
    white-space: nowrap;

    &.is-muted {
      color: var(--text-3);
    }
  }
}
</style>
