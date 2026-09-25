import js from '@eslint/js'
import globals from 'globals'
import pluginVue from 'eslint-plugin-vue'
import vueA11y from 'eslint-plugin-vuejs-accessibility'
import eslintConfigPrettier from 'eslint-config-prettier'

/**
 * ESLint 9 flat config —— 快递驿站智汇系统 · 网页端（PC 管理后台）（B5 独立工程）
 *
 * 设计原则沿用演示工程（《前端改进建议书》§5.2.2）并**只增不减**（ADR §3.6 门禁不弱化）：
 * 1. 依赖边界机器化：把「展示层不得直连假后端」「端隔离」「请求层统一」「登录态自有」从口头约定变成 lint error。
 * 2. 渐进放行：纯逻辑层（utils / composables）开 error，展示层（views / components / layout）开 warn。
 * 3. 不拦项目台账：`TODO(扩展)` 是全局扩展点标记，no-warning-comments 一律关闭。
 *
 * 端固定化后边界较演示工程更严：mock 从 `@/shared/mock` 变为共享包 `@kdyzgl/mock`，两者一并封禁。
 */

// 逻辑密集、回归成本高的「纯工具层」——规则从严
const CORE_FILES = ['src/utils/**', 'src/composables/**']

// 展示层——规则从宽，先看 warn
const VIEW_FILES = ['src/views/**', 'src/components/**', 'src/layout/**']

/**
 * 未使用变量
 * caughtErrors 用 none：本项目空 catch 是刻意的「静默降级、不阻断主流程」，逐处补 `_` 前缀只是噪音。
 */
const noUnusedVars = (severity) => [
  severity,
  { argsIgnorePattern: '^_', varsIgnorePattern: '^_', caughtErrors: 'none' }
]

/** 假后端封禁：装配点白名单外的任何文件都不得 import（共享包 @kdyzgl/mock 与旧路径 @/shared/mock 同时封） */
const MOCK_PATTERNS = [
  { group: ['@kdyzgl/mock', '@kdyzgl/mock/**'], message: '展示层禁止直连假后端；装配点仅限 src/main.js' },
  { group: ['**/shared/mock/**', '@/shared/mock/**'], message: '展示层禁止直连假后端；装配点仅限 src/main.js' }
]

/**
 * 端隔离（ADR §3.7 B5 ⑤「无跨端源码复制」的机器化）：本端源码不得引用其它端源码；
 * 且必须机器化三条 B5 决策 —— 登录态自建、接口薄壳自建、请求层统一到 @kdyzgl/api-client。
 *
 * @admin 只读引用（D2 短期保留）**不在封禁之列**：一期页面 / 样式基座 / 下载工具为合法只读消费；
 * 但 @admin 的 stores / api / utils/request 被显式封禁 —— 走它们会把登录态与请求层又分裂回两套。
 * 唯一例外：src/main.js 需 import @admin/utils/request 才能把 Mock 挂到一期 axios 实例（一期页面取数依赖）。
 */
const END_ISOLATION_PATTERNS = [
  {
    group: ['**/apps/staff-h5/**', '**/apps/boss-h5/**', '**/hrm-demo/**'],
    message: '本端禁止引用其它端源码（ADR §3.7 B5 ⑤ 无跨端源码复制）'
  },
  { group: ['**/modules/boss/**', '@/modules/boss/**'], message: '管理端移动模块是叶子域，PC 端不得引用' },
  { group: ['@admin/stores/**'], message: '登录态用本端 src/stores/auth.js（端固定 WEB）；@admin/stores 不接入' },
  { group: ['@admin/api/**'], message: '接口薄壳在本端 src/api/**；复用 @admin/api 会把请求层分裂成两套' },
  { group: ['@admin/utils/request'], message: '请求层统一走 @kdyzgl/api-client 的 createHttp；仅 src/main.js 例外（Mock 装配）' }
]

/** 展示组件边界（L1 + L2）：不得直连接口、不得直接 useStore、不得跨端直引 */
const COMPONENT_BOUNDARY_PATTERNS = [
  ...MOCK_PATTERNS,
  ...END_ISOLATION_PATTERNS,
  { group: ['**/api/**', '@/api/**'], message: '展示组件不得直连接口，数据由容器/composable 注入' },
  { group: ['**/stores/**', '@/stores/**'], message: '展示组件不得直接 useStore，状态由容器/composable 注入' }
]

/** constants 边界（L3）：只放静态配置，取数归 stores/composables */
const CONSTANTS_BOUNDARY_PATTERNS = [
  ...MOCK_PATTERNS,
  ...END_ISOLATION_PATTERNS,
  { group: ['**/api/**', '@/api/**'], message: 'constants 只放静态配置，取数放到 stores/composables' },
  { group: ['**/stores/**', '@/stores/**'], message: 'constants 只放静态配置，不得依赖 store' }
]

