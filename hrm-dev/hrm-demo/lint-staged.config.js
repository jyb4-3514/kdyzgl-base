/**
 * lint-staged：pre-commit 只检查暂存文件，避免一次提交顺带改掉无关文件。
 *
 * 执行顺序有意为之：先 eslint（逻辑与依赖边界）→ prettier（排版）→ stylelint（样式块），
 * 三者职责不重叠，eslint-config-prettier 已关掉会与 prettier 互改的规则。
 */
export default {
  '*.{js,vue}': ['eslint --fix'],
  '*.{js,vue,json,md,scss}': ['prettier --write'],
  '*.{scss,vue}': ['stylelint --fix']
}
