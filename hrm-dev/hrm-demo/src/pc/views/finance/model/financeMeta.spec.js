import { describe, expect, it } from 'vitest'
import { currentMonth, flattenDeptTree } from './financeMeta.js'

/**
 * 财务域纯函数的回归网
 * 重点钉住：账期补零口径、部门树递归拍平（含空/缺失 children）。
 */
describe('currentMonth · 账期口径', () => {
  it('返回 YYYY-MM 且月份补零', () => {
    expect(currentMonth()).toMatch(/^\d{4}-(0[1-9]|1[0-2])$/)
  })
})

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
