/**
 * shared/domain 公开入口（纯工具层，零 mock 依赖）
 * 展示端只从这里取工具函数；Mock 内部改走 shared/mock/util.js 的 re-export，两边同源。
 */
export * from './time.js'
export * from './sla.js'
export * from './pagination.js'
export * from './csv.js'
export * from './mask.js'
export * from './text.js'
export * from './workOrderText.js'
export * from './permission.js'
export * from './applyDataScope.js'
