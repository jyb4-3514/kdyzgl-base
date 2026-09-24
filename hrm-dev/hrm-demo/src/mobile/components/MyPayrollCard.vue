<script setup>
import { computed, ref } from 'vue'
import StatusTag from './StatusTag.vue'
import { PAYROLL_STATUS } from '@/shared/constants/dict.js'
import { moneyText } from '../utils/format.js'

/**
 * 工资单卡片（C4 Organism，移动端）：摘要 + 逐项明细二合一
 * 为什么合并：管理端详情与员工端详情都要「上半看实发、下半看构成」，拆两个组件会把同一份明细表写两遍；
 * 用 showItems 控制信息密度即可覆盖两种场景。
 *
 * 状态覆盖（B0.2 七态）：
 * - 默认：月份 / 员工 / 实发 / 状态 / 明细
 * - 加载：块骨架
 * - 空 / 错误：由页面 PageState 承担；payroll 为空时本组件给兜底说明
 * - 禁用 / 无权限：不适用 —— 纯展示，操作按钮由页面 ActionBar 按状态渲染
 * - 边界：明细超过 maxVisible 折叠（可展开）；扣项带 `-` 号而非只靠红色（C6 SC 1.4.1）
 */
const props = defineProps({
  payroll: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  /** 列表场景传 false 只显示摘要，详情场景展示逐项明细 */
  showItems: { type: Boolean, default: true },
  maxVisible: { type: Number, default: 6 }
})

const expanded = ref(false)
const items = computed(() => (props.payroll && props.payroll.items) || [])
const visibleItems = computed(() => (expanded.value ? items.value : items.value.slice(0, props.maxVisible)))
const hiddenCount = computed(() => Math.max(items.value.length - props.maxVisible, 0))

/** 扣项统一加负号：金额本身在契约里是正数，符号由展示层按 type 补 */
const signedMoney = (item) => moneyText(item.type === 'DEDUCTION' ? -Math.abs(item.amount) : item.amount)
</script>

<template>
  <div v-if="loading" class="card sk-payroll skeleton-block" />
  <div v-else-if="!payroll" class="card">
    <p class="tip">工资单数据不可用，请返回列表重试</p>
  </div>
  <div v-else class="card pay-card">
    <div class="pay-card__head">
      <div class="pay-card__id">
        <p class="pay-card__month tabular-nums">{{ payroll.month }} 工资单</p>
        <p class="list-item__meta">
          {{ payroll.employeeName }} · {{ payroll.billTypeLabel }} · {{ payroll.stationName || '总部' }}
        </p>
      </div>
      <StatusTag :dict="PAYROLL_STATUS" :value="payroll.status" />
    </div>

    <div class="pay-card__net">
      <span class="list-item__meta">实发合计</span>
      <span class="pay-card__amount tabular-nums">{{ moneyText(payroll.netAmount) }}</span>
    </div>

    <template v-if="showItems">
      <van-cell-group class="pay-card__items">
        <van-cell v-for="item in visibleItems" :key="item.key" :title="item.name">
          <template #label>
            <span class="pay-card__detail">{{ item.detail }}</span>
          </template>
          <template #value>
            <span class="tabular-nums" :class="{ 'pay-card__minus': item.type === 'DEDUCTION' }">{{
              signedMoney(item)
            }}</span>
          </template>
        </van-cell>
      </van-cell-group>

      <button v-if="hiddenCount" type="button" class="pay-card__more" @click="expanded = !expanded">
        {{ expanded ? '收起明细' : `展开剩余 ${hiddenCount} 项` }}
      </button>

      <div class="pay-card__foot tabular-nums">
        <span class="list-item__meta">应发 {{ moneyText(payroll.grossAmount) }}</span>
        <span class="list-item__meta">扣款 {{ moneyText(payroll.deductionTotal) }}</span>
      </div>

      <p v-if="payroll.approveRemark" class="pay-card__remark">审核意见：{{ payroll.approveRemark }}</p>
      <p v-else-if="payroll.objectionReason" class="pay-card__remark" role="alert">
        员工异议：{{ payroll.objectionReason }}
      </p>
    </template>
  </div>
</template>

<style scoped>
.sk-payroll {
  height: 120px;
}

.pay-card__head {
  display: flex;
  gap: var(--sp-2);
  align-items: flex-start;
  justify-content: space-between;
}

.pay-card__id {
  min-width: 0;
}

.pay-card__month {
  margin: 0;
  font-size: var(--fs-body-strong);
  font-weight: var(--fw-medium);
  line-height: var(--lh-body);
  color: var(--text-1);
}

.pay-card__net {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding-top: var(--sp-3);
  margin-top: var(--sp-3);
  border-top: 1px solid var(--border-line);
}

.pay-card__amount {
  font-size: var(--fs-num-lg-staff);
  font-weight: var(--fw-semibold);
  line-height: var(--lh-num-lg);
  color: var(--text-1);
}

/* 明细去掉 van-cell 的左右内边距与最后一行分割线，避免卡片内出现双重缩进 */
.pay-card__items {
  margin-top: var(--sp-2);

  --van-cell-horizontal-padding: 0;
}

.pay-card__detail {
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

.pay-card__minus {
  color: var(--color-danger);
}

/* 展开/收起是次要动作，视觉降级为文字按钮但保留 44px 热区 */
.pay-card__more {
  display: block;
  width: 100%;
  min-height: 44px;
  font-size: var(--fs-caption);
  color: var(--color-primary);
  background: none;
  border: none;
}

.pay-card__foot {
  display: flex;
  gap: var(--sp-4);
  justify-content: flex-end;
}

.pay-card__remark {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-2);
}
</style>
