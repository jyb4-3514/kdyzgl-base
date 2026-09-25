import { describe, expect, it, vi } from 'vitest'
import { useListPager } from './useListPager.js'

/**
 * 分页组合的回归网
 * 重点钉住：单飞防重复请求、reload 不被单飞阻挡、过期响应不得覆盖或关闭新 loading。
 */

function deferred() {
  let resolve
  let reject
  const promise = new Promise((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

describe('useListPager · 触底分页', () => {
  it('首屏替换、第二页拼接，取满即 finished', async () => {
    const first = deferred()
    const second = deferred()
    const queue = [first.promise, second.promise]
    const pager = useListPager(() => queue.shift(), { pageSize: 2 })

    const p1 = pager.loadMore()
    first.resolve({ list: [{ id: 1 }], total: 3 })
    await p1
    expect(pager.list.value).toEqual([{ id: 1 }])
    expect(pager.finished.value).toBe(false)
    expect(pager.pageNum.value).toBe(2)

    const p2 = pager.loadMore()
    second.resolve({ list: [{ id: 2 }, { id: 3 }], total: 3 })
    await p2
    expect(pager.list.value).toEqual([{ id: 1 }, { id: 2 }, { id: 3 }])
    expect(pager.finished.value).toBe(true)
    expect(pager.loading.value).toBe(false)
    expect(pager.initialized.value).toBe(true)
  })

  it('单飞：在途时重复触底只发起一次请求', async () => {
    const first = deferred()
    const fetcher = vi.fn(() => first.promise)
    const pager = useListPager(fetcher)

    const p1 = pager.loadMore()
    const p2 = pager.loadMore()
    await p2
    expect(pager.loading.value).toBe(true)

    first.resolve({ list: [{ id: 1 }], total: 1 })
    await p1
    expect(fetcher).toHaveBeenCalledTimes(1)
    expect(pager.list.value).toEqual([{ id: 1 }])
  })

  it('取数失败：进入错误态并终止分页，不用空列表冒充「没有数据」', async () => {
    const pager = useListPager(() => Promise.reject(new Error('接口挂了')))
    await pager.loadMore()

    expect(pager.error.value).toBe('接口挂了')
    expect(pager.finished.value).toBe(true)
    expect(pager.list.value).toEqual([])
    expect(pager.initialized.value).toBe(true)
  })

  it('finished 后不再触底请求', async () => {
    const fetcher = vi.fn(() => Promise.resolve({ list: [{ id: 1 }], total: 1 }))
    const pager = useListPager(fetcher)
    await pager.loadMore()
    expect(pager.finished.value).toBe(true)

    await pager.loadMore()
    expect(fetcher).toHaveBeenCalledTimes(1)
  })
})

describe('useListPager · 筛选切换 reload', () => {
  it('不受单飞阻挡：整表替换，在途的旧触底响应被作废', async () => {
    const first = deferred()
    const second = deferred()
    const queue = [first.promise, second.promise]
    const pager = useListPager(() => queue.shift())

    const p1 = pager.loadMore()
    const p2 = pager.reload()
    second.resolve({ list: [{ id: 2 }], total: 1 })
    await p2
    expect(pager.list.value).toEqual([{ id: 2 }])

    first.resolve({ list: [{ id: 1 }], total: 1 })
    await p1
    expect(pager.list.value).toEqual([{ id: 2 }])
    expect(pager.loading.value).toBe(false)
  })

  it('过期响应不得关闭新筛选的 loading', async () => {
    const first = deferred()
    const second = deferred()
    const queue = [first.promise, second.promise]
    const pager = useListPager(() => queue.shift())

    const p1 = pager.loadMore()
    const p2 = pager.reload()
    first.resolve({ list: [{ id: 1 }], total: 2 })
    await p1
    expect(pager.loading.value).toBe(true)
    expect(pager.list.value).toEqual([])

    second.resolve({ list: [{ id: 2 }], total: 1 })
    await p2
    expect(pager.list.value).toEqual([{ id: 2 }])
    expect(pager.loading.value).toBe(false)
  })

  it('cancel 后在途响应不写状态（组件卸载场景）', async () => {
    const first = deferred()
    const pager = useListPager(() => first.promise)

    const p1 = pager.loadMore()
    pager.cancel()
    first.resolve({ list: [{ id: 1 }], total: 1 })
    await p1

    expect(pager.list.value).toEqual([])
    expect(pager.loading.value).toBe(false)
    expect(pager.refreshing.value).toBe(false)
  })
})
