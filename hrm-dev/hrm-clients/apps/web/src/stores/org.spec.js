import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'

/**
 * 组织基础数据 store 的回归网
 * 重点钉住：同一会话只发一次请求（并发共享在途 Promise）、失败不缓存、换身份即失效。
 */
const mocks = vi.hoisted(() => ({ getStations: vi.fn(), getDepartmentTree: vi.fn() }))

vi.mock('@/api/station.js', () => ({ getStations: mocks.getStations }))
vi.mock('@/api/department.js', () => ({ getDepartmentTree: mocks.getDepartmentTree }))
// 一期 auth store 在 state 阶段直读 localStorage，node 环境下不可用，故用可控的 reactive 桩
vi.mock('@/stores/auth', async () => {
  const { reactive } = await import('vue')
  const auth = reactive({ token: 'token-1' })
  return { useAuthStore: () => auth, authStub: auth }
})

const { useOrgStore } = await import('./org.js')
const { authStub } = await import('@/stores/auth')

beforeEach(() => {
  vi.clearAllMocks()
  authStub.token = 'token-1'
  // 每个用例一套全新 Pinia：store 缓存互不干扰
  setActivePinia(createPinia())
})

describe('useOrgStore · 取数与缓存', () => {
  it('并发调用共享同一次请求：多页同时进也只有一个请求', async () => {
    let resolve
    mocks.getStations.mockImplementation(() => new Promise((r) => (resolve = r)))
    const org = useOrgStore()

    const all = Promise.all([org.loadStations(), org.loadStations(), org.loadStations()])
    expect(mocks.getStations).toHaveBeenCalledTimes(1)

    resolve([{ id: 1, stationName: '城东驿站' }])
    await all
    expect(org.stations).toEqual([{ id: 1, stationName: '城东驿站' }])
  })

  it('成功后再次进页面命中缓存，不重复请求', async () => {
    mocks.getStations.mockResolvedValue([{ id: 1 }])
    const org = useOrgStore()

    await org.loadStations()
    await org.loadStations()
    expect(mocks.getStations).toHaveBeenCalledTimes(1)
  })

  it('取数失败不缓存：下次进页面会重发，一次抖动不锁死下拉', async () => {
    mocks.getStations.mockRejectedValueOnce(new Error('接口挂了')).mockResolvedValueOnce([{ id: 2 }])
    const org = useOrgStore()

    await expect(org.loadStations()).rejects.toThrow('接口挂了')
    await org.loadStations()
    expect(mocks.getStations).toHaveBeenCalledTimes(2)
    expect(org.stations).toEqual([{ id: 2 }])
  })

  it('reset 清空数据并让下次取数重新请求', async () => {
    mocks.getStations.mockResolvedValue([{ id: 1 }])
    const org = useOrgStore()

    await org.loadStations()
    org.reset()
    expect(org.stations).toEqual([])

    await org.loadStations()
    expect(mocks.getStations).toHaveBeenCalledTimes(2)
  })
})

describe('useOrgStore · 换身份与部门树', () => {
  it('登录态失效即清空缓存：上一个角色的可见驿站不串给下一个角色', async () => {
    mocks.getStations.mockResolvedValue([{ id: 1 }])
    mocks.getDepartmentTree.mockResolvedValue([{ id: 9, deptName: '总部' }])
    const org = useOrgStore()

    await org.loadStations()
    await org.loadDepartments()

    authStub.token = ''
    await nextTick()
    expect(org.stations).toEqual([])
    expect(org.departments).toEqual([])

    authStub.token = 'token-2'
    await org.loadStations()
    expect(mocks.getStations).toHaveBeenCalledTimes(2)
  })

  it('departmentOptions 由部门树拍平而来', async () => {
    mocks.getDepartmentTree.mockResolvedValue([{ id: 1, deptName: '总部', children: [{ id: 2, deptName: '城东分部' }] }])
    const org = useOrgStore()

    await org.loadDepartments()
    expect(org.departmentOptions).toEqual([
      { id: 1, deptName: '总部' },
      { id: 2, deptName: '城东分部' }
    ])
  })
})
