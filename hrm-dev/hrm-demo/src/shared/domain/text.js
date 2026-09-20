/**
 * 复制纯文本（PC / 移动端共用同一份核心逻辑）
 *
 * 降级链路（顺序不可颠倒）：
 * 1. navigator.clipboard.writeText —— 仅安全上下文可用（https / localhost）；
 *    安卓壳的 file:// 与 http://局域网 IP 下该 API 不存在或直接抛 NotAllowedError；
 * 2. document.execCommand('copy') + 临时 textarea —— 覆盖非安全上下文与老内核；
 * 3. 两条都失败 → 返回 false，由调用方给用户可见提示（严禁静默失败）。
 *
 * execCommand 已被标记废弃，但在安卓 WebView 里仍是唯一可用通道，故保留并注释原因；
 * TODO(扩展): 壳升级到支持 Clipboard API 的 WebView 且业务全站 HTTPS 后，可只保留第 1 条通道
 *
 * @param {string} text 待复制文本
 * @returns {Promise<boolean>} 是否复制成功
 */
export async function copyText(text) {
  const value = text == null ? '' : String(text)
  if (!value) return false

  if (
    typeof window !== 'undefined' &&
    window.isSecureContext &&
    typeof navigator !== 'undefined' &&
    navigator.clipboard
  ) {
    try {
      await navigator.clipboard.writeText(value)
      return true
    } catch (e) {
      /* 权限被拒或内核不支持：落到 execCommand 兜底 */
    }
  }
  return legacyCopy(value)
}

/** 临时 textarea + execCommand 兜底：复制完立刻移除节点并还原用户的选区 */
function legacyCopy(value) {
  if (typeof document === 'undefined') return false
  const area = document.createElement('textarea')
  area.value = value
  area.setAttribute('readonly', '')
  // 移出视口而不是 display:none：display:none 的元素不可选中，execCommand 会失败
  area.style.position = 'fixed'
  area.style.top = '-9999px'
  area.style.left = '-9999px'
  document.body.appendChild(area)

  const selection = document.getSelection()
  const savedRange = selection && selection.rangeCount > 0 ? selection.getRangeAt(0) : null
  try {
    area.select()
    area.setSelectionRange(0, value.length)
    return document.execCommand('copy')
  } catch (e) {
    return false
  } finally {
    document.body.removeChild(area)
    if (savedRange && selection) {
      selection.removeAllRanges()
      selection.addRange(savedRange)
    }
  }
}
