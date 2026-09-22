# PC 端拆分与精细化规范

> 版本 v1.0 | 建立日期 2026-09-22 | 分支 `feature/PC端拆分与精细化`
> 适用：`hrm-dev/hrm-demo/src/pc/**`
> 本文档是本轮拆分的**唯一口径来源**。各批次实现前必读，实现后逐条自检。

## 1. 背景与结论

现状（实测）：

| 文件 | 行数 | 问题 |
| --- | --- | --- |
| `views/workOrder/index.vue` | 1057 | 列表 + 详情抽屉 + 新建 + 自动派单 + 复制 + 竞态守卫 + 列定义混于一处 |
| `views/dashboard/index.vue` | 736 | 指标卡 + 图表 + 待办 + 多路请求编排混于一处 |
| `views/schedule/index.vue` | 651 | 排班矩阵 + 批量工具 + 班次管理混于一处 |
| `views/sync/index.vue` | 644 | 页内三 Tab（流水 / 采集配置 / 配置管理）编排 + 各自状态混于一处 |
| `views/finance/index.vue` | 613 | 三 Tab + 试算预览 + 规则编辑混于一处 |

已具备、**不重复建设**：`api/` 按域分文件、`components/` 已有 11 个通用组件、`shared/domain/` 纯函数层 + 98% 单测覆盖、ESLint/Prettier/Stylelint/Vitest/commitlint 质量链。

## 2. 目标目录约定

```
src/pc/
  api/                  # 接口封装（按域一文件，已存在，保持）
  components/           # 跨域通用展示组件（已存在，保持）
  composables/          # 跨域复用逻辑（已存在 useLogout，本轮按需增补）
  stores/               # 【新增】跨页共享状态（Pinia）
  utils/                # 跨域纯函数（已存在，保持）
  views/<域>/
    index.vue           # 页面壳：路由入口 + 数据编排 + 四态切换（≤150 行）
    components/         # 该域视图组件（展示 + 局部交互）
    composables/        # 该域逻辑（请求编排 / 表单 / 列定义 / 筛选）
    model/              # 【按需新增】列定义、字典映射、枚举常量
    utils/              # 该域纯函数（已有则保留）
```

**新增目录只在确有内容时创建**，不预留空目录。

## 3. 组件契约

1. **命名**：组件文件与组件名同为 PascalCase 多词（`WorkOrderFilterBar.vue` → `WorkOrderFilterBar`）。
2. **数据流**：`props` 进 / `emits` 出。禁止子组件直接改 props。
3. **分层禁令**（ESLint 已在 `no-restricted-imports` 层面守住一部分，此处补视图层约定）：
   - 展示组件（`components/` 下）：**禁止** `import` `api/*`、**禁止** `import` `shared/mock/*`、**禁止**直接 `useXxxStore()`。数据由容器组件或 composable 以 props 注入。
   - 容器组件（`views/<域>/index.vue` 及域内页面级组件）：允许 `import` store / api / composable。
4. **四态覆盖**：所有承载数据的组件必须显式覆盖 `loading / empty / error / normal`，复用既有 `StateBlock.vue`（PC）/ `PageState`（移动端），不得自造第四套。
5. **体积约束**：单文件 ≤300 行；`index.vue` ≤150 行。超限即说明还有未拆净的职责。
6. **事件命名**：动词过去式或名词（`change` / `submit` / `select`），不用 `onXxx`。

## 4. 状态边界（三层，判据唯一）

| 层级 | 判据 | 落点 |
| --- | --- | --- |
| 跨页共享状态 | ≥2 个路由页面或 layout 消费，或需跨组件订阅 | `src/pc/stores/<域>.js`（Pinia setup store） |
| 域内复用逻辑 | 仅该域 ≥2 个组件共用 | `views/<域>/composables/useXxx.js` |
| 服务端数据获取 | 请求 + 缓存 + 竞态守卫，仅本页消费 | `views/<域>/composables/useXxxData.js`（内部调 `api/`） |

- **不引入** vue-query / TanStack Query / Pinia 持久化插件等新依赖（M10 决策 #13 精神：避免过度设计）。
- 仅被单个组件消费的状态**不外提**，留在组件内 `ref`。
- store 必须是 setup store 写法，与既有 `@admin/stores/*`、`mobile/stores/*` 风格一致。

## 5. 竞态守卫统一方案

