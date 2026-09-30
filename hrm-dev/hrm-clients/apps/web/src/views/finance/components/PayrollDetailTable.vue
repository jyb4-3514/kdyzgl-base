<script setup>
import { computed } from 'vue'
import { PAYROLL_BILL_TYPE, PAYROLL_STATUS } from '@kdyzgl/shared/constants/dict.js'
import StateBlock from '../../../components/StateBlock.vue'
import StatusTag from '../../../components/StatusTag.vue'

/**
 * 工资单列表（C4 Organism，B9.3 Tab1）
 *
 * 展开行放「逐项明细」：工资单的争议基本都出在某一项的算法上，列表里能直接展开看到
 * 「项目名 / 计算式 / 金额」，比先点详情再找人快得多。展开控件用 el-table 原生 type="expand"，
 * Element 已在该 <button> 上输出 aria-expanded 与 aria-label，不再自绘重复控件。
 *
 * 行内可执行动作不由前端判断状态，而是取服务端下发的 row.actions（口径唯一真源，避免两边漂移）。
 */
const props = defineProps({
  list: { type: Array, default: () => [] },
  total: { type: Number, default: 0 },
  loading: { type: Boolean, default: false },
  error: { type: Boolean, default: false },
  pageNum: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 },
  canWrite: { type: Boolean, default: false }
})

const emit = defineEmits(['retry', 'generate', 'open', 'action', 'page-change', 'size-change'])

/** 服务端动作 → 行内按钮文案；同一行只展示第一个可执行的主操作，其余动作留在详情抽屉里 */
const ACTION_TEXT = { submit: '提交审核', approve: '审核', publish: '发布' }
const primaryActionOf = (row) => (row.actions || []).find((action) => ACTION_TEXT[action]) || ''

const emptyText = computed(() => (props.total ? '当前筛选下没有工资单' : '本月尚未生成工资单'))
</script>

<template>
  <div class="payroll-table">
    <StateBlock v-if="error" variant="error" title="工资单加载失败" @action="emit('retry')" />

    <StateBlock
      v-else-if="!loading && !list.length"
      variant="empty"
      :title="emptyText"
      description="生成前请确认当月考勤与 KPI 已完成，避免绩效与出勤按 0 计算"
      :action-text="canWrite ? '生成工资单' : ''"
      @action="emit('generate')"
    />

    <template v-else>
      <el-table v-loading="loading" :data="list" row-key="id" class="payroll-table__grid">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="payroll-table__items">
              <div v-for="item in row.items" :key="item.key" class="payroll-table__item">
                <span class="payroll-table__item-name">{{ item.name }}</span>
                <span class="payroll-table__item-source">{{ item.sourceLabel }}</span>
                <span class="payroll-table__item-detail">{{ item.detail }}</span>
                <span class="payroll-table__item-amount" :class="{ 'is-minus': item.type === 'DEDUCTION' }">
                  {{ item.type === 'DEDUCTION' ? '-' : '' }}{{ Math.abs(Number(item.amount)) }}
                </span>
              </div>
              <div class="payroll-table__item payroll-table__item--total">
                <span class="payroll-table__item-name">应发 {{ row.grossAmount }} · 扣款 {{ row.deductionTotal }}</span>
                <span class="payroll-table__item-amount">实发 {{ row.netAmount }}</span>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="payrollNo" label="单号" min-width="164" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button link type="primary" @click="emit('open', row)">{{ row.payrollNo }}</el-button>
          </template>
        </el-table-column>

        <el-table-column prop="month" label="月份" width="92" />
        <el-table-column prop="employeeName" label="员工" min-width="100" show-overflow-tooltip />
        <el-table-column prop="stationName" label="驿站" min-width="110" show-overflow-tooltip />

        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <StatusTag :dict="PAYROLL_BILL_TYPE" :value="row.billType" variant="outline" />
          </template>
        </el-table-column>

        <el-table-column label="应发" width="96" align="right">
          <template #default="{ row }">{{ row.grossAmount }}</template>
        </el-table-column>
        <el-table-column label="扣款" width="96" align="right">
          <template #default="{ row }">{{ row.deductionTotal }}</template>
        </el-table-column>
        <el-table-column label="实发" width="104" align="right">
          <template #default="{ row }">
            <span class="payroll-table__net">{{ row.netAmount }}</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="104">
          <template #default="{ row }">
            <StatusTag
              :dict="PAYROLL_STATUS"
              :value="row.status"
              :variant="(PAYROLL_STATUS[row.status] || {}).variant"
            />
          </template>
        </el-table-column>

        <el-table-column prop="createTime" label="生成时间" width="164" />

        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="emit('open', row)">详情</el-button>
            <el-button
              v-if="canWrite && primaryActionOf(row)"
              link
              type="primary"
              @click="emit('action', { action: primaryActionOf(row), row })"
            >
              {{ ACTION_TEXT[primaryActionOf(row)] }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          :current-page="pageNum"
          :page-size="pageSize"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @size-change="emit('size-change', $event)"
          @current-change="emit('page-change', $event)"
        />
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss">
.payroll-table {
  &__grid {
    width: 100%;
  }

  &__items {
    padding: var(--sp-3) var(--sp-4);
    background-color: var(--surface-sub);
  }

  &__item {
    display: grid;
    grid-template-columns: 140px 110px 1fr 110px;
    gap: var(--sp-2);
    align-items: baseline;
    padding: var(--sp-1) 0;
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    border-bottom: 1px dashed var(--border-line);

    &:last-child {
      border-bottom: none;
    }

    &--total {
      grid-template-columns: 1fr 110px;
      margin-top: var(--sp-1);
      font-weight: var(--fw-semibold);
      color: var(--text-1);
    }
  }

  &__item-name {
    color: var(--text-2);
  }

  &__item-source {
    color: var(--text-3);
  }

  &__item-detail {
    color: var(--text-3);
  }

  &__item-amount {
    text-align: right;
    color: var(--text-1);
    font-variant-numeric: tabular-nums;

    // 负数不只靠颜色：同时带 - 号（SC 1.4.1）
    &.is-minus {
      color: var(--state-danger-fg);
    }
  }

  &__net {
    font-weight: var(--fw-semibold);
    color: var(--text-1);
  }
}
</style>
