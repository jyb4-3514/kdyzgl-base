import js from '@eslint/js'
import globals from 'globals'
import pluginVue from 'eslint-plugin-vue'
import vueA11y from 'eslint-plugin-vuejs-accessibility'
import eslintConfigPrettier from 'eslint-config-prettier'

/**
 * ESLint 9 flat config —— 快递驿站智汇系统 · 员工端「驿站助手」（B3 独立工程）
 *
 * 设计原则沿用演示工程（《前端改进建议书》§5.2.2）并**只增不减**（ADR §3.6 门禁不弱化）：
 * 1. 依赖边界机器化：把「展示层不得直连假后端」等约束从口头约定变成 lint error，越界即红。
 * 2. 渐进放行：纯逻辑层（utils / composables）开 error，展示层（views / components / layout）开 warn。
 * 3. 不拦项目台账：`TODO(扩展)` 是全局扩展点标记，no-warning-comments 一律关闭。
 *
 * 端固定化后边界规则较演示工程更严：mock 从 `@/shared/mock` 变为共享包 `@kdyzgl/mock`，两者一并封禁。
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
 * 端隔离（ADR §3.7 B3 ④「无跨端源码复制」的机器化）：本端源码不得引用其它端源码与一期只读资产。
 *
 * 为什么必须机器化：拆端后「跨域直引」在编译期本应断掉，但相对路径 / 别名一旦被手写成直引，
 * 构建仍可能通过 —— 靠人工静态检索只能事后发现，改成 lint error 才能越界即红。
 * 覆盖：一期 PC 管理端只读资产 @admin、hrm-demo 演示工程源码、管理端移动模块 modules/boss。
 */
const END_ISOLATION_PATTERNS = [
  {
    group: ['@admin/**', '@/pc/**', '**/src/pc/**', '**/src/mobile/**', '**/hrm-demo/**'],
    message: '本端禁止引用一期只读资产与其它端源码（ADR §3.7 B3 ④ 无跨端源码复制）'
  },
  { group: ['**/modules/boss/**', '@/modules/boss/**'], message: '管理端模块是叶子域，本端不得引用' }
]

/** 展示组件边界（L1 + L2）：不得直连接口、不得直接 useStore、不得引管理端模块、不得跨端直引 */
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
  { ignores: ['**/node_modules/**', '**/dist/**'] },

  js.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  ...vueA11y.configs['flat/recommended'],

  {
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: { ...globals.browser, __APP_VERSION__: 'readonly' }
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
  // 规则 1：展示层禁止直连假后端；装配点白名单仅 src/main.js，
  //         外加 utils/workorder.js —— Mock 关闭时的动态 import 兜底属设计允许；
  //         demo/** 为「仅 Mock 态动态加载」的演示资产（账号清单与 mock 密码同源），属合法消费方。
  {
    files: ['src/**'],
    ignores: ['src/main.js', 'src/utils/workorder.js', 'src/demo/**'],
    rules: {
      'no-restricted-imports': ['error', { patterns: [...MOCK_PATTERNS, ...END_ISOLATION_PATTERNS] }]
    }
  },
  // 规则 2：员工域禁止反向依赖管理端模块与其它端源码（复述 mock 禁令与端隔离禁令）
  {
    files: ['src/views/staff/**'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [...MOCK_PATTERNS, ...END_ISOLATION_PATTERNS]
        }
      ]
    }
  },
  // 规则 3（L1 + L2，error）：域内展示组件不得直连接口、不得直接 useStore
  {
    files: ['src/views/**/components/**'],
    rules: { 'no-restricted-imports': ['error', { patterns: COMPONENT_BOUNDARY_PATTERNS }] }
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
  // 规则 5（L4，error）：已拆出的域内组件 ≤300 行
  {
    files: ['src/views/**/components/**/*.vue'],
    rules: { 'max-lines': ['error', { max: 300, skipBlankLines: true, skipComments: true }] }
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
