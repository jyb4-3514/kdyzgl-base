<script setup>
import { computed } from 'vue'
import { CHECK_TYPE, dictLabel } from '@/shared/constants/dict.js'
import { MAKEUP_REASON_MAX } from '../model/attendanceUi.js'

/**
 * 补卡表单弹层
 *
 * 日期 / 时段 / 卡类型由接口数据带齐（员工不必手选位置，少一次选错的机会），员工只填理由。
 * 提交失败在弹层内 role="alert" 渲染：弹层还开着却在背后飘一个 Toast 会被完全忽略。
 * 理由 2-200 字：下限由 disabled 拦截、上限由 maxlength 截断，两者共用 model 里的同一组常量。
 */
const props = defineProps({
  show: { type: Boolean, default: false },
  reason: { type: String, default: '' },
  /** { workDate, periodName, checkType }，全部来自 /attendance/status 与所点槽位 */
  target: { type: Object, default: () => ({ workDate: '', periodName: '', checkType: 'ON' }) },
  error: { type: String, default: '' },
  submitting: { type: Boolean, default: false },
  canSubmit: { type: Boolean, default: false }
})

const emit = defineEmits(['update:show', 'update:reason', 'submit'])

const typeLabel = computed(() => dictLabel(CHECK_TYPE, props.target.checkType))
</script>

<template>
  <van-popup :show="show" round position="bottom" safe-area-inset-bottom @update:show="emit('update:show', $event)">
    <div class="makeup-pop">
      <div class="makeup-pop__title">申请补卡</div>
      <van-cell-group :border="false">
        <van-cell title="补卡日期" :value="target.workDate" />
        <van-cell title="打卡时段" :value="target.periodName" />
        <van-cell title="卡类型" :value="typeLabel" />
      </van-cell-group>
      <div class="makeup-pop__form">
        <van-field
          :model-value="reason"
          type="textarea"
          rows="3"
          :maxlength="MAKEUP_REASON_MAX"
          show-word-limit
          label="补卡理由"
          placeholder="请说明漏卡原因（2-200 字），例如：外出取件错过下班打卡"
          @update:model-value="emit('update:reason', $event)"
        />
        <!-- 提交失败就在弹层内说清原因，员工不必关掉弹层再猜（9108 会带上查看进度的指引） -->
        <p v-if="error" class="makeup-pop__error" role="alert">{{ error }}</p>
        <p class="tip">提交后需老板审批，通过后系统自动补录该时段打卡记录</p>
      </div>
      <div class="makeup-pop__foot">
        <van-button block type="primary" :loading="submitting" :disabled="!canSubmit" @click="emit('submit')">
          提交申请
        </van-button>
      </div>
    </div>
  </van-popup>
</template>

<style scoped>
/* 上下留白 20 / 24（弹层规范），底部安全区由 popup 的 safe-area-inset-bottom 处理 */
.makeup-pop {
  padding: var(--sp-5) 0 var(--sp-6);
}

.makeup-pop__title {
  margin-bottom: var(--sp-3);
  font-size: var(--fs-h2);
  font-weight: var(--fw-semibold);
  text-align: center;
}

.makeup-pop__form {
  padding: var(--sp-3) var(--sp-4) 0;
}

.makeup-pop__error {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--color-danger);
}

.makeup-pop__foot {
  padding: var(--sp-4) var(--sp-4) 0;
}
</style>
