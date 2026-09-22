<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { showSuccessToast } from 'vant'
import ActionBar from '../../components/ActionBar.vue'
import PageNav from '../../components/PageNav.vue'
import PageState from '../../components/PageState.vue'
import SlaTag from '../../components/SlaTag.vue'
import StatusTag from '../../components/StatusTag.vue'
import WorkOrderCopyButton from '../../components/WorkOrderCopyButton.vue'
import { assignWorkOrder, changeWorkOrderStatus, getWorkOrder, transferWorkOrder } from '../../api/workOrder.js'
import { WORK_ORDER_PRIORITY, WORK_ORDER_STATUS, WORK_ORDER_TYPE } from '@/shared/constants/dict.js'
import { useAuthStore } from '../../stores/auth.js'
import { fetchTransferTargets, transferErrorHint } from '../../utils/workorder.js'

/**
 * 工单详情（老板端与员工端共用：S7）
 *
 * 为什么不另写老板端详情页：ADMIN 与员工看的是同一份工单、同一套流转规则，差异只有「可指派」与
 * 「可跨站转单」两条权限；另起一页会让时间线、流转按钮、转单弹层各维护两份（router 里已有同类决策）。
 * 老板端入口：/boss/workorder 列表与「异常预警」，两处都跳这里。
 *
 * 本轮最高优先级交互修复（沿用）：操作按钮在固定底部 ActionBar，而不是排在内容流末尾 ——
 * 原先「接单/核销」排在信息 + 描述 + 时间线之后，一线员工必须滑到底才能操作。
 *
 * 流转规则与 Mock 一致（TRANSITIONS）：待处理→处理中/已关闭；处理中→已解决；已解决→重开/关闭；已关闭为终态。
 * 操作权限在本地只做「按钮显隐」（体验层），真正的拦截在 Mock 层（8001/8002/8003/8004）。
 *
 * 转单与指派（T19）：
 * - 转单只改处理人、不改状态，详情页的「转单留痕」与「当前处理人」始终自洽；
 * - 候选口径与 Mock 的 8004 一致：老板可跨站调人，站长与处理人只能在本站内消化（见 utils/workorder.js）。
 */
const route = useRoute()
const auth = useAuthStore()

const loading = ref(true)
const error = ref('')
const detail = ref(null)
const submitting = ref(false)
const showRemark = ref(false)
const remark = ref('')
const pendingAction = ref(null)

/** 人员弹层：指派与转单共用同一套「候选人 + 提交」骨架，只有理由字段与目标接口不同 */
const showPeople = ref(false)
const peopleMode = ref('transfer')
const people = ref([])
const peopleLoading = ref(false)
const peopleError = ref('')
const peopleSubmitting = ref(false)
const selectedId = ref(null)
const transferReason = ref('')
const submitError = ref('')

const ACTIONS = {
  accept: { label: '接单处理', target: 1, needRemark: false },
  resolve: { label: '标记已解决', target: 2, needRemark: true, placeholder: '填写处理过程与结果（必填）' },
  close: { label: '关闭工单', target: 3, needRemark: true, placeholder: '填写关闭原因（必填）' },
  reopen: { label: '重新打开', target: 1, needRemark: true, placeholder: '填写重开原因（必填）' }
}

const ACTION_LABEL = {
  create: '创建工单',
  assign: '指派',
  accept: '接单处理',
  resolve: '标记已解决',
  close: '关闭工单',
  reopen: '重新打开',
  transfer: '转单'
}

/** 时间线色点：创建/指派/接单/转单=主色，解决=成功，关闭=危险 */
const ACTION_DOT = { resolve: 'success', close: 'danger' }

/** 权限镜像：ADMIN 全量 / 本站站长 / 本人为处理人 */
const canManage = computed(() => {
  if (!detail.value) return false
  if (auth.isAdmin) return true
  if (auth.role === 'STATION_ADMIN') return detail.value.stationId === auth.stationId
  return detail.value.assigneeId === (auth.user && auth.user.id)
})

/** 指派权限镜像 Mock 的 canAssign：仅 ADMIN 或本驿站站长（普通员工不可指派） */
const canAssign = computed(() => {
  if (!detail.value) return false
  if (auth.isAdmin) return true
  return auth.role === 'STATION_ADMIN' && detail.value.stationId === auth.stationId
})

/** 可流转动作：与 Mock 的 TRANSITIONS 表逐键对齐 */
const availableActions = computed(() => {
  if (!detail.value || !canManage.value) return []
  const map = { 0: ['accept', 'close'], 1: ['resolve'], 2: ['reopen', 'close'], 3: [] }
  return map[detail.value.status] || []
})

