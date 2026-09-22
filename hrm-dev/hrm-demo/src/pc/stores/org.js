import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { getStations } from '@admin/api/station'
import { getDepartmentTree } from '@admin/api/department'
import { useAuthStore } from '@admin/stores/auth'
import { flattenDeptTree } from '../utils/department.js'

/**
 * 组织基础数据（驿站清单 + 部门树）：PC 端唯一的跨页共享服务端状态
 *
 * 为什么落在 stores 层（demo-pc-refactor.md §4「跨页共享状态」判据 —— ≥2 个路由页面消费）：
 * 驿站清单被 11 个路由页面用完全相同的参数各拉一遍（考勤/排班/工单/包裹/KPI/人事/财务/请假/入离职/通知/同步），
 * 部门树被人事/入离职/财务三页各拉一遍 —— 属「同一份基础数据多页重复取数」，收敛后同一会话只发一次请求。
 * 各页自己的「选中驿站」不进 store：考勤默认全域、排班默认首个驿站、包裹默认未选，默认值不同即语义不同。
 *
 * 缓存失效策略：
 * 1. 只缓存成功结果。失败不置 loaded，下次进页面会重发（一次接口抖动不该把下拉锁死整个会话）。
 * 2. 登录态失效自动清空：驿站与部门都是「按当前账号权限下发」的数据，跨身份复用会把上一个角色的可见驿站串给下一个角色。
 * 3. TODO(扩展): 驿站管理页（一期 @admin 页面，不可改）增删驿站后回跳到业务页仍读到旧清单；
 *    待该页开放保存回调或二期自建驿站管理页后，在保存成功后调用 reset() 失效缓存。
 *
 * 若将来判据变化：驿站/部门各自被单页独享时，退化为 views/<域>/composables/useXxxData.js 的页内取数；
 * 需要持久化（刷新不丢）时另议，勿在本 store 内挂持久化插件（§4 明确不引新依赖）。
 */
export const useOrgStore = defineStore('pcOrg', () => {
  const stations = ref([])
  const departments = ref([])
  const departmentOptions = computed(() => flattenDeptTree(departments.value))

  // loaded 标记与在途 Promise 分开：并发调用共享同一次请求，失败清标记以便下次重试
  let stationsLoaded = false
  let stationsTask = null
  let departmentsLoaded = false
  let departmentsTask = null

  /** 驿站清单（全量不分页）：已取过直接复用，在途时复用同一 Promise */
  function loadStations() {
    if (stationsLoaded) return Promise.resolve(stations.value)
    if (!stationsTask) {
      stationsTask = getStations()
        .then((list) => {
          stations.value = list || []
          stationsLoaded = true
          return stations.value
        })
        .finally(() => {
          stationsTask = null
        })
    }
    return stationsTask
  }

  /** 部门树：失败语义由调用方决定（人事/入离职/财务把失败降级为空下拉，不阻断列表） */
  function loadDepartments() {
    if (departmentsLoaded) return Promise.resolve(departments.value)
    if (!departmentsTask) {
      departmentsTask = getDepartmentTree()
        .then((tree) => {
          departments.value = tree || []
          departmentsLoaded = true
          return departments.value
        })
        .finally(() => {
          departmentsTask = null
        })
    }
    return departmentsTask
  }

  function reset() {
    stations.value = []
    departments.value = []
    stationsLoaded = false
    departmentsLoaded = false
  }

  // token 被清（退出登录 / 401 由拦截器清）即视为换身份，缓存随之作废
  const auth = useAuthStore()
  watch(
    () => auth.token,
    (token) => {
      if (!token) reset()
    }
  )

  return { stations, departments, departmentOptions, loadStations, loadDepartments, reset }
})
