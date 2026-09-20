const CSV_TYPE = 'text/csv;charset=utf-8'

function escapeCsv(value) {
  const text = value == null ? '' : String(value)
  return /[",\n\r]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
}

/**
 * 前端生成 CSV 并下载
 * 为什么不在前端拼文件流：Mock 未提供 /parcels/export 接口（包裹是二期实体，导出契约未定）。
 * CSV 约定与 Mock 的一期导出保持一致：\ufeff BOM + CRLF，Excel 直接打开中文不乱码。
 * TODO(扩展): 二期定稿导出接口后改用 @admin/utils/download.js 的 saveResponseFile 消费文件流
 */
export function downloadCsv(filename, header, rows) {
  const content = `\ufeff${[header, ...rows].map((row) => row.map(escapeCsv).join(',')).join('\r\n')}`
  const url = URL.createObjectURL(new Blob([content], { type: CSV_TYPE }))
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}
