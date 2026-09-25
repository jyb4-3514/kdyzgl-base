import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import { useLatestRequest } from './useLatestRequest.js'

/**
 * 竞态守卫的回归网
 * 重点钉住：乱序响应丢弃、过期响应不得关闭新 loading、作废后旧响应不得写入。
 * TODO(扩展): workorder / parcel / leaveList 等列表页接入后，各页各留一条乱序用例，防回归。
 */

/** 可控 promise，用来精确编排「谁先回、谁后回」 */
function deferred() {
  let resolve
  let reject
  const promise = new Promise((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

/**
 * 按约定用法包一层页面取数：loading 的开关写在 finally 里并按序号收尾。
 * 这样测的就是「调用方照契约写」时的真实行为，而不是 composable 内部的孤立逻辑。
 */
function useLoader(fetcher) {
  const loading = ref(false)
  const data = ref(null)
  const error = ref('')
  const { run, isLatest, cancel } = useLatestRequest()

  async function load() {
    loading.value = true
    await run(async (token) => {
      try {
        const result = await fetcher()
        if (!isLatest(token)) return
        data.value = result
      } catch (e) {
        if (!isLatest(token)) return
        error.value = e.message
      } finally {
        if (isLatest(token)) loading.value = false
      }
    })
  }

  return { loading, data, error, load, cancel }
}

describe('useLatestRequest · 乱序响应', () => {
  it('先发后到的旧结果被丢弃，不覆盖新结果', async () => {
    const first = deferred()
    const second = deferred()
    const queue = [first.promise, second.promise]
    const page = useLoader(() => queue.shift())

    const p1 = page.load()
    const p2 = page.load()
    second.resolve({ id: 2 })
    await p2
    expect(page.data.value).toEqual({ id: 2 })

    first.resolve({ id: 1 })
    await p1
    expect(page.data.value).toEqual({ id: 2 })
    expect(page.loading.value).toBe(false)
    expect(page.error.value).toBe('')
  })

  it('过期响应不得关闭新请求的 loading', async () => {
    const first = deferred()
    const second = deferred()
    const queue = [first.promise, second.promise]
    const page = useLoader(() => queue.shift())

    const p1 = page.load()
    const p2 = page.load()
    // 旧请求先回：此时新请求仍在途，loading 必须保持为真
    first.resolve({ id: 1 })
    await p1
    expect(page.loading.value).toBe(true)
    expect(page.data.value).toBeNull()

    second.resolve({ id: 2 })
    await p2
    expect(page.data.value).toEqual({ id: 2 })
    expect(page.loading.value).toBe(false)
  })

  it('过期请求失败也不得污染最新状态（列表不进入错误态）', async () => {
    const first = deferred()
    const second = deferred()
    const queue = [first.promise, second.promise]
    const page = useLoader(() => queue.shift())

    const p1 = page.load()
    const p2 = page.load()
    second.resolve({ id: 2 })
    await p2
    first.reject(new Error('旧请求失败'))
    await p1

    expect(page.data.value).toEqual({ id: 2 })
    expect(page.error.value).toBe('')
  })

  it('连续两次筛选：只有最后一次的结果落库', async () => {
    const first = deferred()
    const second = deferred()
    const queue = [first.promise, second.promise]
    const page = useLoader(() => queue.shift())

    const p1 = page.load()
    const p2 = page.load()
    first.resolve({ keyword: 'a' })
    second.resolve({ keyword: 'b' })
    await Promise.all([p1, p2])

    expect(page.data.value).toEqual({ keyword: 'b' })
  })
})

describe('useLatestRequest · 作废与自增时机', () => {
  it('cancel 后旧响应不得写入（组件卸载 / reset 场景）', async () => {
    const d = deferred()
    const page = useLoader(() => d.promise)

    const p = page.load()
    page.cancel()
    d.resolve({ id: 1 })
    await p

    expect(page.data.value).toBeNull()
    expect(page.error.value).toBe('')
  })

  it('序号在「发起时」自增：首屏请求也会被后续请求判为过期', async () => {
    const queue = [Promise.resolve({ id: 1 }), Promise.resolve({ id: 2 })]
    const page = useLoader(() => queue.shift())

    // 首屏与筛选几乎同时发起，两者都必须进入序号比较，不能只在筛选变化时自增
    const p1 = page.load()
    const p2 = page.load()
    await Promise.all([p1, p2])

    expect(page.data.value).toEqual({ id: 2 })
  })

  it('cancel 之后再发起仍能正常落库（序号继续向后走）', async () => {
    const first = deferred()
    const page = useLoader(() => first.promise)

    const p1 = page.load()
    page.cancel()
    const p2 = page.load()
    first.resolve({ id: 9 })
    await Promise.all([p1, p2])

    expect(page.data.value).toEqual({ id: 9 })
    expect(page.loading.value).toBe(false)
  })
})
