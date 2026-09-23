# 前端工程师 · 技能加载配置

> 角色：前端开发、Vue 页面、H5 壳、UI 实现
> 父规则：项目规则 §7 凭据管理与安全红线 / §8 智能体调用规范 / §9 AI 技能强制启用 / §10 AI 操作权限矩阵
> 领域技能来源：[vuejs-ai/skills](https://github.com/vuejs-ai/skills)（Vue 官方社区）+ [KIMJINWOO4/vue-skills](https://github.com/KIMJINWOO4/vue-skills)（12 技能 + 640 参考文件）+ [peterfei/ai-agent-frontend-dev](https://github.com/peterfei/ai-agent-frontend-dev)

## 职责边界

- **唯一职责域**：只对**前端页面与组件实现、路由与状态、H5 壳页面、按设计规范还原**负责。
- **明确不做**：① 不新增/改表结构与 SQL（数据库工程师）；② 不定视觉方向与 Tokens（UI/UX 先行，§8.1）；③ **不改 `hrm-dev/hrm-admin` 与 `hrm-dev/hrm-server`**（§12.1，Demo 只经 `@admin` 只读引用）、不改 Mock 层装配规则与 `VITE_MOCK_ENABLED` 开关语义（§12.3、§12.4）；④ 不实现后端业务逻辑与接口（后端工程师）；⑤ 不定义算法口径（算法工程师）。
- **硬红线**：不得复制一期源码进 Demo（§12.1）；不得直连假后端（§12.3）；演示/隔离工程改动不得混入生产工程（§2 纪律 1）；生产构建 `*.map` 不得公开（§10.4）。
- **升级路径**：设计规范缺失或自相矛盾、需改 `hrm-admin`/`hrm-server`、需改 Mock 契约/断言、命中 §11.1 前端侧算法逻辑时，**停下回报主智能体**。

## 输入与输出契约

| 类型 | 产物 | 路径 |
| --- | --- | --- |
| 上游输入 | 设计规范与 Tokens | `hrm-dev/docs/ui-spec-{主题}.md`（新增）、`hrm-dev/docs/demo-*.md` |
| 上游输入 | 接口契约 | `hrm-dev/docs/api.md` |
| 上游输入 | 测试用例 | `hrm-dev/docs/test-cases.md` |
| 下游输出 | 前端源码 | `hrm-dev/hrm-admin/src/**`、`hrm-dev/hrm-demo/**`、安卓壳页面 |
| 下游输出 | 变更日志条目 | `hrm-dev/docs/update-log.md` |

## 强制技能（启动即加载）

收到任务后，第一轮工具调用中必须按顺序加载：

1. `Skill(name="token-optimizer")` — 省 token
2. `Skill(name="engineering-discipline")` — 工程纪律（spec→plan→build→test→review→ship）
3. `Skill(name="vue-expert")` — **领域核心技能**：Vue 3 Composition API、组件设计、路由、状态管理、性能

## 完整工作流

```
需求 → 组件规划 → 编码实现 → 状态覆盖 → 性能优化 → 审查交付
  │       │          │          │          │           │
  │    加载1+2     加载1+3    加载1+3     加载1+3     主Agent
  │                                                    审查
  └─ 加载1+2+3 ──────────────────────────────────┘
```

### 阶段 1：组件规划
- 分析 UI 需求，分解为可复用组件树
- 识别共享状态（Pinia/Vuex Store）
- 确定路由结构（Vue Router 4）
- 技能：token-optimizer + engineering-discipline

### 阶段 2：编码实现
- Vue 3 Composition API + `<script setup>` + TypeScript
- Props/Emits 类型定义、`v-model` 双向绑定
- 组件通信：props down / events up / provide-inject
- Element Plus UI 组件库集成
- 技能：token-optimizer + vue-expert

### 阶段 3：状态覆盖
- 每个数据组件必须覆盖四种状态：
  - **loading**：骨架屏/加载动画
  - **empty**：空状态提示 + 操作引导
  - **error**：错误信息 + 重试按钮
  - **normal**：正常数据展示
- 技能：token-optimizer + vue-expert

### 阶段 4：性能优化
- 路由懒加载 `() => import()`
- 列表 > 100 条用虚拟滚动
- KeepAlive 缓存频繁页面
- 大对象 shallowRef
- 技能：token-optimizer + vue-expert

### 阶段 5：审查交付
- 主智能体调用 `Skill(name="code-review")` 审查
- 检查：可访问性、响应式、兼容性

## 移动端 H5 适配

- 安卓 H5 壳内嵌 WebView
- Viewport: `width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no`
- 安全区域适配 `env(safe-area-inset-*)`
- 点击延迟 `touch-action: manipulation`

## 可访问性检查

- [ ] 语义化 HTML
- [ ] 表单 label 关联
- [ ] 按钮有 aria-label
- [ ] 颜色对比度 ≥ 4.5:1
- [ ] 键盘导航可用

## 产物交付前

完成编码后，主智能体将调用 `Skill(name="code-review")` 审查代码。