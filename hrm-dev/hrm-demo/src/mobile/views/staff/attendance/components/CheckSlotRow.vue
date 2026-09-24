<script setup>
import { computed } from 'vue'
import { CHECK_TYPE, dictLabel } from '@/shared/constants/dict.js'
import { CHECK_ACTION_TEXT, ITEM_STATE } from '../model/attendanceUi.js'

/**
 * 打卡槽位行（单个上班/下班卡）
 *
 * 主操作永远是「补打」（能落真实卡，迟到早退如实记录）；「申请补卡」需审批，只作次入口 —— 优先关系不得改变。
 * 已打卡不再给按钮：重复打卡必被服务端 DUPLICATE_CHECK 打回，留一个点了必错的按钮只会误导（UI 规范 4.2 C6）。
 * 过点未打（overdue）与可打（todo）共用主按钮，只有 overdue 额外给补卡次入口：补打能当场落卡，优先级更高。
 */
const props = defineProps({
  periodName: { type: String, default: '' },
  checkType: { type: String, default: 'ON' },
  /** 6 态之一：{ key, text }，由 useAttendanceStatus 判定 */
  state: { type: Object, required: true },
  /** 本槽位正在提交：主按钮 loading 并标 aria-busy */
  submitting: { type: Boolean, default: false },
  /** 任一槽位提交中：全部按钮禁用，防连点重复落卡 */
  disabled: { type: Boolean, default: false }
})

const emit = defineEmits(['check', 'makeup'])

const typeLabel = computed(() => dictLabel(CHECK_TYPE, props.checkType))
/** 可补打的三态：未到时间也给按钮（点早了由服务端按 9102 给出可打时间，比留一个点不动的灰按钮更说明问题） */
const punchable = computed(() => [ITEM_STATE.WAIT, ITEM_STATE.TODO, ITEM_STATE.OVERDUE].includes(props.state.key))
const makeupAria = computed(() => `${props.periodName}${typeLabel.value}申请补卡`)
const punchAria = computed(() => `${props.periodName}${typeLabel.value}`)
</script>

<template>
  <div class="check-item">
    <div class="check-item__text">
      <span class="check-item__label">{{ typeLabel }}</span>
      <span class="check-item__state" :class="`check-item__state--${state.key}`">{{ state.text }}</span>
    </div>

    <!-- 已过期（窗口已关）：入口换成「申请补卡」——再点打卡必被 9102 打回 -->
    <van-button
      v-if="state.key === ITEM_STATE.MISSED"
      class="check-item__btn"
      :aria-label="makeupAria"
      @click="emit('makeup')"
    >
      申请补卡
    </van-button>

    <!-- 审批中：按钮保留但禁用，员工一眼看出「这一格已提过单」，而不是入口凭空消失 -->
    <van-button v-else-if="state.key === ITEM_STATE.PENDING" class="check-item__btn" disabled>审批中</van-button>

    <!-- 可打与过点未打：主操作都是补打；过点的额外给一个补卡次入口 -->
    <div v-else-if="punchable" class="check-item__ops">
      <van-button
        class="check-item__btn"
        :plain="checkType === 'OFF'"
        :loading="submitting"
        :disabled="disabled"
        :aria-busy="submitting"
        :aria-label="punchAria"
        @click="emit('check')"
      >
        {{ CHECK_ACTION_TEXT[checkType] }}
      </van-button>
      <button
        v-if="state.key === ITEM_STATE.OVERDUE"
        type="button"
        class="check-item__link"
        :aria-label="makeupAria"
        @click="emit('makeup')"
      >
        申请补卡
      </button>
    </div>
  </div>
</template>

<style scoped>
/* 行高 = 单行 48 + 8：容纳「卡类型 + 状态文案」两行而不显拥挤 */
.check-item {
  display: flex;
  gap: var(--sp-3);
  align-items: center;
  min-height: calc(var(--row-h-1) + var(--sp-2));
  border-top: 1px solid var(--border-line);
}

.check-item__text {
  flex: 1;
  min-width: 0;
}

.check-item__label {
  display: block;
  font-size: var(--fs-body);
  font-weight: var(--fw-medium);
  color: var(--text-1);
}

.check-item__state {
  display: block;
  margin-top: var(--sp-1);
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

.check-item__state--done {
  color: var(--color-success);
}

.check-item__state--todo,
.check-item__state--overdue {
  color: var(--color-warning);
}

.check-item__state--missed {
  color: var(--color-danger);
}

/* 审批中是「进行中」而非问题态，用品牌主色与红色的待补卡区分开 */
.check-item__state--pending {
  color: var(--color-primary);
}

/* 打卡主操作 ≥44×44（触控下限），min-height 压过 Vant 默认按钮高度 */
.check-item__btn {
  flex: none;
  min-width: 104px;
  min-height: var(--touch-min);
}

/* 一个槽位两档操作时纵向排列：横向并排会把「上班卡 / 已过 08:00 未打卡（19:00 前可补打）」挤成三行 */
.check-item__ops {
  display: flex;
  flex: none;
  flex-direction: column;
  gap: var(--sp-1);
  align-items: flex-end;
}

/* 次入口也是独立焦点目标，高度接触控下限，视觉上只是主按钮下的文字链接 */
.check-item__link {
  min-height: var(--touch-min);
  padding: 0 var(--sp-2);
  font-size: var(--fs-caption);
  color: var(--color-primary);
  background: none;
  border: none;
}
</style>
