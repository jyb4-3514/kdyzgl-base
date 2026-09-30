<script setup>
import { computed, onMounted, ref } from 'vue'
import PageNav from '@kdyzgl/shared/ui/PageNav.vue'
import PageState from '@kdyzgl/shared/ui/PageState.vue'
import TodoGroup from '@kdyzgl/shared/ui/TodoGroup.vue'
import { useTodoStore } from '@/stores/todo.js'
import { clockText, numberText } from '@/utils/format.js'

/**
 * 审批中心（管理能力扩展 ⑥；v1.1 体验优化）· boss-management-ui-design.md ⑧ / ⑬.3
 *
 * 承载方案：分组列表（单滚动页），不做 Tab —— 与消息页「待办」子视图同构，直接复用 TodoGroup 与
 * stores/todo.js，零新组件、零新接口。本页 = 该快照按 key 白名单筛出的子集（**排除工单 orders**），
 * 与消息页待办（含工单）同源但视图不同；两者数字不同属预期，页脚 tip 已说明。
 *
 * 组顺序 = 处理紧迫度（**固定，不做动态排序**，设计 O3）：补卡 → 请假 → 入离职 → 工资单审核 → 工资单异议。
 * 角色白名单不在此复制：configs 已按 canAccess 过滤，本页只按 key 再筛一次。
 *
 * 红线（设计 8.10 / O12）：页内**不得出现通过 / 驳回 / 同意**类动作——审批必须进业务页详情后完成，防误批；
 * 可点元素仅「导航 / 筛选 / 刷新 / 重试」。
 */
const todo = useTodoStore()

/**
 * 分类筛选（设计 O2）：'all' + 5 类，单选，本地过滤（不新增请求）。
 * 顺序即业务紧迫度，与分组顺序一致，避免「chips 顺序」与「组顺序」两套心智。
 */
const FILTERS = [
  { key: 'all', label: '全部' },
  { key: 'makeups', label: '补卡' },
  { key: 'leaves', label: '请假' },
  { key: 'flows', label: '入离职' },
  { key: 'payrolls', label: '工资单审核' },
  { key: 'payrollObjections', label: '工资单异议' }
]

/**
 * 筛选态持久化（设计 8.11）：写入 sessionStorage，返回审批中心时筛选不丢。
 * 为什么不用 route.query：避免中间态进入地址栏（可被误分享 / 误收藏），且本页无深链需求。
 */
const FILTER_KEY = 'boss.approval.filter'
function readFilter() {
  try {
    const value = sessionStorage.getItem(FILTER_KEY)
    return FILTERS.some((item) => item.key === value) ? value : 'all'
  } catch {
    // 隐私模式下 sessionStorage 可能不可用：降级为「全部」，不影响本次使用
    return 'all'
  }
}
const activeType = ref(readFilter())

function selectType(key) {
  activeType.value = key
  try {
    sessionStorage.setItem(FILTER_KEY, key)
  } catch {
    // 存不了不影响本次筛选，仅返回后不记忆
  }
}

/** 更新时间行：本次 refresh 完成时刻（页面本地 ref，不进 store，设计 8.5） */
const updatedText = ref('更新中…')
/** 下拉刷新受控值：van-pull-refresh 拉起时置 true，收尾须复位 */
const refreshing = ref(false)
/** 本次刷新是否进行中：只驱动右上按钮的可见反馈（「刷新中…」），不做禁用以免请求挂起时锁死入口 */
const reloading = ref(false)

/** 按白名单 key 取出组（保持固定顺序）；缺失的组（未取到 / 无权限）直接跳过 */
const relevantGroups = computed(() =>
  todo.approvalKeys.map((key) => todo.groups.find((item) => item.key === key)).filter(Boolean)
)

/** 分类计数：'all' = approvalTotal；单类 = 该组自身 total。null = 未知（显示 `—`，绝不用 0 冒充） */
const countOf = (key) => (key === 'all' ? todo.approvalTotal : (todo.counts[key] ?? null))

/** 总数行文案：未知时「待处理项获取中」而不是 0（不与消息页总数形成假一致） */
const totalText = computed(() =>
  todo.approvalTotal == null ? '待处理项获取中' : `共 ${numberText(todo.approvalTotal)} 项待处理`
)

