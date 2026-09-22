<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { showSuccessToast, showToast } from 'vant'
import ActionBar from '../../components/ActionBar.vue'
import PageNav from '../../components/PageNav.vue'
import StatusTag from '../../components/StatusTag.vue'
import { getParcels, pickupParcel } from '../../api/parcel.js'
import { PARCEL_STATUS } from '@/shared/constants/dict.js'
import { relativeTime } from '../../utils/format.js'

/**
 * S4 取件核销（闭环：校验运单号 → 展示包裹信息 → 确认取件 → Toast 反馈）
 * 本站校验与状态校验都在 Mock 层强制完成（7001 不存在 / 7002 状态不允许 / 7003 已被他人取件），
 * 前端只保留「查询结果为空」这一体验提示，不复制一套业务校验规则。
 *
 * 一线效率相关（本轮重点）：进入即聚焦输入框、回车即查、确认按钮固定在底部 ActionBar、
 * 演示运单号热区撑到 44px、非在库待取时用页内警示条替代「3 秒后消失的 Toast」。
 */
const waybillNo = ref('')
const parcel = ref(null)
const querying = ref(false)
const submitting = ref(false)
const notFound = ref(false)
const inputRef = ref(null)
/** 本次演示已核销的包裹：驿站现场需要一眼看到「刚做的操作」 */
const recent = ref([])

/** 演示运单号热区：仅 Mock 态从剧本读取；关闭后热区不显示，生产构建静态剔除 demo/mock 依赖 */
const demoWaybill = ref('')
if (import.meta.env.VITE_MOCK_ENABLED === 'true') {
  import('@/demo/scenario.js').then(({ readScenario }) => {
    const scenario = readScenario()
    demoWaybill.value = (scenario && scenario.waybillNo) || ''
  })
}

const canPickup = computed(() => parcel.value && parcel.value.status === 1)

/** 页内警示条：Toast 会消失，用户回看时容易误解当前状态（S4） */
const alertText = computed(() => {
  if (notFound.value) return '未找到该运单号的包裹，请核对运单号后重试'
  if (parcel.value && parcel.value.status !== 1) return guardText(parcel.value)
  return ''
})

function guardText(item) {
  if (item.status === 2) return '该包裹已取件，无需重复核销'
  if (item.status === 3) return '该包裹为异常件，请先处理异常后再核销'
  if (item.status === 4) return '该包裹已退回，不能核销'
  return '该包裹尚未入库上架，不能核销'
}

async function onQuery() {
  const no = waybillNo.value.trim()
  if (!no) {
    showToast('请输入或扫描运单号')
    return
  }
  querying.value = true
  parcel.value = null
  notFound.value = false
  try {
    const page = await getParcels({ waybillNo: no, pageNum: 1, pageSize: 1 })
    if (!page.total) {
      notFound.value = true
      return
    }
    parcel.value = page.list[0]
  } catch (e) {
    // 网络/业务错误已由 http 层提示，页内不再重复文案
  } finally {
    querying.value = false
  }
}

async function onConfirm() {
  if (!canPickup.value || submitting.value) return
  submitting.value = true
  try {
    const vo = await pickupParcel(parcel.value.id)
    parcel.value = vo
    recent.value.unshift({ waybillNo: vo.waybillNo, pickupTime: vo.pickupTime })
    waybillNo.value = ''
    showSuccessToast('取件成功，包裹状态已更新为已取件')
    // 连续核销是常态：清空后把焦点交回输入框，省掉一次点击
    await nextTick()
    focusInput()
  } catch (e) {
    // 7002/7003 等状态冲突：重新查一次拿到最新状态，避免用户对着旧数据反复点
    onQuery()
  } finally {
    submitting.value = false
  }
}

/** 扫码占位：真实能力由安卓壳提供，桥接约定（demo-design.md 8.2）当前没有扫码方法，故不伪造调用 */
function onScan() {
  // TODO(扩展): 壳侧新增 HrmBridge.scanCode() 后，在此调用并把结果写入 waybillNo
  showToast('扫码能力由安卓壳提供，浏览器演示请手输或点「填入演示运单号」')
}

function fillDemoWaybill() {
  if (!demoWaybill.value) {
    showToast('演示运单号未预置，请回入口页点「重置演示数据」')
    return
  }
  waybillNo.value = demoWaybill.value
  onQuery()
}

/** 壳内软键盘常需显式调用 focus；浏览器下 autofocus 已足够，这里只做兜底 */
function focusInput() {
  const input = inputRef.value && inputRef.value.$el && inputRef.value.$el.querySelector('input')
  if (input && typeof input.focus === 'function') input.focus()
}

onMounted(() => {
  nextTick(focusInput)
})
</script>

