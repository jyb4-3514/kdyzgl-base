<script setup>
import { onMounted, onUnmounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import PageNav from '@kdyzgl/shared/ui/PageNav.vue'
import PageState from '@kdyzgl/shared/ui/PageState.vue'
import ShiftCard from './components/ShiftCard.vue'
import ClockHero from './attendance/components/ClockHero.vue'
import MakeupPopup from './attendance/components/MakeupPopup.vue'
import PeriodCard from './attendance/components/PeriodCard.vue'
import VerifyCard from './attendance/components/VerifyCard.vue'
import { useAttendanceStatus } from './attendance/composables/useAttendanceStatus.js'
import { useMakeupForm } from './attendance/composables/useMakeupForm.js'
import { useCheckIn } from '../../composables/useCheckIn.js'
import { useAuthStore } from '../../stores/auth.js'
import { periodWindowText } from '../../utils/attendance.js'
import { LOCATE_BTN_TEXT, MORE_LINKS, shiftProps } from './attendance/model/attendanceUi.js'

/** 演示辅助只在演示构建出现：生产态没有「演示辅助」入口（编译期常量，演示分支整块剔除） */
const demoEnabled = import.meta.env.VITE_MOCK_ENABLED === 'true'

/** 员工端打卡页（壳）：取数 + 四态 + 区块编排；仅认 /attendance/status 的 periods（不推演时段）。
 * 补卡唯一性 = 员工+日期+时段+卡类型；WiFi 模拟值与演示辅助均显式标注；区块 DOM 细节全在 attendance/ 下的组件里。 */
const att = reactive(useAttendanceStatus())
const checkIn = reactive(useCheckIn())
const makeup = reactive(useMakeupForm({ onSettled: () => att.load(false) }))
const auth = useAuthStore()
const router = useRouter()
const go = (to) => router.push(to)
async function handleCheck(period, checkType) {
  // 坐标与演示引导由页面（已自查过 WiFi/定位）给出，提交实现本身不留演示手段，首页与打卡页共用一份
  await checkIn.submit(period, checkType, att.rule, att.submitOptions(period, checkType))
  // 成功与校验失败都会改变今日状态（失败还落异常卡），回读保证界面与服务端一致；非 initial 分支不切骨架
  await att.load(false)
}

/** 判定结果只回给被操作的那个时段卡，避免与相邻时段的结论串台 */
function resultOf(period) {
  return checkIn.result && checkIn.result.periodIndex === period.periodIndex ? checkIn.result : null
}
onMounted(() => {
  att.load()
  att.startClock()
})
onUnmounted(att.stopClock)
</script>

<template>
  <div class="page page--loose">
    <PageNav title="打卡" />
    <PageState :loading="att.loading" :error="att.error" :rows="6" @retry="att.load">
      <!-- 未配规则属「业务未就绪」而非无数据，单独给引导，不显示打卡按钮 -->
      <PageState v-if="!att.rule" :empty="true" empty-text="该驿站尚未配置打卡规则">
        <template #empty-action><p class="tip">请联系站长或管理员在「打卡规则」中完成配置后再打卡</p></template>
      </PageState>
      <!-- 站点未配置启用班次：无打卡时间基准（后端 9113 / shiftConfigured=false），禁用打卡并引导联系管理员 -->
      <PageState v-else-if="!att.shiftConfigured" :empty="true" empty-text="该驿站尚未配置班次">
        <template #empty-action>
          <p class="tip">本站点尚未配置班次，无法确定上下班时间；请联系管理员在「排班管理」维护班次后再打卡</p>
        </template>
      </PageState>
      <template v-else>
        <ClockHero :time="att.clockText" :date-text="att.dateText" :station-name="auth.user.stationName" />
        <div class="section-title">
          <span>今日班次</span>
          <span class="section-title__extra">{{ att.hasSchedule ? '来自排班表' : '未排班 · 按规则标准工时' }}</span>
        </div>
        <div class="card"><ShiftCard v-bind="shiftProps(att.shift)" /></div>
        <div class="section-title">
          <span>打卡时段</span>
          <span class="section-title__extra tabular-nums">已完成 {{ att.doneCount }}/{{ att.requireTotal }}</span>
        </div>
        <div v-if="!att.periods.length" class="card">
          <p class="period-empty">该驿站尚未配置启用班次，暂无打卡时段；请联系管理员在「排班管理」维护班次后再打卡。</p>
        </div>
        <PeriodCard
          v-for="period in att.periodsWithState"
          :key="period.periodIndex"
          :period="period"
          :window-text="periodWindowText(period, att.rule)"
          :submitting-key="checkIn.submittingKey"
          :submitting="checkIn.submitting"
          :result="resultOf(period)"
          @check="handleCheck(period, $event)"
          @makeup="makeup.open(period, $event, att.status.workDate)"
        />
        <div class="section-title"><span>校验状态</span><span class="section-title__extra">打卡前自查</span></div>
        <!-- WiFi 卡：只展示壳侧真实 SSID；未取到如实显示「未获取到」+ 需客户端提示，不回填白名单值 -->
        <VerifyCard title="当前 WiFi" :badge="att.wifiBadge" :value="att.wifiText" :hints="att.wifiHints" />
        <VerifyCard
          title="当前定位"
          :badge="att.locateBadge"
          :value="att.positionText"
          numeric
          :hints="att.fenceHints"
          :error-text="att.locationError"
        >
          <van-button class="verify__btn" size="small" plain type="primary" :loading="att.locating" @click="att.locate">
            {{ LOCATE_BTN_TEXT }}
          </van-button>
          <!-- 演示辅助：整行可点，开关自身仍可聚焦（键盘可操作）；仅演示态渲染 -->
          <div v-if="demoEnabled" class="assist" @click="att.toggleAssist">
            <div class="assist__text">
              <p class="assist__title">演示辅助（非真实能力）</p>
              <p class="assist__hint">
                Mock 围栏坐标是虚构值，手机真实定位必然在围栏外。开启后按围栏中心坐标提交，仅用于演示打卡通过路径；关闭则提交真实定位。
              </p>
            </div>
            <van-switch
              :model-value="att.demoAssist"
              size="24px"
              aria-label="演示辅助开关：开启后按围栏中心坐标提交打卡"
              @keydown.enter.prevent="att.toggleAssist"
              @keydown.space.prevent="att.toggleAssist"
            />
          </div>
        </VerifyCard>
        <VerifyCard title="规则要求" :head-extra="att.rule.ruleName" :hints="att.ruleHints" :chips="att.ruleChips" />
        <div class="more-links">
          <button v-for="link in MORE_LINKS" :key="link.to" type="button" class="more-links__btn" @click="go(link.to)">
            {{ link.label }}
          </button>
        </div>
      </template>
    </PageState>
    <MakeupPopup
      v-model:show="makeup.show"
      v-model:reason="makeup.reason"
      v-bind="{ target: makeup.target, error: makeup.error, submitting: makeup.submitting }"
      :can-submit="makeup.canSubmit"
      @submit="makeup.submit"
    />
  </div>
</template>

<style scoped>
.period-empty {
  margin: 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

.more-links {
  display: flex;
  gap: var(--sp-3);
  margin-top: var(--sp-4);
}

.more-links__btn {
  flex: 1;
  min-height: var(--touch-min);
  font-size: var(--fs-body);
  color: var(--color-primary);
  background: var(--surface-card);
  border: 1px solid var(--color-primary-border);
  border-radius: var(--r-sm);
}
</style>
