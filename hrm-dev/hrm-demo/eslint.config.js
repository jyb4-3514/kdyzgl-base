import js from '@eslint/js'
import globals from 'globals'
import pluginVue from 'eslint-plugin-vue'
import vueA11y from 'eslint-plugin-vuejs-accessibility'
import eslintConfigPrettier from 'eslint-config-prettier'

/**
 * ESLint 9 flat config —— 快递驿站智汇系统 · 三端 Demo
 *
 * 设计原则（见《前端改进建议书》§5.2.2）：
 * 1. 依赖边界机器化：把「展示层不得直连假后端」等三条架构约束从口头约定变成 lint error，越界即红。
 * 2. 渐进放行：纯逻辑层（shared / utils）开 error，展示层（views / components）开 warn。
 *    一次性全开 error 会爆数百条，团队转头用 --no-verify 绕过、规范整体失效；
 *    故本配置刻意不启用 --max-warnings 0，允许 warn 长期存在、逐迭代收敛。
 * 3. 不拦项目台账：`TODO(扩展)` 是全局扩展点标记（现 79 处），no-warning-comments 一律关闭。
 */

// 逻辑密集、回归成本高的「纯工具层」——规则从严
const CORE_FILES = ['src/shared/**', 'src/pc/utils/**', 'src/mobile/utils/**']

// 展示层——规则从宽，先看 warn
const VIEW_FILES = [
  'src/pc/views/**',
  'src/mobile/views/**',
  // 按域拆模块后，模块内页面同样属展示层；不补这条会让「展示层降 warn」口径在 modules 下失效
  'src/mobile/modules/**',
  'src/**/components/**',
  'src/pc/layout/**',
  'src/mobile/layout/**'
]

/**
 * 未使用变量
 * caughtErrors 用 none：本项目空 catch 是刻意的「静默降级、不阻断主流程」，
 * 且每处都写了中文原因注释，逐处补 `_` 前缀只是噪音。
 */
const noUnusedVars = (severity) => [
  severity,
  { argsIgnorePattern: '^_', varsIgnorePattern: '^_', caughtErrors: 'none' }
]

// 展示组件边界（L1 + L2）：不得直连接口、不得直接 useStore；
// 必须复述 mock 禁令与「老板端模块是叶子域」两条既有红线 —— flat config 中同一 rule 后者整体覆盖前者。
const COMPONENT_BOUNDARY_PATTERNS = [
  {
    group: ['**/shared/mock/**', '@/shared/mock/**'],
    message: '展示层禁止直连假后端；装配点仅限 pc/main.js 与 mobile/main.js'
  },
  { group: ['**/modules/boss/**'], message: '老板端模块是叶子域，禁止被员工端反向依赖' },
  { group: ['**/api/**', '@/mobile/api/**'], message: '展示组件不得直连接口，数据由容器/composable 注入' },
  {
    group: ['**/stores/**', '@/mobile/stores/**', '@/pc/stores/**'],
    message: '展示组件不得直接 useStore，状态由容器/composable 注入'
  }
]

// constants 边界（L3）：只放静态配置，取数归 stores/composables（消 G3）
const CONSTANTS_BOUNDARY_PATTERNS = [
  {
    group: ['**/shared/mock/**', '@/shared/mock/**'],
    message: '展示层禁止直连假后端；装配点仅限 pc/main.js 与 mobile/main.js'
  },
  { group: ['**/api/**', '@/mobile/api/**'], message: 'constants 只放静态配置，取数放到 stores/composables' },
  { group: ['**/stores/**', '@/mobile/stores/**', '@/pc/stores/**'], message: 'constants 只放静态配置，不得依赖 store' }
]

