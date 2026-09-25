/**
 * 契约类型（ADR §3.5 第 5 项）
 *
 * 为什么放这里：接口入参/出参契约须单一真源，避免各端各写一份 JSDoc。
 * 工程无 TypeScript 构建链（沿用现状），故一律用 JSDoc `@typedef` 表达，不引入 .d.ts / ts 编译。
 *
 * TODO(扩展): B2 起按 `docs/api.md` 逐端点补 `@typedef`（入参 / 出参 / 错误码），
 * 并加 `verify:contracts` 静态校验（断言端点覆盖）。B1 仅占位，不进任何构建。
 */

/**
 * 统一响应包络（`docs/api.md` 第 1 章）
 * @typedef {Object} ApiEnvelope
 * @property {number} code    业务码，200 为成功
 * @property {string} [message]
 * @property {*} [data]
 */

export {}
