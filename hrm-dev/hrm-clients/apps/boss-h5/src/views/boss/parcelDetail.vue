<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { showConfirmDialog, showSuccessToast, showToast } from 'vant'
import ActionBar from '@kdyzgl/shared/ui/ActionBar.vue'
import PageNav from '@kdyzgl/shared/ui/PageNav.vue'
import PageState from '@kdyzgl/shared/ui/PageState.vue'
import StatusTag from '@kdyzgl/shared/ui/StatusTag.vue'
import { getParcel, pickupParcel } from '../../api/parcel.js'
import { PARCEL_STATUS } from '@kdyzgl/shared/constants/dict.js'
import { useAuthStore } from '../../stores/auth.js'
import { relativeTime } from '../../utils/format.js'

/**
 * S3 包裹详情
 * 为什么操作栏固定到底部：核销入口原来排在 cell 列表之后，内容一长就要先滑到底，
 * 一线员工单手持机时这是最高频的失败路径（P31，本轮最高优先级修复）。
 * ADMIN（管理端预警下钻）只读，不出现核销入口，避免「替驿站取件」的越界操作。
 */
const route = useRoute()
const auth = useAuthStore()

const loading = ref(true)
const error = ref('')
const detail = ref(null)
const submitting = ref(false)

const isStaffRole = computed(() => !auth.isAdmin)
const canPickup = computed(() => isStaffRole.value && detail.value && detail.value.status === 1)

/** 禁用原因说明从按钮文案里挪出来：44px 高的按钮放长文案会被截断（S3） */
const actionNote = computed(() => {
  if (!detail.value || !isStaffRole.value) return ''
  if (canPickup.value) return ''
  return `当前状态不支持取件（${detail.value.status === 2 ? '已取件' : '非在库待取'}）`
})

const actions = computed(() => {
  if (!detail.value || !isStaffRole.value) return []
  return [
    {
      key: 'pickup',
      label: '取件核销',
      type: 'primary',
      plain: false,
      disabled: !canPickup.value,
      loading: submitting.value
    },
    { key: 'abnormal', label: '上报异常', type: 'danger', plain: true }
  ]
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    detail.value = await getParcel(route.params.id)
  } catch (e) {
    error.value = e.message || '包裹不存在或无权查看'
  } finally {
    loading.value = false
  }
}

async function onPickup() {
  if (submitting.value) return
  try {
    await showConfirmDialog({
      title: '确认取件',
      message: `运单号 ${detail.value.waybillNo}，确认已完成取件核销？确认后包裹状态变更为「已取件」。`
    })
  } catch (e) {
    return
  }
  submitting.value = true
  try {
    detail.value = await pickupParcel(detail.value.id)
    showSuccessToast('取件核销成功')
  } catch (e) {
    // 7002/7003 等业务错误由 http 层 Toast 提示，此处只需刷新最新状态
    load()
  } finally {
    submitting.value = false
  }
}

function onReportAbnormal() {
  // TODO(扩展): 包裹异常上报接口（api.md 与 demo-design.md 7.4 均未定义），待二期补充后改为表单弹层提交
  showToast('异常上报接口待二期定义，暂未接入')
}

function onAction(key) {
  if (key === 'pickup') onPickup()
  else onReportAbnormal()
}

onMounted(load)
</script>

<template>
  <div class="detail-page">
    <PageNav title="包裹详情" />
    <div class="page page--bar">
      <PageState
        :loading="loading"
        :error="error"
        :rows="6"
        error-hint="该包裹可能已不在本站，可返回上一页重新选择"
        @retry="load"
      >
        <div class="card summary">
          <div class="flex-between">
            <span class="waybill">{{ detail.waybillNo }}</span>
            <StatusTag :dict="PARCEL_STATUS" :value="detail.status" />
          </div>
          <div class="list-item__meta">{{ detail.stationName }} · 货架 {{ detail.shelfCode }}</div>
        </div>

        <div class="section-title">包裹信息</div>
        <van-cell-group inset>
          <van-cell title="收件人" :value="`${detail.receiverName} ${detail.receiverPhone}`" />
          <van-cell title="入库时间" :value="detail.inboundTime" />
          <van-cell title="入库时长" :value="relativeTime(detail.inboundTime)" />
          <van-cell title="取件时间" :value="detail.pickupTime || '-'" />
          <van-cell title="取件员工" :value="detail.pickupEmployeeId ? `工号 ${detail.pickupEmployeeId}` : '-'" />
          <van-cell title="同步批次" :value="detail.syncBatchNo || '-'" />
          <van-cell title="备注" :value="detail.remark || '-'" />
        </van-cell-group>

        <p v-if="!isStaffRole" class="tip">驿站精灵仅查看包裹明细；取件核销在员工端操作</p>
      </PageState>
    </div>

    <ActionBar :actions="actions" :note="actionNote" :submitting="submitting" @select="onAction" />
  </div>
</template>

<style scoped>
.summary {
  margin-top: var(--sp-3);
}

.waybill {
  font-size: var(--fs-h2);
  font-weight: var(--fw-semibold);
}
</style>
