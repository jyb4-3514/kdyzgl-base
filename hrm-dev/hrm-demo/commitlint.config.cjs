/**
 * 提交信息校验 —— 对齐项目规范《类型: 中文简短描述》
 *
 * 与 @commitlint/config-conventional 的差异：
 * - 类型收紧为项目规定的 7 种
 * - 禁用 scope（项目规范无 scope 段）
 * - 标题长度收紧到 50 字
 * - 关闭 subject-case / subject-full-stop：描述为中文，大小写与句号规则无意义
 */
module.exports = {
  extends: ['@commitlint/config-conventional'],
  rules: {
    'type-enum': [2, 'always', ['feat', 'fix', 'refactor', 'docs', 'style', 'test', 'chore']],
    'scope-empty': [2, 'always'],
    'header-max-length': [2, 'always', 50],
    'subject-case': [0],
    'subject-full-stop': [0],
    'body-max-line-length': [0],
    'footer-max-line-length': [0]
  }
}
