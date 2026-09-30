<script setup>
import { computed } from 'vue'
import {
  COLLECT_STATE,
  PARCEL_STATUS,
  PAYROLL_STATUS,
  SYNC_LOG_LEVEL,
  SYNC_STATUS,
  WORK_ORDER_PRIORITY,
  WORK_ORDER_STATUS
} from '@kdyzgl/shared/constants/dict.js'

/**
 * 业务状态胶囊（C-M3）
 * 形态（soft/outline/solid）由字典项的 variant 承载，字典是唯一真源；
 * 但 variant 只表达「有没有动作诉求」，不表达色族，且同一 type 下仍有
 * 「待入库(浅底 info) vs 已退回(描边中性)」这类差异，故保留 DICT_COLORS 做色值覆盖表。
 * 为什么不用 van-tag：2.7 要求同时指定「底 / 字 / 描边」三色且三态可切换，
 * Vant Tag 的 plain 只覆盖底与描边两种组合，硬凑会出现两套形态（现状 P30）。
 */
const props = defineProps({
  dict: { type: Object, required: true },
  value: { type: [Number, String], default: '' },
  /** 显式指定形态，优先级最高，用于通知类型等统一描边的场景 */
  variant: { type: String, default: '' }
})

/**
 * 按「字典 + 值」覆盖配色：只在 type 色族不足以表达时登记
 * 注意：键必须是 shared/constants/dict.js 的导出对象本体，用局部副本会静默失配并退化到 TYPE_FALLBACK
 */
const DICT_COLORS = new Map([
  [
    PARCEL_STATUS,
    {
      0: { surface: 'var(--color-info-surface)', text: 'var(--color-info-text)' },
      1: { surface: 'var(--color-warning-surface)', text: 'var(--color-warning)' },
      2: { surface: 'var(--color-success-surface)', text: 'var(--color-success)' },
      3: { surface: 'var(--color-danger-surface)', text: 'var(--color-danger)' },
      4: { text: 'var(--text-3)' }
    }
  ],
  [
    WORK_ORDER_STATUS,
    {
      0: { surface: 'var(--color-warning-surface)', text: 'var(--color-warning)' },
      1: { surface: 'var(--color-primary-surface)', text: 'var(--color-primary)' },
      2: { surface: 'var(--color-success-surface)', text: 'var(--color-success)' },
      3: { text: 'var(--text-3)' }
    }
  ],
  [
    WORK_ORDER_PRIORITY,
    {
      0: { text: 'var(--text-3)' },
      1: { surface: 'var(--color-warning-surface)', text: 'var(--color-warning)' },
      2: { surface: 'var(--color-danger)', text: 'var(--text-on-dark)' }
    }
  ],
  [
    SYNC_STATUS,
    {
      0: { text: 'var(--text-3)' },
      1: { surface: 'var(--color-primary-surface)', text: 'var(--color-primary)' },
      2: { surface: 'var(--color-success-surface)', text: 'var(--color-success)' },
      3: { surface: 'var(--color-danger-surface)', text: 'var(--color-danger)' }
    }
  ],
  [
    SYNC_LOG_LEVEL,
    {
      0: { surface: 'var(--color-info-surface)', text: 'var(--color-info-text)' },
      1: { surface: 'var(--color-warning-surface)', text: 'var(--color-warning)' },
      2: { surface: 'var(--color-danger-surface)', text: 'var(--color-danger)' }
    }
  ],
  [
    // 采集状态（需求1）：异常需立刻行动用实心，已停用无动作诉求用描边
    COLLECT_STATE,
    {
      NORMAL: { surface: 'var(--color-success-surface)', text: 'var(--color-success)' },
      ABNORMAL: { surface: 'var(--color-danger)', text: 'var(--text-on-dark)' },
      UNCONFIGURED: { surface: 'var(--color-warning-surface)', text: 'var(--color-warning)' },
      DISABLED: { text: 'var(--text-3)' }
    }
  ],
  [
    // 工资单（需求9）：APPROVED 与 CONFIRMED 同归 success 族，靠步骤条位置区分，不再单独覆盖配色
    PAYROLL_STATUS,
    {
      DRAFT: { text: 'var(--text-3)' },
      PENDING_APPROVAL: { surface: 'var(--color-warning-surface)', text: 'var(--color-warning)' },
      REJECTED: { surface: 'var(--color-danger-surface)', text: 'var(--color-danger)' },
      PUBLISHED: { surface: 'var(--color-primary-surface)', text: 'var(--color-primary)' },
      CONFIRMED: { surface: 'var(--color-success-surface)', text: 'var(--color-success)' },
      // OBJECTED 是 solid 变体：不补覆盖会回落 warning 淡底（与 soft 无异），必须显式给实底 + 白字
      OBJECTED: { surface: 'var(--color-warning)', text: 'var(--text-on-dark)' }
      // PAID 为 success + outline，文字色与描边由 variant 类与 type 兜底决定，无需覆盖
    }
  ]
])

