import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { BOSS_TODO_GROUPS, STAFF_TODO_GROUPS } from '../constants/todoGroups.js'
import { canAccess } from '@/shared/domain/permission.js'
import { readToken } from '../utils/authStorage.js'
import { useAuthStore } from './auth.js'

/**
 * 待办快照（A4-3 的收敛点）
 *
 * 为什么单独成 store：待办计数同时被三处消费 —— 首页宫格角标、消息 Tab 角标、消息页「待办」子视图。
 * 三处各拉一遍接口必然出现「首页说 3 条、消息页说 2 条」的口径漂移，且首屏请求翻三倍。
 *
 * 取值约定：`total` 为 `null` 表示「取数失败 / 未知」，`0` 表示「确实没有待办」。
 * 两者在角标与文案上表现不同，绝不可互相顶替（B4-2 硬规则 2）。
 */
export const useTodoStore = defineStore('mobileTodo', () => {
  const auth = useAuthStore()

  /** 分组快照（含前 3 条明细），结构由 constants/todoGroups.js 定义 */
  const groups = ref([])
  const loading = ref(false)

  /** 按角色取分组：老板 6 类（A4-3 + 待终审请假），员工 4 类、站长 5 类（多「待初审请假」）。
   *  组级 roles 未声明 = 所有能看到本表的角色都可见（向后兼容既有 5 + 3 组）。 */
  const configs = computed(() =>
    (auth.isAdmin ? BOSS_TODO_GROUPS : STAFF_TODO_GROUPS).filter((item) => canAccess(item.roles, auth.user))
  )

  /** key → total，供首页宫格按 key 取角标值（null 即「未知」，不渲染角标） */
  const counts = computed(() => Object.fromEntries(groups.value.map((item) => [item.key, item.total])))

  /** 「待我处理」总数：取数失败的分组不计入（该部分按 0 计），全失败即 0 */
  const total = computed(() => groups.value.reduce((sum, item) => sum + (item.total || 0), 0))

  /** 是否至少有一组取数成功：全失败时消费方显示 `···` 而不是 0（B4-2 硬规则 2） */
  const known = computed(() => groups.value.some((item) => item.total !== null))

  async function refresh() {
    if (!readToken()) {
      groups.value = []
      return
    }
    loading.value = true
    // 逐组独立降级：某一组接口挂了只让该组显示重试，不把整个待办页判成错误态（B4-2 硬规则 1）
    const results = await Promise.all(
      configs.value.map(async (config) => {
        try {
          const { total: count, rows } = await config.load()
          return { key: config.key, title: config.title, to: config.to, total: count, rows, error: '' }
        } catch (e) {
          return {
            key: config.key,
            title: config.title,
            to: config.to,
            total: null,
            rows: [],
            error: e.message || '加载失败'
          }
        }
      })
    )
    groups.value = results
    loading.value = false
  }

  function clear() {
    groups.value = []
  }

  return { groups, counts, total, known, loading, refresh, clear }
})