/** ActionBar 只认数据：第 1 个是主操作，关闭类走危险描边，指派/转单排在流转动作之后（超出 2 个自动收进「更多」） */
const barActions = computed(() => {
  const list = availableActions.value.map((key) => ({
    key,
    label: ACTIONS[key].label,
    type: key === 'close' ? 'danger' : 'primary',
    plain: key === 'close'
  }))
  if (canAssign.value) list.push({ key: 'assign', label: '指派处理人', type: 'primary', plain: true })
  if (canManage.value) list.push({ key: 'transfer', label: '转单', type: 'primary', plain: true })
  return list
})

const timeline = computed(() =>
  detail.value && detail.value.handleLog ? detail.value.handleLog.slice().reverse() : []
)

/** 转单留痕（接口已倒序返回）：转出人 → 转入人 + 理由 + 时间，用于回答「这单怎么到我这儿的」 */
const transfers = computed(() => (detail.value && detail.value.transfers) || [])

/** 转单理由 2-100 字（与 Mock 的 textLen 校验同口径）；指派不需要理由 */
const peopleReady = computed(
  () => !!selectedId.value && (peopleMode.value === 'assign' || transferReason.value.trim().length >= 2)
)

async function load() {
  loading.value = true
  error.value = ''
  try {
    detail.value = await getWorkOrder(route.params.id)
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

/** 静默重拉：指派/转单后对齐服务端，不切骨架（页面已有内容，切骨架会白闪一下） */
async function reload() {
  try {
    detail.value = await getWorkOrder(route.params.id)
  } catch (e) {
    /* 重拉失败保留当前视图：写操作本身已由接口确认，不为此把整页判成错误态 */
  }
}

function onAction(key) {
  if (key === 'assign' || key === 'transfer') {
    openPeople(key)
    return
  }
  const action = ACTIONS[key]
  pendingAction.value = action
  if (action.needRemark) {
    remark.value = ''
    showRemark.value = true
    return
  }
  submit(action.target, '')
}

async function confirmRemark() {
  if (!remark.value.trim()) return
  showRemark.value = false
  await submit(pendingAction.value.target, remark.value.trim())
}

async function submit(status, text) {
  if (submitting.value) return
  submitting.value = true
  try {
    detail.value = await changeWorkOrderStatus(detail.value.id, { status, remark: text })
    showSuccessToast('工单状态已更新')
  } catch (e) {
    // 8001/8002 由 http 层提示；状态可能已被他人改变，重新拉一次对齐
    load()
  } finally {
    submitting.value = false
  }
}

/**
 * 打开人员弹层
 * 每次打开都重拉候选：工单流转期间人员可能已停用，缓存一份名单会出现「选中即被 8004 打回」。
 */
async function openPeople(mode) {
  peopleMode.value = mode
  selectedId.value = null
  transferReason.value = ''
  submitError.value = ''
  peopleError.value = ''
  people.value = []
  showPeople.value = true
  peopleLoading.value = true
  try {
    people.value = await fetchTransferTargets({
      role: auth.role,
      stationId: detail.value.stationId,
      selfId: auth.user.id
    })
    if (!people.value.length)
      peopleError.value = mode === 'assign' ? '该驿站没有其他在职员工可指派' : '该驿站没有其他在职员工可转单'
  } catch (e) {
    peopleError.value =
      e.code === 403 ? '当前身份无法获取员工名单，请联系站长代为处理' : e.message || '人员列表加载失败'
  } finally {
    peopleLoading.value = false
  }
}

async function submitPeople() {
  if (!peopleReady.value || peopleSubmitting.value) return
  peopleSubmitting.value = true
  submitError.value = ''
  try {
    if (peopleMode.value === 'assign') {
      await assignWorkOrder(detail.value.id, { assigneeId: selectedId.value })
      showSuccessToast('已指派处理人')
    } else {
      await transferWorkOrder(detail.value.id, { toEmployeeId: selectedId.value, reason: transferReason.value.trim() })
      showSuccessToast('转单成功，处理人已变更')
    }
    showPeople.value = false
  } catch (e) {
    // 8002/8003/8004 都要说清「是权限不够还是选的人不对」，笼统的「操作失败」现场无从整改
    submitError.value = transferErrorHint(e.code, {
      crossStation: auth.isAdmin,
      stationName: detail.value.stationName,
      message: e.message
    })
  } finally {
    peopleSubmitting.value = false
    await reload()
  }
}

onMounted(load)
</script>

<template>
  <div class="wo-detail">
    <PageNav title="工单详情">
      <!-- 复制入口放导航右侧：底部 ActionBar 已承载流转动作，复制不是流转语义，不该混进去 -->
      <template #right>
        <WorkOrderCopyButton v-if="detail" :order="detail" />
      </template>
    </PageNav>
    <div class="page" :class="barActions.length ? 'page--bar' : 'page--loose'">
      <PageState :loading="loading" :error="error" :rows="6" @retry="load">
        <div class="card summary">
          <div class="flex-between">
            <span class="order-no">{{ detail.orderNo }}</span>
            <StatusTag :dict="WORK_ORDER_STATUS" :value="detail.status" />
          </div>
          <div class="wo-title">{{ detail.title }}</div>
          <div class="list-item__tags">
            <StatusTag :dict="WORK_ORDER_TYPE" :value="detail.type" />
            <StatusTag :dict="WORK_ORDER_PRIORITY" :value="detail.priority" />
            <SlaTag
              :deadline="detail.slaDeadline"
              :active="detail.status === 0 || detail.status === 1"
              :priority="detail.priority"
            />
          </div>
        </div>

        <div class="section-title">工单信息</div>
        <van-cell-group inset>
          <van-cell title="归属驿站" :value="detail.stationName" />
          <van-cell title="上报人" :value="detail.reporterName" />
          <van-cell title="处理人" :value="detail.assigneeName || '未指派'" />
          <van-cell title="关联运单" :value="detail.waybillNo || '-'" />
          <van-cell title="SLA 截止" :value="detail.slaDeadline" />
          <van-cell title="创建时间" :value="detail.createTime" />
          <van-cell title="解决时间" :value="detail.resolvedTime || '-'" />
          <van-cell title="关闭时间" :value="detail.closedTime || '-'" />
        </van-cell-group>

        <div class="section-title">工单描述</div>
        <div class="card content">{{ detail.content || '（未填写描述）' }}</div>

        <div class="section-title">处理时间线</div>
        <div class="card">
          <div v-for="(log, index) in timeline" :key="index" class="timeline__item">
            <i
              class="timeline__dot"
              :class="ACTION_DOT[log.action] ? `timeline__dot--${ACTION_DOT[log.action]}` : ''"
              aria-hidden="true"
            />
            <div class="timeline__time">{{ log.time }}</div>
            <div class="timeline__text">
              <strong class="timeline__action">{{ ACTION_LABEL[log.action] || log.action }}</strong>
              <span v-if="log.operatorName" class="timeline__time"> · {{ log.operatorName }}</span>
            </div>
            <div v-if="log.content" class="timeline__text">{{ log.content }}</div>
          </div>
        </div>

        <template v-if="transfers.length">
          <div class="section-title">
            转单留痕<span class="section-title__extra">共 {{ transfers.length }} 次</span>
          </div>
          <div class="card">
            <div v-for="item in transfers" :key="item.id" class="transfer">
              <div class="transfer__head">
                <span class="transfer__chain">{{ item.fromEmployeeName || '未指派' }} → {{ item.toEmployeeName }}</span>
                <span class="transfer__time tabular-nums">{{ item.transferTime }}</span>
              </div>
              <p class="transfer__reason">理由：{{ item.reason }}</p>
              <p class="transfer__meta">操作人：{{ item.operatorName || '系统' }}</p>
            </div>
          </div>
        </template>

        <!-- 权限提示：不是处理人时按钮全隐藏，必须说明原因，否则像「功能坏了」 -->
        <p v-if="!canManage" class="tip">
          当前身份不是该工单的处理人，仅可查看（流转与转单权限：管理员 / 本站站长 / 处理人本人）
        </p>
      </PageState>
    </div>

    <!-- 固定底部操作栏：一线员工不必滑到底（修 P31） -->
    <ActionBar :actions="barActions" :submitting="submitting" @select="onAction" />

    <!-- 指派 / 转单共用的人员弹层：候选按角色收敛（老板跨站、站长与处理人限本站），与 8004 同口径 -->
    <van-popup v-model:show="showPeople" round position="bottom" safe-area-inset-bottom>
      <div class="people-pop">
        <div class="people-pop__title">{{ peopleMode === 'assign' ? '指派处理人' : '转单' }}</div>
        <p class="people-pop__sub">
          {{
            peopleMode === 'assign'
              ? `工单归属：${detail.stationName}`
              : auth.isAdmin
                ? '老板可跨驿站转单'
                : `仅可转给本站（${detail.stationName}）在职员工`
          }}
        </p>

        <div class="people-pop__list">
          <p v-if="peopleLoading" class="people-pop__hint">正在加载员工名单…</p>
          <p v-else-if="peopleError" class="people-pop__error" role="alert">{{ peopleError }}</p>
          <button
            v-for="item in people"
            :key="item.id"
            type="button"
            class="people-pop__item"
            :aria-pressed="selectedId === item.id"
            @click="selectedId = item.id"
          >
            <span class="people-pop__name">
              {{ item.name }}
              <span v-if="auth.isAdmin && item.stationName" class="people-pop__station">{{ item.stationName }}</span>
            </span>
            <van-icon v-if="selectedId === item.id" name="passed" aria-hidden="true" />
          </button>
        </div>

        <div v-if="peopleMode === 'transfer'" class="people-pop__form">
          <van-field
            v-model="transferReason"
            type="textarea"
            rows="3"
            maxlength="100"
            show-word-limit
            label="转单理由"
            placeholder="请说明转单原因（2-100 字），例如：本人休假，交由同班同事跟进"
          />
        </div>

        <p v-if="submitError" class="people-pop__error" role="alert">{{ submitError }}</p>

        <div class="people-pop__foot">
          <van-button block type="primary" :loading="peopleSubmitting" :disabled="!peopleReady" @click="submitPeople">
            {{ peopleMode === 'assign' ? '确认指派' : '确认转单' }}
          </van-button>
        </div>
      </div>
    </van-popup>

    <van-popup v-model:show="showRemark" round position="bottom" safe-area-inset-bottom>
      <div class="remark-pop">
        <div class="remark-pop__title">{{ pendingAction && pendingAction.label }}</div>
        <van-field
          v-model="remark"
          type="textarea"
          rows="3"
          maxlength="200"
          show-word-limit
          :placeholder="pendingAction && pendingAction.placeholder"
        />
        <div class="remark-pop__foot">
          <van-button block type="primary" :disabled="!remark.trim()" @click="confirmRemark">确认提交</van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.summary {
  margin-top: var(--sp-3);
}

.order-no {
  font-size: var(--fs-body-strong);
  font-weight: var(--fw-semibold);
}

.wo-title {
  margin-top: var(--sp-2);
  font-size: var(--fs-body-strong);
  font-weight: var(--fw-medium);
  line-height: var(--lh-body);
}

.content {
  font-size: var(--fs-body);
  line-height: 1.7;
  color: var(--text-2);
}

.timeline__action {
  font-size: var(--fs-body);
  font-weight: var(--fw-semibold);
  color: var(--text-1);
}

/* 操作栏页底部留白由 --page-pad-bottom 提供，末元素与操作栏之间再留 8px 呼吸 */
.page--bar {
  padding-bottom: calc(var(--page-pad-bottom) + var(--sp-2));
}

.remark-pop {
  padding: var(--sp-5) 0 var(--sp-6);
}

.remark-pop__title {
  margin-bottom: var(--sp-3);
  font-size: var(--fs-h2);
  font-weight: var(--fw-semibold);
  text-align: center;
}

.remark-pop__foot {
  padding: var(--sp-4) var(--sp-4) 0;
}

/* ==================== 转单留痕 ==================== */

.transfer + .transfer {
  padding-top: var(--sp-3);
  margin-top: var(--sp-3);
  border-top: 1px solid var(--border-line);
}

.transfer__head {
  display: flex;
  gap: var(--sp-2);
  align-items: baseline;
  justify-content: space-between;
}

.transfer__chain {
  font-size: var(--fs-body-strong);
  font-weight: var(--fw-medium);
  color: var(--text-1);
}

.transfer__time {
  flex: none;
  font-size: var(--fs-caption);
  color: var(--text-3);
}

.transfer__reason,
.transfer__meta {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-2);
}

.transfer__meta {
  color: var(--text-3);
}

/* ==================== 指派 / 转单弹层 ==================== */

.people-pop {
  padding: var(--sp-5) 0 var(--sp-6);
}

.people-pop__title {
  font-size: var(--fs-h2);
  font-weight: var(--fw-semibold);
  text-align: center;
}

.people-pop__sub {
  margin: var(--sp-1) var(--sp-4) 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
  text-align: center;
}

/* 名单超长时在弹层内滚动，不把弹层顶到屏幕外（无横向滚动） */
.people-pop__list {
  max-height: 46vh;
  margin-top: var(--sp-3);
  overflow-y: auto;
}

.people-pop__item {
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

.people-pop__item[aria-pressed='true'] {
  color: var(--color-primary);
  background: var(--color-primary-surface);
}

.people-pop__name {
  display: inline-flex;
  gap: var(--sp-2);
  align-items: baseline;
  min-width: 0;
}

.people-pop__station {
  font-size: var(--fs-caption);
  color: var(--text-3);
}

.people-pop__hint {
  margin: 0;
  padding: var(--sp-4);
  font-size: var(--fs-caption);
  color: var(--text-3);
  text-align: center;
}

.people-pop__form {
  padding: var(--sp-3) var(--sp-4) 0;
}

.people-pop__error {
  margin: var(--sp-2) var(--sp-4) 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--color-danger);
}

.people-pop__foot {
  padding: var(--sp-4) var(--sp-4) 0;
}
</style>
