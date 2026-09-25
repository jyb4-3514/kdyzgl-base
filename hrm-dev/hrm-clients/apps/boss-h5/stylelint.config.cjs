/**
 * Stylelint 配置 —— 快递驿站智汇系统 · 管理端「驿站精灵」
 * 只 lint src 下的 scss 与 Vue SFC 样式块（脚本文件交给 ESLint/Prettier）。
 * 放行原则：设计系统文件（tokens / mobile.scss）优先保证语义与兼容性，与之冲突的规则一律放宽，不为「过 lint」改设计系统源码。
 */
module.exports = {
  extends: ['stylelint-config-standard-scss', 'stylelint-config-recommended-vue/scss'],
  ignoreFiles: ['node_modules/**', 'dist/**'],
  rules: {
    // BEM 块/元素/修饰命名；额外放行 is- 状态类
    'selector-class-pattern': '^[a-z][a-z0-9-]*(__[a-z0-9-]+)?(--[a-z0-9-]+)?$|^is-[a-zA-Z]+$',

    // 安卓 WebView 兼容：媒体区间语法（width <= 768px）在旧内核不支持，保留 max-width 前缀写法
    'media-feature-range-notation': 'prefix',
    // 安卓 WebView 兼容：颜色函数保留 legacy 逗号写法
    'color-function-notation': 'legacy',
    'color-function-alias-notation': 'with-alpha',
    // 项目 alpha 一律写小数（0.82）
    'alpha-value-notation': 'number',

    // .sr-only 类无障碍隐藏依赖 clip: rect(0 0 0 0)，老内核无 clip-path，不视为应废弃
    'property-no-deprecated': [true, { ignoreProperties: ['clip'] }],
    // 设计系统文件名非 partial（tokens.base.scss 无前导下划线），@use 必须带扩展名
    'scss/load-partial-extension': null,
    'value-keyword-case': ['lower', { ignoreProperties: ['font-family'] }]
  },

  // 设计系统文件豁免纯排版规则（紧凑的「变量表」注释与自定义属性紧贴是有意为之）
  overrides: [
    {
      files: ['src/styles/**'],
      rules: {
        'comment-empty-line-before': null,
        'custom-property-empty-line-before': null,
        'declaration-empty-line-before': null,
        'color-hex-length': null,
        'value-keyword-case': null
      }
    }
  ]
}