export default [
  {
    // 一期只读资产必须是第一个 ignore（误伤即灾难）
    ignores: ['**/node_modules/**', '**/dist/**', '../../hrm-admin/**', '../hrm-admin/**']
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
      // 中文文案刻意用全角空格做分隔（如「删除将一并影响」前的缩进），不视为错误
      'no-irregular-whitespace': [
        'error',
        { skipStrings: true, skipComments: true, skipRegExps: true, skipTemplates: true }
      ],
      // 现有组件名 PageNav / SlaTag 等单词数不足，开启纯噪音
      'vue/multi-word-component-names': 'off',
      // 默认值统一在父层给，逐 prop 补 default 无收益
      'vue/require-default-prop': 'off',
      // v-html 仅个别处使用，需单独 review，不做全局拦
      'vue/no-v-html': 'off',
      // TODO(扩展) 是项目扩展点台账，绝不可拦
      'no-warning-comments': 'off'
    }
  },

  // 构建/校验脚本、Playwright 编排与配置文件跑在 Node 侧
  {
    files: ['scripts/**/*.mjs', 'e2e/**/*.js', '*.config.js', '*.config.mjs', '*.config.cjs'],
    languageOptions: { globals: { ...globals.node } },
    rules: {
      // 验收脚本已冻结（754/48 项断言），不为 lint 改写；其未使用变量不影响断言
      'no-unused-vars': noUnusedVars('warn')
    }
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

  // ---- 依赖边界机器化（本批次核心产出）----
  // 规则 1：展示层禁止直连假后端；装配点白名单仅 pc/main.js 与 mobile/main.js，
  //         外加 mobile/utils/workorder.js —— Mock 关闭时的动态 import 兜底属设计允许；
  //         useTransferTargets.js 是该文件拆分后的落点（B2），一并放行，避免拆分时白名单失效（§8.2 L6）。
  {
    files: ['src/pc/**', 'src/mobile/**'],
    ignores: [
      'src/pc/main.js',
      'src/mobile/main.js',
      'src/mobile/utils/workorder.js',
      'src/mobile/views/staff/workorder/composables/useTransferTargets.js'
    ],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['**/shared/mock/**', '@/shared/mock/**'],
              message: '展示层禁止直连假后端；装配点仅限 pc/main.js 与 mobile/main.js'
            }
          ]
        }
      ]
    }
  },
  // 规则 2：纯工具层禁止反向依赖 mock
  {
    files: ['src/shared/domain/**', 'src/shared/composables/**'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            { group: ['**/shared/mock/**', '@/shared/mock/**'], message: 'domain 是纯工具层，禁止反向依赖 mock' }
          ]
        }
      ]
    }
  },
  // 规则 3：端选择页 / 演示资产禁止反向依赖具体端代码
  {
    files: ['src/portal/**', 'src/demo/**'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['**/mobile/**', '**/pc/**', '@/mobile/**', '@/pc/**'],
              message: '端选择页/演示资产禁止反向依赖具体端代码'
            }
          ]
        }
      ]
    }
  },

  // ==========================================================================
  // 规则 4-6：按域拆模块后的域边界（结构与依赖方案 §5.3）
  // --------------------------------------------------------------------------
  // 必须放在规则 1-3 之后：flat config 中同一 rule 名「后者整体覆盖前者」、不做选项合并，
  // 故每条新规则都要把它所覆盖文件原本生效的 patterns 复述一遍 —— 尤其是规则 1 的
  // `shared/mock/**` 封禁，漏掉即被静默抹掉「展示层不得直连假后端」这条架构红线。
  // 依据：ESLint《Configuration Files · Cascading Configuration Objects》。
  // ==========================================================================

  // 规则 4：老板端模块内部 —— 禁止跨域 / 禁止触达 PC 端与一期只读资产 / 复述 mock 禁令
  {
    files: ['src/mobile/modules/boss/**'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['**/shared/mock/**', '@/shared/mock/**'],
              message: '展示层禁止直连假后端；装配点仅限 pc/main.js 与 mobile/main.js'
            },
            {
              group: ['**/views/staff/**', '**/modules/staff/**'],
              message:
                '老板端模块禁止直引其他业务域；跨端复用请走内核或中立共享页（路由层例外只写在聚合点 router/index.js）'
            },
            {
              group: ['@admin/**', '@/pc/**', '**/src/pc/**'],
              message: '老板端模块禁止依赖 PC 端与一期只读资产 @admin'
            }
          ]
        }
      ]
    }
  },
  // 规则 5：员工端域 —— 禁止反向依赖老板端模块（复述 mock 禁令）
  {
    files: ['src/mobile/views/staff/**', 'src/mobile/modules/staff/**'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['**/shared/mock/**', '@/shared/mock/**'],
              message: '展示层禁止直连假后端；装配点仅限 pc/main.js 与 mobile/main.js'
            },
            {
              group: ['**/modules/boss/**'],
              message: '老板端模块是叶子域，禁止被员工端反向依赖'
            }
          ]
        }
      ]
    }
  },
  // 规则 6：PC 端 —— 禁止反向依赖移动端老板模块（复述 mock 禁令）
  {
    files: ['src/pc/**'],
    ignores: ['src/pc/main.js'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['**/shared/mock/**', '@/shared/mock/**'],
              message: '展示层禁止直连假后端；装配点仅限 pc/main.js 与 mobile/main.js'
            },
            {
              group: ['**/modules/boss/**'],
              message: '老板端模块属移动端叶子域，PC 端禁止反向依赖'
            }
          ]
        }
      ]
    }
  },

  // ==========================================================================
  // 规则 7-13：员工端拆分新增（结构与依赖方案 §5.3 / §8.2 的 6 项约束）
  // --------------------------------------------------------------------------
  // 渐进落地方式（§8.2 未规定严重级别，按「拆分产物从严、存量从宽」执行）：
  //   error —— 拆分产物：views/**/components/**（L1/L2）、views/staff/**/index.vue（L5 页面壳）
  //   warn  —— 尚未拆分的存量：mobile/components/**（5 处既有越界）、各整页与 login/index.vue
  //            存量若直接设 error，`npm run lint` 不可能 0 error，规范会被绕过；随 B1–B8 逐批收敛后再升 error。
  // 每条规则同样复述上一版生效的 patterns（flat config 后者整体覆盖前者）。
  // ==========================================================================

  // L1 + L2（error）：域内展示组件不得直连接口、不得直接 useStore
  {
    files: ['src/mobile/views/**/components/**'],
    rules: { 'no-restricted-imports': ['error', { patterns: COMPONENT_BOUNDARY_PATTERNS }] }
  },
  // L1 + L2（warn）：既有跨域组件层的存量越界，待 B5/B6 把取数与 store 上提后升 error（消 G7）
  {
    files: ['src/mobile/components/**'],
    rules: { 'no-restricted-imports': ['warn', { patterns: COMPONENT_BOUNDARY_PATTERNS }] }
  },
  // L3（error）：constants 只放静态配置（消 G3）
  {
    files: ['src/mobile/constants/**'],
    rules: { 'no-restricted-imports': ['error', { patterns: CONSTANTS_BOUNDARY_PATTERNS }] }
  },
  // L4（warn）：普通组件 ≤300 行；存量整页尚未拆分，先 warn
  {
    files: ['src/mobile/**/*.vue'],
    rules: { 'max-lines': ['warn', { max: 300, skipBlankLines: true, skipComments: true }] }
  },
  // L4（error）：已拆出的域内组件 ≤300 行
  {
    files: ['src/mobile/views/**/components/**/*.vue'],
    rules: { 'max-lines': ['error', { max: 300, skipBlankLines: true, skipComments: true }] }
  },
  // L5（warn）：页面壳 ≤150 行；login/index.vue 是既有整页（非壳），先 warn
  {
    files: ['src/mobile/views/**/index.vue'],
    rules: { 'max-lines': ['warn', { max: 150, skipBlankLines: true, skipComments: true }] }
  },
  // L5（error）：员工端拆分后的页面壳 ≤150 行
  {
    files: ['src/mobile/views/staff/**/index.vue'],
    rules: { 'max-lines': ['error', { max: 150, skipBlankLines: true, skipComments: true }] }
  },

  // a11y 插件：全量 error 会瞬间爆数百条（现有 321 处自绘 aria/role），
  // 仅保留 5 条「结构性强、误报低」的为 error，其余降 warn。
  {
    rules: Object.fromEntries(
      Object.keys(vueA11y.rules)
        .filter(
          (r) => !['alt-text', 'aria-props', 'aria-role', 'anchor-has-content', 'form-control-has-label'].includes(r)
        )
        .map((r) => [`vuejs-accessibility/${r}`, 'warn'])
    )
  },

  // 必须放最后：关掉所有与 Prettier 冲突的排版规则，避免 eslint --fix 与 prettier 互改
  eslintConfigPrettier
]