M11 已在工单列表页引入请求序号守卫，同类问题在**考勤 / 包裹 / 员工列表页仍存在**（`TODO(扩展)`）。本轮统一为：

```js
let reqSeq = 0
async function load() {
  const seq = ++reqSeq
  const data = await fetchList(params)
  if (seq !== reqSeq) return // 后发先至：丢弃过期响应
  list.value = data
}
```

**要求**：守卫生效的前提是「发起时自增、回来时比对」，不得只在筛选变化时自增（会漏掉首屏与筛选的竞争）。

## 6. 行为等价红线（违反即回滚该子任务）

1. `npm run verify:mock` **878 项断言一字不改、全部通过**。不得为通过而修改 `scripts/verify-mock.mjs`。
2. `npm run e2e` **37 用例通过 / 0 失败**。e2e 依赖 DOM 选择器与可访问名，**DOM 语义不得退化**（按钮文本、`aria-label`、`role`、可聚焦顺序）。
3. `hrm-dev/hrm-admin/**`、`hrm-dev/hrm-server/**` **零改动**。`@admin` 只读复用。
4. 拆分**不得夹带行为变更**。若发现既有缺陷，单独记录并在拆分完成后单列一条修复，不与拆分混在一起提交。
5. 提交粒度：一个域一次提交，信息形如 `refactor: 拆分 PC 工单页为壳+组件+composable`。

## 7. 性能目标（Element Plus 按需引入）

- 现状：`src/pc/main.js` 全量 `app.use(ElementPlus)` + `element-plus/dist/index.css` 全量样式 + 全量注册 `@element-plus/icons-vue`。
- 目标：PC 首屏 gzip **451 KB → 250–290 KB**。
- 硬约束：
  - `@admin` 一期页面依赖**全局图标注册**与 `<el-*>` 全局组件，**图标全局注册不可移除**（移了必白屏，且不允许改一期源码）。
  - `ElMessage / ElMessageBox / ElNotification / ElLoading` 以函数式调用，必须确保其样式被引入，否则出现「弹框无样式」。
  - 完成后必须做 **A/B 体积对比 + 全部 PC 路由（15 个）逐个目视**，发现样式缺失即回滚本步。
- 路由懒加载：确认 `src/pc/router/index.js` 全部 `() => import()`；重组件（`TrendChart`、`ConfigImportDrawer`）按需 `defineAsyncComponent`。

## 8. 响应式断点基线

| 断点 | 目标 | 现状 |
| --- | --- | --- |
| ≥1440px | `.app-main` 居中，`--content-max: 1440px` | 已实现，未在真实宽屏验证 |
| 1280–1439px | 侧栏完整展开，PageHeader 单行 | **未验证**（内置浏览器固定 810×658） |
| 1024–1279px | 侧栏折叠为图标，表格横向滚动可用 | 窄窗观察到折行，**未判定真假** |
| <768px | 仅移动端入口承接，PC 不承诺 | 不承诺 |

**要求**：新增/调整断点一律走 `tokens.base.scss` + 平台差异层，**不得**在组件内写魔法 px 媒体查询（除既有白名单项）。

## 9. 单元测试要求

- 覆盖对象：`src/pc/stores/**`、`src/pc/composables/**`、`src/pc/utils/**`、`src/pc/views/**/model/**`。
- 必测边界：空集、单元素、极值、非法输入、**竞态守卫（乱序响应）**、四态切换。
- 复用既有 Vitest 配置（`vitest.config.mjs`），spec 与被测文件同目录，命名 `<name>.spec.js`。
- 不追求行数覆盖率数字，追求**把已修缺陷变成永久回归网**（如工单列表竞态、`computed` 漏 `.value`、`monthShiftMap` 未导入）。

## 10. 交付节奏与检查点

批次按「域」切分，每批次完成即跑一次门禁（`verify:mock` + `test` + `lint` + `build`），通过后再进下一批次。检查点写入 `SESSION-STATE.md`。

| 批次 | 范围 | 状态 |
| --- | --- | --- |
| B1 | `workOrder` + `schedule` | 待办 |
| B2 | `dashboard` + `sync` + `finance` | 待办 |
| B3 | 状态管理三层梳理 + PC store | 待办 |
| B4 | 性能（Element Plus 按需 + 懒加载） | 待办 |
| B5 | 响应式断点补齐 | 待办 |
| B6 | 单测补齐 | 待办 |
| B7 | 视觉/无障碍走查 + 全门禁 | 待办 |
