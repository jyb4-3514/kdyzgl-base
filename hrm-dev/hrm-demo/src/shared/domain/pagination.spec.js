import { describe, expect, it } from 'vitest'
import { paginate } from './pagination.js'

const list = [1, 2, 3, 4, 5]

describe('paginate', () => {
  it('按页切分且 total 恒为全量条数', () => {
    expect(paginate(list, 1, 2)).toEqual({ total: 5, pageNum: 1, pageSize: 2, list: [1, 2] })
    expect(paginate(list, 3, 2)).toEqual({ total: 5, pageNum: 3, pageSize: 2, list: [5] })
  })

  it('越界页返回空列表但保留 total（与后端 LIMIT 行为一致）', () => {
    expect(paginate(list, 4, 2)).toEqual({ total: 5, pageNum: 4, pageSize: 2, list: [] })
  })

  it('pageNum 非法时回落 1', () => {
    expect(paginate(list, 0, 2).pageNum).toBe(1)
    expect(paginate(list, 'abc', 2).pageNum).toBe(1)
    expect(paginate(list, undefined, 2).pageNum).toBe(1)
  })

  it('pageSize 默认 10、下限 1、上限 100', () => {
    expect(paginate(list, 1, undefined).pageSize).toBe(10)
    expect(paginate(list, 1, 0).pageSize).toBe(10)
    expect(paginate(list, 1, -5).pageSize).toBe(1)
    expect(paginate(list, 1, 999).pageSize).toBe(100)
  })

  it('空列表返回空页', () => {
    expect(paginate([], 1, 10).list).toEqual([])
    expect(paginate([], 1, 10).total).toBe(0)
  })
})
