---
name: "vue-expert"
description: "Vue 3 前端开发专家技能。覆盖 Composition API、Vue Router 4、Pinia 状态管理、Vite 构建、组件设计、性能优化、可访问性。源自 vuejs-ai/skills + KIMJINWOO4/vue-skills。触发：Vue/前端/组件/页面/H5/路由/状态管理/Pinia/Vuex/Vite/响应式。"
---

# Vue 3 前端开发专家

> 综合 [vuejs-ai/skills](https://github.com/vuejs-ai/skills)（Vue 官方社区技能） +
> [KIMJINWOO4/vue-skills](https://github.com/KIMJINWOO4/vue-skills)（12 技能 + 640 参考文件） +
> [peterfei/ai-agent-frontend-dev](https://github.com/peterfei/ai-agent-frontend-dev)（前端开发 Agent）。

## 技术栈基线

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue | 3.x | Composition API + `<script setup>` |
| Vue Router | 4.x | 路由管理 |
| Pinia | 2.x | 状态管理 |
| Vuex | 3.x | 兼容旧模块 |
| Vite | 5.x | 构建工具 |
| Element Plus | 2.x | UI 组件库 |

## 组件开发规范

### 结构模板
```vue
<script setup>
// 1. imports
// 2. props / emits
// 3. composables
// 4. reactive state
// 5. computed
// 6. methods
// 7. lifecycle hooks
// 8. watch
</script>

<template>
  <!-- 模板 -->
</template>

<style scoped>
/* 组件样式 */
</style>
```

### Props 定义
```typescript
interface Props {
  modelValue?: string
  disabled?: boolean
  size?: 'small' | 'default' | 'large'
}
const props = withDefaults(defineProps<Props>(), {
  disabled: false,
  size: 'default'
})
```

### Emits 定义
```typescript
const emit = defineEmits<{
  'update:modelValue': [value: string]
  'change': [value: string]
}>()
```

## 状态覆盖

每个数据组件必须覆盖四种状态：
- **loading**：数据加载中，显示骨架屏/加载动画
- **empty**：数据为空，显示空状态提示 + 操作引导
- **error**：加载失败，显示错误信息 + 重试按钮
- **normal**：正常数据展示

## 路由规范

```typescript
// 路由命名：模块-页面
{ path: '/employee/list', name: 'EmployeeList', component: () => import('@/views/employee/List.vue') }
{ path: '/employee/:id',  name: 'EmployeeDetail', component: () => import('@/views/employee/Detail.vue') }
```

## 性能优化

- [ ] 路由懒加载：`() => import()`
- [ ] 组件异步加载：`defineAsyncComponent`
- [ ] 列表虚拟滚动：> 100 条数据
- [ ] 图片懒加载：`v-lazy`
- [ ] KeepAlive：缓存频繁切换的页面
- [ ] 大对象 shallowRef / shallowReactive

## 可访问性（A11y）

- [ ] 语义化 HTML 标签
- [ ] 表单 label 关联 input
- [ ] 按钮有明确的文本或 aria-label
- [ ] 颜色对比度 ≥ 4.5:1
- [ ] 键盘导航可用（Tab/Enter/Escape）

## 移动端 H5 适配

- 安卓 H5 壳：内嵌 WebView，走同一后端 API
- Viewport：`<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">`
- 点击延迟：使用 `fastclick` 或 `touch-action: manipulation`
- 安全区域：`env(safe-area-inset-*)` 适配刘海屏

## 参考来源

- [vuejs-ai/skills](https://github.com/vuejs-ai/skills) — Vue 官方社区技能
- [KIMJINWOO4/vue-skills](https://github.com/KIMJINWOO4/vue-skills) — 12 技能 + 640 参考文件
- [peterfei/ai-agent-frontend-dev](https://github.com/peterfei/ai-agent-frontend-dev) — 前端开发 Agent