<template>
  <div class="pickup-page">
    <PageNav title="取件核销" />
    <div class="page" :class="canPickup ? 'page--bar' : 'page--loose'">
      <div class="card">
        <van-field
          ref="inputRef"
          v-model="waybillNo"
          label="运单号"
          placeholder="请输入或扫描运单号"
          clearable
          autofocus
          enterkeyhint="search"
          @keyup.enter="onQuery"
        />
        <div class="pickup__actions">
          <van-button class="pickup__query" type="primary" :loading="querying" @click="onQuery">查询包裹</van-button>
          <van-button class="pickup__scan" plain type="primary" icon="scan" @click="onScan">扫码</van-button>
        </div>
        <button v-if="demoWaybill" type="button" class="pickup__demo" @click="fillDemoWaybill">
          <van-icon name="gift-o" aria-hidden="true" /> 填入演示运单号 {{ demoWaybill }}
        </button>
      </div>

      <!-- 未查询：引导态（不给「假空态」，明确告诉用户下一步做什么） -->
      <div v-if="!parcel && !notFound && !querying" class="pickup__guide">
        <van-icon name="scan" class="pickup__guide-icon" aria-hidden="true" />
        <p class="pickup__guide-text">输入运单号或扫码开始</p>
        <p class="pickup__guide-hint">核销后包裹状态变更为「已取件」，取件员工与时间同时留痕</p>
      </div>

      <div v-if="alertText" class="pickup__alert" role="status">
        <van-icon name="warning-o" aria-hidden="true" /> {{ alertText }}
      </div>

      <template v-if="parcel">
        <div class="section-title">包裹信息</div>
        <div class="card">
          <div class="flex-between">
            <span class="waybill">{{ parcel.waybillNo }}</span>
            <StatusTag :dict="PARCEL_STATUS" :value="parcel.status" />
          </div>
          <div class="list-item__meta">
            {{ parcel.stationName }} · 货架 {{ parcel.shelfCode }} · {{ relativeTime(parcel.inboundTime) }}
          </div>
          <div class="list-item__meta">收件人 {{ parcel.receiverName }} {{ parcel.receiverPhone }}</div>
          <div class="list-item__meta">入库时间 {{ parcel.inboundTime }}</div>
        </div>
      </template>

      <template v-if="recent.length">
        <div class="section-title">
          本次演示已核销<span class="section-title__extra">{{ recent.length }} 件</span>
        </div>
        <div v-for="item in recent" :key="item.waybillNo" class="list-item">
          <div class="list-item__title">
            <span>{{ item.waybillNo }}</span>
            <van-icon name="passed" class="pickup__done" aria-label="核销成功" />
          </div>
          <div class="list-item__meta">取件时间 {{ item.pickupTime }}</div>
        </div>
      </template>

      <p class="tip">核销写入覆盖层，刷新后仍一致；演示数据可在入口页一键重置</p>
    </div>

    <!-- 确认按钮只在「可核销」时出现：状态不符由上方警示条解释，不给禁用的大按钮 -->
    <ActionBar
      :actions="canPickup ? [{ key: 'confirm', label: '确认取件', plain: false, loading: submitting }] : []"
      :submitting="submitting"
      @select="onConfirm"
    />
  </div>
</template>

<style scoped>
.card {
  margin-top: var(--sp-3);

  /* 卡片已给 16px 内边距，字段自身再缩一次会成 32px 的过度缩进 */
  --van-cell-horizontal-padding: 0px;

  /* 输入框压到 44px：10×2 + 24 */
  --van-cell-vertical-padding: 10px;
}

.pickup__actions {
  display: flex;
  gap: var(--sp-3);
  padding-top: var(--sp-3);
}

/* 主操作占 2/3：查询是高频动作，扫码是低速备用 */
.pickup__query {
  flex: 2;
  min-height: 44px;
}

.pickup__scan {
  flex: 1;
  min-height: 44px;
}

/* 演示运单号：热区撑到 44px（修 P34 的 33px） */
.pickup__demo {
  display: flex;
  gap: var(--sp-1);
  align-items: center;
  justify-content: center;
  width: 100%;
  min-height: 44px;
  margin-top: var(--sp-2);
  font-size: var(--fs-caption);
  color: var(--color-primary);
  background: var(--color-primary-surface);
  border: none;
  border-radius: var(--r-sm);
}

.pickup__guide {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: var(--sp-8) var(--sp-4);
  text-align: center;
}

.pickup__guide-icon {
  font-size: 40px;
  color: var(--text-disabled);
}

.pickup__guide-text {
  margin: var(--sp-3) 0 0;
  font-size: var(--fs-body);
  color: var(--text-3);
}

.pickup__guide-hint {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

.pickup__alert {
  display: flex;
  gap: var(--sp-1);
  align-items: center;
  padding: var(--sp-2) var(--sp-3);
  margin-top: var(--sp-3);
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--color-warning);
  background: var(--color-warning-surface);
  border-radius: var(--r-sm);
}

.waybill {
  font-size: var(--fs-h2);
  font-weight: var(--fw-semibold);
}

.pickup__done {
  font-size: 18px;
  color: var(--color-success);
}
</style>
