/**
 * 分页归一（api.md 1.1）：pageNum 默认 1，pageSize 默认 10、最大 100
 * 越界页返回空列表但保留 total，与后端 LIMIT 行为一致。
 */
export function paginate(list, pageNum, pageSize) {
  const num = Math.max(1, Number.parseInt(pageNum, 10) || 1)
  const size = Math.min(100, Math.max(1, Number.parseInt(pageSize, 10) || 10))
  const start = (num - 1) * size
  return { total: list.length, pageNum: num, pageSize: size, list: list.slice(start, start + size) }
}
