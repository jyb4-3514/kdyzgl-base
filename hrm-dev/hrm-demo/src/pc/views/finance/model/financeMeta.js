/**
 * 财务域展示元数据与纯函数
 *
 * 为什么收口：账期默认值与部门树拍平原本写在页面 setup 里，属可离线验证的纯逻辑；
 * 收进 model 后既能被单测覆盖，也避免「生成工资单」等复用方各写一份账期口径。
 */

export const RULE_STATUS = {
  1: { label: '启用', type: 'success' },
  0: { label: '停用', type: 'info' }
}

/** 当前账期（YYYY-MM）：工资单按自然月生成，默认落当前月 */
export function currentMonth() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

/** 部门树拍平为下拉选项：只保留 id 与名称，递归展开 children */
export function flattenDeptTree(nodes) {
  const flat = []
  const walk = (list) => {
    ;(list || []).forEach((node) => {
      flat.push({ id: node.id, deptName: node.deptName })
      walk(node.children)
    })
  }
  walk(nodes)
  return flat
}
