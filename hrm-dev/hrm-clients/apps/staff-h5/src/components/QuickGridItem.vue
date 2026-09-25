<script setup>
import { computed } from 'vue'
import { badgeText } from '../utils/format.js'

/**
 * 首页快捷功能宫格单元（D2-1，Molecule）
 *
 * 三种变体互斥（B4-1）：count 计数型（角标）/ status 状态型（第二行数据行）/ plain 纯入口型。
 * 为什么自绘角标而不是用 van-grid-item 的 badge 属性：宫格单元的可聚焦元素是 Vant 内部的内容层，
 * 外部属性传不进去，`aria-label` 只能落在不可聚焦的外层容器上，读屏读不到「N 条待处理」；
 * 自绘后把 aria-label 直接绑在原生链接上，同时满足「整项为链接」与「角标可播报」两条硬性要求。
 *
 * 状态（纯展示原子，无远程取数）：
 *   禁用 / 无权限不适用 —— 无权限的项由父级过滤后不渲染，不做置灰；
 *   加载 / 空 / 错误三态由父级给定的 `value` / `statusText` 表达，null 一律降级，绝不用 0 冒充未知。
 */
const props = defineProps({
  icon: { type: String, required: true },
  name: { type: String, required: true },
  to: { type: [String, Object], required: true },
  /** count 计数型 / status 状态型 / plain 纯入口型 */
  type: { type: String, default: 'plain' },
  /** 计数型数值：null 表示取数失败或未知（不渲染角标） */
  value: { type: [Number, String], default: null },
  /** 状态型数据行文案：null 表示取数失败（显示占位）；确无数据的业务零值由调用方给文案 */
  statusText: { type: String, default: null },
  loading: { type: Boolean, default: false }
})

const badge = computed(() => (props.type === 'count' ? badgeText(props.value) : ''))

/** 状态型数据行：加载中 `···`、失败 `—`；计数型与纯入口型不渲染该行 */
const dataText = computed(() => {
  if (props.type !== 'status') return ''
  if (props.loading) return '···'
  return props.statusText == null ? '—' : props.statusText
})

/** 读屏文案把实时值一并播报，避免只读出「工单管理」而漏掉「2 条待处理」 */
const ariaLabel = computed(() => {
  if (props.type === 'count' && badge.value) return `${props.name}，${badge.value} 条待处理`
  if (props.type === 'status' && dataText.value) return `${props.name}，${dataText.value}`
  return props.name
})
</script>

<template>
  <van-grid-item>
    <!-- 用默认插槽整体接管内容：把点击与键盘行为交给原生 router-link，不再依赖 Vant 的额外键处理 -->
    <router-link :to="to" class="quick-item" :aria-label="ariaLabel">
      <span class="quick-item__icon">
        <van-icon :name="icon" aria-hidden="true" />
        <span v-if="badge" class="quick-item__badge" aria-hidden="true">{{ badge }}</span>
      </span>
      <span class="quick-item__name">{{ name }}</span>
      <span v-if="type === 'status'" class="quick-item__data">{{ dataText }}</span>
    </router-link>
  </van-grid-item>
</template>

<style scoped>
.quick-item {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 100%;
  color: inherit;
  text-align: center;
  text-decoration: none;
  border-radius: var(--r-sm);
  transition: background-color var(--dur-fast) var(--ease-std);
  -webkit-tap-highlight-color: transparent;
}

.quick-item:active {
  background: var(--surface-subtle);
}

.quick-item__icon {
  position: relative;
  display: inline-flex;
  font-size: var(--van-grid-item-icon-size);
  line-height: 1;
  color: var(--color-primary-icon);
}

/* 角标：底取 --color-danger、白字 10px（B4-1 / C4），压过 Vant 默认的 12px 字号 */
.quick-item__badge {
  position: absolute;
  top: -6px;
  left: 60%;
  min-width: 16px;
  height: 16px;
  padding: 0 3px;
  font-size: 10px;
  font-weight: var(--fw-medium);
  line-height: 16px;
  color: var(--text-on-dark);
  background: var(--color-danger);
  border-radius: var(--r-full);
}

.quick-item__name {
  margin-top: var(--sp-2);
  font-size: var(--van-grid-item-text-font-size);
  font-weight: var(--fw-regular);
  line-height: 1.4;
  color: var(--van-grid-item-text-color);
  word-break: break-all;
}

/* 状态型数据行：与名称同字号、色降一档（B4-1）；超宽省略，完整值进业务页看 */
.quick-item__data {
  max-width: 100%;
  overflow: hidden;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
