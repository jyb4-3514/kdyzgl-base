import { computed, ref } from 'vue'
import { showSuccessToast } from 'vant'
import { applyMakeup } from '@/mobile/api/attendance.js'
import { makeupErrorHint } from '@/mobile/utils/attendance.js'
import { MAKEUP_REASON_MIN } from '../model/attendanceUi.js'

/**
 * 补卡表单（弹层）
 *
 * 为什么日期/时段/卡类型由 open() 注入而不是让员工在弹层里选：补卡的唯一性口径是
 * 「员工 + 日期 + 时段 + 卡类型」这一个槽位，入口挂在时段项上时四项定位信息天然带齐，
 * 少一次选错的机会；员工只需填理由。
 *
 * 为什么提交后无论成败都回读（onSettled）：失败多因服务端已有申请或已有卡，
 * 回读后界面与服务端对齐，弹层保留错误文案，员工能立刻看到「其实已经提过了」。
 */
export function useMakeupForm({ onSettled } = {}) {
  const show = ref(false)
  const reason = ref('')
  const error = ref('')
  const submitting = ref(false)
  const target = ref({ workDate: '', periodIndex: 0, periodName: '', checkType: 'ON' })

  /** 理由不足 2 字不可提交：与弹层 maxlength 一起构成 2-200 字的显式规则 */
  const canSubmit = computed(() => reason.value.trim().length >= MAKEUP_REASON_MIN)

  /** 打开弹层：日期取接口下发的 workDate，避免用本地日期与服务端判定错位 */
  function open(period, checkType, workDate) {
    target.value = {
      workDate,
      periodIndex: period.periodIndex,
      periodName: period.name,
      checkType
    }
    reason.value = ''
    error.value = ''
    show.value = true
  }

  function close() {
    show.value = false
  }

  async function submit() {
    const text = reason.value.trim()
    if (submitting.value || text.length < MAKEUP_REASON_MIN) return
    submitting.value = true
    error.value = ''
    try {
      await applyMakeup({
        workDate: target.value.workDate,
        periodIndex: target.value.periodIndex,
        checkType: target.value.checkType,
        reason: text
      })
      show.value = false
      showSuccessToast('补卡申请已提交，等待老板审批')
    } catch (e) {
      // 9108 最常见（本人刚申请过，或期间又正常打了卡），提示要落到「去哪儿看进度 / 无需补卡」
      error.value = makeupErrorHint(e.code, { message: e.message })
    } finally {
      if (onSettled) await onSettled()
      submitting.value = false
    }
  }

  return { show, reason, error, submitting, target, canSubmit, open, close, submit }
}
