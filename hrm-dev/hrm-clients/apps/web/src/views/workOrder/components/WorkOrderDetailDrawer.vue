<script setup>
import { ArrowDown, Right } from '@element-plus/icons-vue'
import { WORK_ORDER_PRIORITY, WORK_ORDER_SOURCE, WORK_ORDER_STATUS, WORK_ORDER_TYPE, dictLabel } from '@kdyzgl/shared/constants/dict'
import StateBlock from '../../../components/StateBlock.vue'
import StatusTag from '../../../components/StatusTag.vue'
import SlaCountdown from '../../../components/SlaCountdown.vue'
import WorkOrderCopyButton from '../../../components/WorkOrderCopyButton.vue'
import {
  DRAWER_SIZE,
  LOG_ACTION,
  LOG_DOT,
  PRIORITY_VARIANT,
  slaFinished,
  sourceVariantOf,
  statusVariantOf
} from '../model/workOrderMeta.js'

/**
 * 工单详情抽屉（基础信息 + 工单描述 + 流转时间线 + 底部操作）
 *
 * 底部动作可见性由父层按状态机算好后以 props 注入，本组件只负责渲染与回抛意图，
 * 不自己判断流转合法性，避免与 Mock 的 TRANSITIONS 形成第二份口径。
 */
defineProps({
  modelValue: { type: Boolean, default: false },
  detail: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  // 流转提交中：只作用于底部流转按钮，与详情加载态分开，避免拉详情时按钮也转圈
  acting: { type: Boolean, default: false },
  canAssign: { type: Boolean, default: false },
  canTransfer: { type: Boolean, default: false },
  availableActions: { type: Array, default: () => [] },
  timelineEvents: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue', 'assign', 'transfer', 'transition'])
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    title="工单详情"
    :size="DRAWER_SIZE"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-loading="loading" class="drawer-body">
      <template v-if="detail">
        <el-descriptions :column="2" size="small" border>
          <el-descriptions-item label="工单号">{{ detail.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <StatusTag :dict="WORK_ORDER_STATUS" :value="detail.status" :variant="statusVariantOf(detail.status)" />
          </el-descriptions-item>
          <el-descriptions-item label="类型">{{ dictLabel(WORK_ORDER_TYPE, detail.type) }}</el-descriptions-item>
          <el-descriptions-item label="来源">
            <StatusTag
              :dict="WORK_ORDER_SOURCE"
              :value="detail.source || 'MANUAL'"
              :variant="sourceVariantOf(detail.source)"
            />
          </el-descriptions-item>
          <el-descriptions-item label="优先级">
            <StatusTag
              :dict="WORK_ORDER_PRIORITY"
              :value="detail.priority"
              :variant="PRIORITY_VARIANT[detail.priority] || 'soft'"
            />
          </el-descriptions-item>
          <el-descriptions-item label="归属驿站">{{ detail.stationName }}</el-descriptions-item>
          <el-descriptions-item label="处理人">{{ detail.assigneeName || '未指派' }}</el-descriptions-item>
          <el-descriptions-item label="上报人">{{ detail.reporterName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="关联运单">{{ detail.waybillNo || '—' }}</el-descriptions-item>
          <el-descriptions-item label="SLA 截止">{{ detail.slaDeadline }}</el-descriptions-item>
          <el-descriptions-item label="SLA 剩余">
            <SlaCountdown :deadline="detail.slaDeadline" :priority="detail.priority" :finished="slaFinished(detail.status)" />
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detail.createTime }}</el-descriptions-item>
          <el-descriptions-item label="解决时间">{{ detail.resolvedTime || '—' }}</el-descriptions-item>
        </el-descriptions>

        <div class="detail-block">
          <div class="block-title">工单描述</div>
          <p class="block-text">{{ detail.content || '无' }}</p>
        </div>

        <div class="detail-block">
          <div class="block-title">
            流转记录
            <span class="block-hint">转单留痕单列，便于追溯处理人变更</span>
          </div>
          <el-timeline v-if="timelineEvents.length">
            <el-timeline-item
              v-for="event in timelineEvents"
              :key="event.key"
              :timestamp="event.time"
              placement="top"
              :color="LOG_DOT[event.action] || 'var(--text-disabled)'"
            >
              <div class="log-line">
                <strong>{{ LOG_ACTION[event.action] || event.action }}</strong>
                <span class="log-operator">{{ event.operatorName || '系统' }}</span>
              </div>
              <!-- 转单：结构化展示「转出人 → 转入人 + 理由」，与普通流转事件在视觉上区分开 -->
              <template v-if="event.action === 'transfer'">
                <div class="transfer-line">
                  <span class="transfer-line__who">{{ event.fromName || '未指派' }}</span>
                  <el-icon class="transfer-line__arrow" aria-hidden="true"><Right /></el-icon>
                  <span class="transfer-line__who transfer-line__who--to">{{ event.toName }}</span>
                </div>
                <div class="log-content">理由：{{ event.reason }}</div>
              </template>
              <div v-else-if="event.content" class="log-content">{{ event.content }}</div>
            </el-timeline-item>
          </el-timeline>
          <StateBlock v-else variant="empty" title="暂无流转记录" />
        </div>
      </template>
      <StateBlock v-else-if="!loading" variant="empty" title="未获取到工单信息" />
    </div>

    <template #footer>
      <div class="drawer-footer">
        <!-- 次操作固定左侧、主操作（流转）固定右侧：按钮位置不随状态变化跳动，避免误点（A7-5）；
             转单属低频次操作，收进「更多」下拉 -->
        <div class="drawer-footer__secondary">
          <WorkOrderCopyButton v-if="detail" :order="detail" variant="text" />
          <el-button v-if="detail && canAssign" plain @click="emit('assign')">指派</el-button>
          <!-- 转单权限与流转同口径（canManage）：ADMIN / 本站站长 / 当前处理人 -->
          <el-dropdown v-if="detail && canTransfer" trigger="click" @command="emit('transfer')">
            <el-button>
              更多
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="transfer">转单</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        <div class="drawer-footer__primary">
          <el-button
            v-for="action in availableActions"
            :key="action.target"
            :type="action.type"
            :plain="action.plain"
            :loading="acting"
            @click="emit('transition', action.target)"
          >
            {{ action.label }}
          </el-button>
          <span v-if="detail && !availableActions.length && !canAssign && !canTransfer" class="footer-tip"
            >当前账号对该工单无可执行动作</span
          >
        </div>
      </div>
    </template>
  </el-drawer>
</template>

<style scoped lang="scss">
.drawer-body {
  min-height: 200px;
}

.detail-block {
  margin-top: var(--sp-5);

  .block-title {
    margin-bottom: var(--sp-2);
    font-size: var(--fs-h3);
    font-weight: var(--fw-semibold);
    color: var(--text-1);
  }

  .block-hint {
    margin-left: var(--sp-2);
    font-size: var(--fs-caption);
    font-weight: var(--fw-regular);
    color: var(--text-3);
  }

  .block-text {
    margin: 0;
    font-size: var(--fs-body);
    line-height: 1.7;
    color: var(--text-2);
  }

  .log-line {
    display: flex;
    align-items: center;
    gap: var(--sp-2);

    .log-operator {
      font-size: var(--fs-caption);
      color: var(--text-3);
    }
  }

  /* 转单留痕：换人链路用「A → B」表达，与普通流转事件的纯文本说明区分 */
  .transfer-line {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
    margin-top: var(--sp-1);

    &__who {
      padding: 0 var(--sp-2);
      border: 1px solid var(--state-warning-border);
      border-radius: var(--r-xs);
      background-color: var(--state-warning-bg);
      color: var(--state-warning-fg);
      font-size: var(--fs-caption);
      line-height: var(--lh-caption);

      &--to {
        border-color: var(--state-primary-border);
        background-color: var(--state-primary-bg);
        color: var(--state-primary-fg);
      }
    }

    &__arrow {
      color: var(--color-accent);
      font-size: var(--fs-caption);
    }
  }

  .log-content {
    margin-top: 2px;
    font-size: var(--fs-body);
    color: var(--text-2);
  }
}

.drawer-footer {
  display: flex;
  align-items: center;
  gap: var(--sp-2);

  /* 主操作固定右侧：按钮位置不随状态变化跳动，降低误点概率（A7-5） */
  &__secondary {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
  }

  &__primary {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
    margin-left: auto;
  }

  .footer-tip {
    font-size: var(--fs-caption);
    color: var(--text-3);
  }
}
</style>
