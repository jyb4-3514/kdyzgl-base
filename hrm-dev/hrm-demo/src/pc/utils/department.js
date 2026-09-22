/**
 * 部门树拍平为下拉选项（只保留 id / deptName）
 *
 * 为什么在这一层：人事 / 入离职 / 财务三页都只要「按某个部门过滤」的一级下拉，不需要层级选择器，
 * 同一段递归此前在三处各写了一遍（financeMeta 一份 + 人事/入离职各一份内联）。此处是唯一实现。
 *
 * @param nodes 节点形如 { id, deptName, children? }；接口可能回 null，故空值按空数组处理
 */
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
