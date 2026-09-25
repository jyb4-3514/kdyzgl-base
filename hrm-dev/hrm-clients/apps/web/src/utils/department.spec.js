import { describe, expect, it } from 'vitest'
import { flattenDeptTree } from './department.js'

/**
 * 部门树拍平的回归网（原属 financeMeta.spec，随函数迁到 utils 层）
 * 重点钉住：递归展开 children、只保留下拉需要的两个字段、空值与缺失 children 不炸。
 */
describe('flattenDeptTree · 部门树拍平', () => {
  it('递归展开 children，只保留 id 与 deptName', () => {
    const tree = [
      { id: 1, deptName: '总部', children: [{ id: 2, deptName: '城东分部', children: [] }] },
      { id: 3, deptName: '城西分部' }
    ]
    expect(flattenDeptTree(tree)).toEqual([
      { id: 1, deptName: '总部' },
      { id: 2, deptName: '城东分部' },
      { id: 3, deptName: '城西分部' }
    ])
  })

  it('空值与缺失 children 不炸', () => {
    expect(flattenDeptTree(null)).toEqual([])
    expect(flattenDeptTree([])).toEqual([])
    expect(flattenDeptTree([{ id: 1, deptName: 'A' }])).toEqual([{ id: 1, deptName: 'A' }])
  })
})
