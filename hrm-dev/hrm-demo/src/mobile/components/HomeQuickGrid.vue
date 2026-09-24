<script setup>
import { computed } from 'vue'
import QuickGridItem from './QuickGridItem.vue'
import { canAccess } from '@/shared/domain/permission.js'
import { useAuthStore } from '../stores/auth.js'

/**
 * 首页快捷功能宫格（D2-2，Organism）
 *
 * 4 列 × 2 行共 8 项（B1 布局规范）；第 9 项起一律下沉到「我的」，不翻页、不做「更多」。
 * 宫格本身不进入错误态：项的数据由父级逐项独立降级（count 失败→无角标，status 失败→`—`），
 * 只有配置数组为空才提示，避免一个接口抖动把整块宫格变成红色错误区（B4-2 硬规则 1）。
 *
 * 项级角色白名单（M11）：配置项可带 `roles`，未声明即全角色可见（向后兼容既有无 roles 的项）；
 * 过滤收在宫格内而不是两端首页各写一遍，判定复用路由守卫同一份 canAccess。
 * TODO(扩展): M11 后员工端宫格为 9~10 项（站长多一项「请假初审」），已超出 B1 的 4×2 网格；
 *   待首页信息架构重排时再决定是「换页」还是「下沉到我的」。
 */
const props = defineProps({
  /** 静态配置数组：{ key, text, icon, to, type, roles? }，见 constants/quickEntries.js */
  entries: { type: Array, required: true },
  /** key → 实时值（计数型传数值，状态型传文案，纯入口型不传），null 表示取数失败 */
  data: { type: Object, default: () => ({}) },
  title: { type: String, default: '快捷功能' },
  /** 标题右侧说明，如管理端的「按待办优先排序」 */
  hint: { type: String, default: '' },
  loading: { type: Boolean, default: false }
})

const auth = useAuthStore()

const visibleEntries = computed(() => props.entries.filter((item) => canAccess(item.roles, auth.user)))
</script>

<template>
  <nav aria-label="快捷功能">
    <div class="section-title">
      <span>{{ title }}</span>
      <span v-if="hint" class="section-title__extra">{{ hint }}</span>
    </div>
    <!-- border=false：整块为一张卡片，相邻格靠内容内边距形成视觉间隔，不加 gutter（B1） -->
    <van-grid :column-num="4" :border="false" class="entry-grid">
      <QuickGridItem
        v-for="item in visibleEntries"
        :key="item.key"
        :icon="item.icon"
        :name="item.text"
        :to="item.to"
        :type="item.type"
        :value="item.type === 'count' ? data[item.key] : null"
        :status-text="item.type === 'status' ? data[item.key] : null"
        :loading="loading"
      />
    </van-grid>
    <p v-if="!visibleEntries.length" class="tip">暂无可用的快捷功能</p>
  </nav>
</template>
