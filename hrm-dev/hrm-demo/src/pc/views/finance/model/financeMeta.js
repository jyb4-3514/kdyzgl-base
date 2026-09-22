/**
 * 财务域展示元数据与纯函数
 *
 * 为什么收口：账期默认值原本写在页面 setup 里，属可离线验证的纯逻辑；
 * 收进 model 后既能被单测覆盖，也避免「生成工资单」等复用方各写一份账期口径。
 * 部门树拍平已迁到 utils/department.js —— 人事/入离职/财务三域共用，不再属财务私有逻辑。
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

// 部门树拍平已迁到跨域 utils；此处仅再导出以兼容既有 financeMeta.spec 回归网，新代码请直接引 utils/department.js
// TODO(扩展): 待 financeMeta.spec 的 flattenDeptTree 用例正式迁到 department.spec 后删除本行
export { flattenDeptTree } from '../../../utils/department.js'