/** 当前筛选命中的组：全部 = 5 组；单类 = 该组（不存在则空数组） */
const scopedGroups = computed(() =>
  activeType.value === 'all' ? relevantGroups.value : relevantGroups.value.filter((item) => item.key === activeType.value)
)

/** 空组不渲染（沿用 TodoGroup 既有口径）：total === 0 跳过；total === null（取数失败）保留以显示组内错误 */
const visibleGroups = computed(() => scopedGroups.value.filter((item) => item.total !== 0))

const pageLoading = computed(() => todo.loading && !todo.groups.length)
const pageError = computed(() => (!todo.known && todo.groups.length > 0 ? '待办加载失败' : ''))
/** 整页空：5 组均为确定的 0（与「筛选态空」文案严格不同，设计 8.7 / O8） */
const pageEmpty = computed(
  () => !pageError.value && relevantGroups.value.length > 0 && relevantGroups.value.every((item) => item.total === 0)
)
/** 筛选态空的类名：选中某类且该类确定为 0（或该组不存在）；'all' 恒为空串 */
const filterEmptyLabel = computed(() => {
  if (activeType.value === 'all') return ''
  const label = FILTERS.find((item) => item.key === activeType.value).label
  const group = scopedGroups.value[0]
  return !group || group.total === 0 ? label : ''
})

async function reload() {
  // 防重入：连点右上按钮 / 按钮与下拉同时触发时不叠加请求（旧实现无守卫，重复请求会互相覆盖 updatedText）
  if (reloading.value) return
  reloading.value = true
  try {
    await todo.refresh()
    updatedText.value = `更新于 ${clockText()}`
  } finally {
    reloading.value = false
  }
}

function onRefresh() {
  return reload()
}

/** 下拉刷新：与右上刷新同源同效（设计 O4 / E3） */
async function onPullRefresh() {
  try {
    await reload()
  } finally {
    refreshing.value = false
  }
}

onMounted(onRefresh)

/** chips 读屏名带计数（设计 O11）：未知播报「待处理数未知」，不念 `—` */
function chipLabel(item) {
  const count = countOf(item.key)
  const tail = count == null ? '待处理数未知' : `${count} 条待处理`
  return item.key === 'all' ? `全部，${tail}` : `${item.label}，${tail}`
}
</script>

<template>
  <div class="approval-page">
    <PageNav title="审批中心">
      <template #right>
        <!-- 刷新中给可见反馈：旧实现无任何按钮级状态，且唯一反馈「更新于 HH:mm」在页顶，
             用户停在页尾时点了看不到变化 → 被判为「刷新键没反应」（现已能正常上滑回页顶） -->
        <button
          type="button"
          class="nav-action"
          :aria-busy="reloading"
          @click="onRefresh"
        >{{ reloading ? '刷新中…' : '刷新' }}</button>
      </template>
    </PageNav>

    <div class="page page--loose">
      <!-- 汇总筛选条（O1/O2，页内实现不新增具名组件）：总数替代 v1.0 的 notice-bar，仍是页内唯一总数 -->
      <section class="summary" aria-label="审批汇总与筛选">
        <!-- aria-live 仅挂总数一处：刷新后计数变化被播报，避免 chips 同时播报造成噪声（O11） -->
        <p class="summary__total" aria-live="polite">{{ totalText }}</p>
        <div class="summary__chips" role="group" aria-label="按审批类型筛选">
          <button
            v-for="item in FILTERS"
            :key="item.key"
            type="button"
            class="fchip"
            :class="{ 'fchip--active': activeType === item.key }"
            :aria-pressed="activeType === item.key"
            :aria-label="chipLabel(item)"
            @click="selectType(item.key)"
          >
            {{ item.label }}
            <span
              class="summary__count"
              :class="{ 'summary__count--unknown': countOf(item.key) == null }"
            >{{ countOf(item.key) == null ? '—' : countOf(item.key) }}</span>
          </button>
        </div>
        <p class="summary__time">{{ updatedText }}</p>
      </section>

      <PageState
        :loading="pageLoading"
        :error="pageError"
        :rows="5"
        :empty="pageEmpty && !filterEmptyLabel"
        empty-text="当前没有待审批事项"
        error-hint="请检查网络后重试，若持续失败请联系管理员"
        @retry="onRefresh"
      >
        <template #empty-action>
          <p class="tip">有新申请时会出现在这里，也会推送通知</p>
        </template>

        <!-- 下拉刷新包裹分组区（O4）；刷新期间不闪骨架：pageLoading 有快照即为 false（E3） -->
        <van-pull-refresh v-model="refreshing" class="approval-pull" @refresh="onPullRefresh">
          <!-- 筛选态差异化空态（O8）：与整页空文案不同，并给回「全部」的出路 -->
          <div v-if="filterEmptyLabel" class="filter-empty">
            <p class="filter-empty__text">暂无待处理的{{ filterEmptyLabel }}</p>
            <button type="button" class="filter-empty__action" @click="selectType('all')">查看全部待办</button>
          </div>

          <TodoGroup
            v-for="group in visibleGroups"
            :key="group.key"
            :title="group.title"
            :to="group.to"
            :total="group.total"
            :rows="group.rows"
            :error="group.error"
            @retry="onRefresh"
          >
            <!-- 「员工注册」来源标识（设计 ⑧.4）：source=SELF_REGISTER 才渲染，其它来源/缺字段一律不渲染 -->
            <template #row-badge="{ row }">
              <span v-if="row.source === 'SELF_REGISTER'" class="reg-chip">注册</span>
            </template>
          </TodoGroup>

          <!-- 不做加载更多（O5）：每组仅前 3 行，明细归业务页 -->
          <p class="tip">每组仅显示前 3 条，查看全部进对应业务页。</p>
        </van-pull-refresh>
      </PageState>

      <p class="tip">审批中心不含工单；工单待办请见首页「工单管理」。消息页「待办」含工单，两者数字不同属正常。</p>
    </div>
  </div>
