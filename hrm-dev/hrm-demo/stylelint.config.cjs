/**
 * Stylelint 配置 —— 快递驿站智汇系统 · 三端 Demo
 *
 * 只 lint src 下的 scss 与 Vue SFC 样式块（脚本文件交给 ESLint/Prettier）。
 * 一期只读资产 hrm-admin 必须忽略，禁止被改写。
 *
 * 放行原则：设计系统文件（tokens*.scss / mobile.scss / element-overrides.scss）的写法优先保证语义与兼容性，
 * 与之冲突的规则一律放宽，不为「过 lint」改设计系统源码。
 */
module.exports = {
  extends: ['stylelint-config-standard-scss', 'stylelint-config-recommended-vue/scss'],
  ignoreFiles: ['../hrm-admin/**', 'hrm-admin/**', 'node_modules/**', 'dist/**'],
  rules: {
    // BEM 块/元素/修饰命名；额外放行 is- 状态类——其中 is-parcelTotal 等由 JS 侧 sort key 直接拼接，必须同名
    'selector-class-pattern': '^[a-z][a-z0-9-]*(__[a-z0-9-]+)?(--[a-z0-9-]+)?$|^is-[a-zA-Z]+$',

    // 安卓 WebView 兼容：媒体区间语法（width <= 768px）在旧内核不支持，保留 max-width 前缀写法
    'media-feature-range-notation': 'prefix',
    // 安卓 WebView 兼容：颜色函数保留 legacy 逗号写法，不改 rgb(r g b / a) 现代语法
    'color-function-notation': 'legacy',
    'color-function-alias-notation': 'with-alpha',
    // 项目 alpha 一律写小数（0.82），不改百分比
    'alpha-value-notation': 'number',

    // .sr-only 类无障碍隐藏依赖 clip: rect(0 0 0 0)，老内核无 clip-path，不视为应废弃
    'property-no-deprecated': [true, { ignoreProperties: ['clip'] }],
    // 设计系统文件名非 partial（tokens.base.scss 无前导下划线），@use 必须带扩展名
    'scss/load-partial-extension': null,
    // font-family 保留原始大小写（BlinkMacSystemFont / Arial）
    'value-keyword-case': ['lower', { ignoreProperties: ['font-family'] }]
  },

  /**
   * 设计系统文件豁免纯排版规则：
   * tokens / 主题文件是紧凑的「变量表」，注释与自定义属性紧贴是有意为之（便于对照阅读），
   * 补空行只会把 100 行变量表撑成 200 行。色值本身由 scripts/gen-element-tokens.mjs 兜底校验。
   */
  overrides: [
    {
      files: ['src/shared/styles/**', 'src/mobile/styles/**', 'src/pc/styles/**'],
      rules: {
        'comment-empty-line-before': null,
        'custom-property-empty-line-before': null,
        'declaration-empty-line-before': null,
        'color-hex-length': null,
        // --font-* 里存的是 font-family 值，大小写要与人名式写法一致（BlinkMacSystemFont）
        'value-keyword-case': null
      }
    }
  ]
}