/** 未登记的字典回落 type 语义，保证新字典不写样式也能用 */
const TYPE_FALLBACK = {
  info: { variant: 'outline', text: 'var(--text-3)' },
  primary: { variant: 'soft', surface: 'var(--color-primary-surface)', text: 'var(--color-primary)' },
  success: { variant: 'soft', surface: 'var(--color-success-surface)', text: 'var(--color-success)' },
  warning: { variant: 'soft', surface: 'var(--color-warning-surface)', text: 'var(--color-warning)' },
  danger: { variant: 'soft', surface: 'var(--color-danger-surface)', text: 'var(--color-danger)' }
}

const item = computed(() => props.dict[props.value] || null)
// 值无匹配时渲染「—」而不是空白，避免用户误以为漏数据（C-P3 同口径）
const matched = computed(() => !!item.value)
const style = computed(() => {
  const fallback = TYPE_FALLBACK[(item.value && item.value.type) || 'info'] || TYPE_FALLBACK.info
  const byDict = DICT_COLORS.get(props.dict)
  const color = (byDict && byDict[props.value]) || fallback
  return {
    // 形态：显式传入 > 字典 variant > type 兜底；配色：字典覆盖 > type 兜底
    variant: props.variant || (item.value && item.value.variant) || fallback.variant,
    surface: color.surface,
    text: color.text
  }
})

const variantClass = computed(() => `status-tag--${style.value.variant}`)

const inlineStyle = computed(() => ({
  '--tag-surface': style.value.surface || 'var(--surface-card)',
  '--tag-text': style.value.text || 'var(--text-2)'
}))
</script>

<template>
  <span v-if="!matched" class="status-tag status-tag--missing">—</span>
  <span v-else class="status-tag" :class="variantClass" :style="inlineStyle">{{ item.label }}</span>
</template>

<style scoped>
.status-tag {
  display: inline-flex;
  align-items: center;
  height: var(--tag-h);
  padding: 0 var(--tag-pad-x);
  font-size: var(--fs-micro);
  font-weight: var(--fw-regular);
  line-height: 1;
  white-space: nowrap;
  border: 1px solid transparent;
  border-radius: var(--r-full);
}

/* 浅底：状态描述（无动作诉求） */
.status-tag--soft {
  color: var(--tag-text);
  background: var(--tag-surface);
  border-color: transparent;
}

/* 描边：终态（已退回/已关闭/低优先级/待领取） */
.status-tag--outline {
  color: var(--tag-text);
  background: var(--surface-card);
  border-color: var(--state-outline-border);
}

/* 实心：需要立刻行动（高优先级、超时） */
.status-tag--solid {
  color: var(--tag-text);
  background: var(--tag-surface);
  border-color: transparent;
}

.status-tag--missing {
  color: var(--text-disabled);
}
</style>
