import { showConfirmDialog } from 'vant'

/**
 * N-06 危险/不可逆操作二次确认（老板端专属，函数式）
 *
 * 为什么收口：老板端 12 个文件、63 处直接调用 Vant 反馈，确认文案各写一套，
 * 「不可逆」这件事经常只写在按钮上而不写在正文里，用户点完才发现回不去。
 * 这里把四要素变成必填契约，缺任一项直接抛错（开发期暴露，不静默降级成「确定/取消」）。
 *
 * 使用示例：
 *   if (!(await bossConfirm({
 *     action: '发布工资单',
 *     target: `${month} · ${stationText}`,
 *     impact: `${approvedCount} 名员工将可见并需确认`,
 *     irreversible: true
 *   }))) return
 *
 * TODO(扩展): 需要「不再提醒」或审计留痕时再扩展 options，不在本轮预置
 */
export function bossConfirm({ action, target, impact, irreversible = false, confirmText } = {}) {
  if (!action || !target || !impact) {
    throw new Error('bossConfirm 缺少必填要素：action（动词短语）/ target（作用对象）/ impact（影响面）')
  }

  const message = `作用对象：${target}。影响：${impact}。${irreversible ? '该操作不可撤销，请确认后再继续。' : ''}`

  return showConfirmDialog({
    title: action,
    message,
    // 按钮用具体动词而不是「确定」，让用户在点之前就知道会发生什么
    confirmButtonText: confirmText || `确认${action}`,
    cancelButtonText: '再想想'
  }).then(
    () => true,
    () => false
  )
}
