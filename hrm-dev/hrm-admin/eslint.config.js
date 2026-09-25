import js from '@eslint/js'
import globals from 'globals'
import pluginVue from 'eslint-plugin-vue'
import vueA11y from 'eslint-plugin-vuejs-accessibility'
import eslintConfigPrettier from 'eslint-config-prettier'

/**
 * ESLint 9 flat config —— 快递驿站智汇系统 · 一期员工管理 PC 管理端（hrm-admin）
 *
 * 由来（U-B）：`hrm-admin` 此前只有 dev/build/preview，无 lint / 无校验脚本，
 * 属 ADR §7.3 U-B「是否为 hrm-admin 补最小门禁」的落地。规则集**对齐项目既有底线**
 * （`hrm-demo/eslint.config.js` 与 `apps/web/eslint.config.js` 的共性部分），只做必要投影：
 * 1. 通用底线：js recommended + vue flat/recommended + a11y（结构性 5 条 error，其余 warn）。
 * 2. 渐进放行：纯逻辑层（utils/api/stores/router）从严，展示层（views/layout）先看 warn ——
 *    与既有工程同策略，避免一次性全开 error 导致团队用 --no-warning 绕过、规范整体失效。
 * 3. 不拦项目台账：`TODO(扩展)` 是全局扩展点标记，no-warning-comments 一律关闭。
 * 4. 架构约束机器化：ADR §2.3 A1「hrm-admin 不引用演示工程」由 no-restricted-imports 兜住。
 *
 * 注意：本工程为独立工程（不在 hrm-clients workspace 内），依赖需在本工程 `npm install` 后才可运行。
 */

// 逻辑密集、回归成本高的「纯逻辑层」——规则从严
const CORE_FILES = ['src/utils/**', 'src/api/**', 'src/stores/**', 'src/router/**']

// 展示层——规则从宽，先看 warn
const VIEW_FILES = ['src/views/**', 'src/layout/**']

/**
 * 未使用变量
 * caughtErrors 用 none：本项目空 catch 是刻意的「静默降级、不阻断主流程」，逐处补 `_` 前缀只是噪音。
 */
const noUnusedVars = (severity) => [
  severity,
  { argsIgnorePattern: '^_', varsIgnorePattern: '^_', caughtErrors: 'none' }
]

export default [
  {
    ignores: ['**/node_modules/**', '**/dist/**']
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
      // smart 放行 `== null`（判空惯用法），其余宽松相等仍提示
      eqeqeq: ['warn', 'smart'],
      // 中文文案刻意用全角空格做分隔，不视为错误
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

  // 校验脚本跑在 Node 侧
  {
    files: ['scripts/**/*.mjs', '*.config.js', '*.config.mjs', '*.config.cjs'],
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

  // ---- 纯逻辑层：在展示层之上再收紧 ----
  {
    files: CORE_FILES,
    rules: {
      'no-unused-vars': noUnusedVars('error'),
      'prefer-const': 'error',
      'no-var': 'error',
      'no-duplicate-imports': 'error'
    }
  },

  // ---- 架构约束机器化：ADR §2.3 A1「hrm-admin 不引用演示工程」 ----
  {
    files: ['src/**'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['**/hrm-demo/**', '**/src/pc/**', '**/src/mobile/**'],
              message: 'ADR §2.3 A1：hrm-admin 是生产工程，不得引用演示工程 hrm-demo 的源码'
            }
          ]
        }
      ]
    }
  },

  // a11y 插件：全量 error 会瞬间爆量，仅保留 5 条「结构性强、误报低」的为 error，其余降 warn。
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