export default [
  {
    // 一期只读资产必须是第一个 ignore（误伤即灾难）
    ignores: ['**/node_modules/**', '**/dist/**', '../hrm-admin/**', '../../hrm-admin/**']
  },

  js.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  ...vueA11y.configs['flat/recommended'],

  {
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: { ...globals.browser }
    },
    rules: {
      'no-unused-vars': noUnusedVars('error'),
      eqeqeq: ['warn', 'smart'],
      'no-irregular-whitespace': [
        'error',
        { skipStrings: true, skipComments: true, skipRegExps: true, skipTemplates: true }
      ],
      'vue/multi-word-component-names': 'off',
      'vue/require-default-prop': 'off',
      'vue/no-v-html': 'off',
      'no-warning-comments': 'off'
    }
  },

  // 构建/校验脚本、Playwright 编排与配置文件跑在 Node 侧
  {
    files: ['scripts/**/*.mjs', 'e2e/**/*.js', '*.config.js', '*.config.mjs', '*.config.cjs'],
    languageOptions: { globals: { ...globals.node } },
    rules: { 'no-unused-vars': noUnusedVars('warn') }
  },

  // ---- 渐进放行：展示层统一降为 warn ----
  {
    files: VIEW_FILES,
    rules: {
      'no-unused-vars': noUnusedVars('warn'),
      'vue/no-unused-vars': 'warn',
      'vue/no-unused-components': 'warn',
      'vue/no-side-effects-in-computed-properties': 'warn',
      'vue/require-explicit-emits': 'warn'
    }
  },

  // ---- 纯工具层：在展示层之上再收紧 ----
  {
    files: CORE_FILES,
    rules: {
      'no-unused-vars': noUnusedVars('error'),
      'prefer-const': 'error',
      'no-var': 'error',
      'no-duplicate-imports': 'error'
    }
  },

  // ---- 依赖边界机器化 ----
  // 规则 1：展示层禁止直连假后端 + 端隔离；装配点白名单仅 src/main.js（Mock 与 @admin 请求实例装配）
  {
    files: ['src/**'],
    ignores: ['src/main.js'],
    rules: {
      'no-restricted-imports': ['error', { patterns: [...MOCK_PATTERNS, ...END_ISOLATION_PATTERNS] }]
    }
  },
  // 规则 2（L1 + L2，warn）：域内展示组件不得直连接口、不得直接 useStore。
  // 强度口径与演示工程一致：PC 存量整页组件仍有「组件直连 api」的历史写法，一次升 error 会爆 40+ 条、
  // 团队只能绕过（规范整体失效）。**规则定义不减少**，先 warn 长期可见，随收敛逐批升 error（与 L4 同策略）。
  // TODO(扩展): 存量「组件直连 api」收敛到容器/composable 后把本规则升为 error
  {
    files: ['src/views/**/components/**'],
    rules: { 'no-restricted-imports': ['warn', { patterns: COMPONENT_BOUNDARY_PATTERNS }] }
  },
  // 规则 3（L1 + L2，warn）：共享组件层的存量越界，待收敛后再升 error
  {
    files: ['src/components/**'],
    rules: { 'no-restricted-imports': ['warn', { patterns: COMPONENT_BOUNDARY_PATTERNS }] }
  },
  // 规则 4（L3，error）：constants 只放静态配置
  {
    files: ['src/constants/**'],
    rules: { 'no-restricted-imports': ['error', { patterns: CONSTANTS_BOUNDARY_PATTERNS }] }
  },
  // 规则 5（L4，warn）：普通组件 ≤300 行；存量整页尚未拆分，先 warn
  {
    files: ['src/**/*.vue'],
    rules: { 'max-lines': ['warn', { max: 300, skipBlankLines: true, skipComments: true }] }
  },
  // 规则 5（L4，warn）：已拆出的域内组件 ≤300 行。PC 存量组件（抽屉/管理器多为整页级）尚未按 300 行拆分，
  // 与「既有整页先 warn」同策略；**规则定义不减少**，随拆分收敛后再升 error。
  // TODO(扩展): 域内组件按 300 行拆分完成后把本规则升为 error
  {
    files: ['src/views/**/components/**/*.vue'],
    rules: { 'max-lines': ['warn', { max: 300, skipBlankLines: true, skipComments: true }] }
  },
  // 规则 5（L5，warn）：页面壳 ≤150 行
  {
    files: ['src/views/**/index.vue'],
    rules: { 'max-lines': ['warn', { max: 150, skipBlankLines: true, skipComments: true }] }
  },

  // a11y 插件：全量 error 会瞬间爆数百条（既有自绘 aria/role），仅保留 5 条结构性强、误报低的为 error
  {
    rules: Object.fromEntries(
      Object.keys(vueA11y.rules)
        .filter(
          (r) => !['alt-text', 'aria-props', 'aria-role', 'anchor-has-content', 'form-control-has-label'].includes(r)
        )
        .map((r) => [`vuejs-accessibility/${r}`, 'warn'])
    )
  },

  // 必须放最后：关掉所有与 Prettier 冲突的排版规则
  eslintConfigPrettier
]