</template>

<style scoped>
.nav-action {
  min-height: 44px;
  padding: 0 var(--sp-1);
  font-size: var(--fs-body);
  color: var(--color-primary);
  background: none;
  border: none;
}

/* 刷新中：文案「刷新中…」+ 降一档到 --text-3，双通道表达进行中（不新增色值） */
.nav-action[aria-busy='true'] {
  color: var(--text-3);
}

/* 汇总筛选条：与分组卡片同宽同位，靠卡片底与分组区分层级 */
.summary {
  padding: var(--sp-3) var(--sp-4);
  background: var(--surface-card);
  border-radius: var(--r-lg);
  box-shadow: var(--e1);
}

.summary__total {
  margin: 0;
  font-size: var(--fs-h3);
  font-weight: var(--fw-semibold);
  color: var(--color-primary);
}

.summary__chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2);
  margin-top: var(--sp-2);
}

.summary__count {
  margin-left: var(--sp-1);
  font-variant-numeric: tabular-nums;
}

/* 计数未知（—）降一档到 --text-3，与 .fchip 未选态的 --text-2 区分（设计 8.12） */
.summary__count--unknown {
  color: var(--text-3);
}

.summary__time {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-caption);
  color: var(--text-3);
}

/* 下拉手势区给最小高度，空组时不至于无可拉区域 */
.approval-pull {
  min-height: 120px;
}

.filter-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-height: 160px;
  padding: var(--sp-10) var(--sp-4);
  text-align: center;
}

.filter-empty__text {
  margin: 0;
  font-size: var(--fs-body);
  line-height: var(--lh-body);
  color: var(--text-3);
}

/* 次要控件：描边式，与 PageState 重试同口径；高度 44 满足触控 */
.filter-empty__action {
  min-height: var(--touch-min);
  padding: 0 var(--sp-5);
  margin-top: var(--sp-4);
  font-size: var(--fs-body);
  color: var(--color-primary);
  background: var(--surface-card);
  border: 1px solid var(--color-primary-icon);
  border-radius: var(--r-full);
}

/* 「注册」来源微胶囊（设计 ⑧.4）：自绘（尺寸对齐 MiniChip），配色取既有 info 语义色（实算对比度 6.61:1）。
 * 为什么不复用 MiniChip：其 tone 走 --state-* 六态族，无 info 态；为一次性标识扩共享组件不划算。 */
.reg-chip {
  display: inline-flex;
  flex: none;
  align-items: center;
  height: var(--tag-h);
  padding: 0 var(--tag-pad-x);
  font-size: var(--fs-micro);
  line-height: 1;
  color: var(--color-info-text);
  white-space: nowrap;
  background: var(--color-info-surface);
  border-radius: var(--r-full);
}
</style>
