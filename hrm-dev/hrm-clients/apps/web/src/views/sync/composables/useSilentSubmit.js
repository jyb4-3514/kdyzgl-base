import { ref } from 'vue'
import { codeMessage } from '@kdyzgl/shared/constants/errorCode'

/**
 * 静默提交（错误原文就地展示）
 *
 * 为什么需要：配置中心所有写接口都关掉了拦截器通用 toast（silent），
 * 错误必须由组件按设计 C.3「原样展示服务端 message」，不能二次改写。
 * 五处抽屉/表单都要「提交中 + 错误文案 + 错误码」，抽一份避免各写一套 try/catch。
 * 服务端只回错误码时（如导入超行数 5002 不带 message）用统一错误码文案兜底，避免只显示「操作失败」。
 */
export function useSilentSubmit() {
  const submitting = ref(false)
  const errorTip = ref('')
  const errorCode = ref(null)

  /** 执行写操作：成功返回结果，失败返回 null 并把服务端 message 原样落到 errorTip */
  async function run(task) {
    submitting.value = true
    errorTip.value = ''
    errorCode.value = null
    try {
      return await task()
    } catch (e) {
      const code = e && e.code !== undefined ? e.code : null
      errorCode.value = code
      // 拦截器在 body.message 为空时会填「操作失败」，此处视为「未给文案」，改用错误码标准文案
      const serverMessage = e && e.message && e.message !== '操作失败' ? e.message : ''
      errorTip.value = serverMessage || (code ? codeMessage(code) : '') || '操作失败，请重试'
      return null
    } finally {
      submitting.value = false
    }
  }

  function clearError() {
    errorTip.value = ''
    errorCode.value = null
  }

  return { submitting, errorTip, errorCode, run, clearError }
}
