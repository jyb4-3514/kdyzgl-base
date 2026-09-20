/**
 * CSV 文件流公共工具（员工导出 / 考勤导出 / 配置导入共用）
 * 收在纯工具层：不带任何 mock 依赖，生产构建下仍可被真实接口复用。
 */

/** CSV MIME：带 charset，Mock 无真实 xlsx 能力，文件流一律走 CSV */
export const CSV_TYPE = 'text/csv;charset=utf-8'

/** CSV 单元格转义：含逗号 / 引号 / 换行时用双引号包裹，内部引号翻倍 */
export function escapeCsv(value) {
  const text = value == null ? '' : String(value)
  return /[",\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
}

/** 生成 CSV Blob：带 BOM 便于 Excel 直接打开中文 */
export function toCsvBlob(rows) {
  const content = `\ufeff${rows.map((row) => row.map(escapeCsv).join(',')).join('\r\n')}`
  return new Blob([content], { type: CSV_TYPE })
}

/** 附件响应头（文件名走 RFC 5987 编码，避免中文乱码） */
export function csvDisposition(filename) {
  return `attachment; filename*=UTF-8''${encodeURIComponent(filename)}`
}

/**
 * CSV 文本 → 二维数组（真实解析，不是 split(',')）
 *
 * 为什么逐字符扫描：单元格可能含逗号、换行、双引号转义，用 split 一定会把
 * 「备注里带逗号」的一行拆错列；导入是外部输入，解析器必须按 RFC 4180 处理。
 * 兼容点（设计 D.3）：忽略 BOM、CRLF / LF 混用、不必要的包裹引号、末尾空行。
 */
export function parseCsv(text) {
  const source = String(text == null ? '' : text).replace(/^\ufeff/, '')
  const rows = []
  let row = []
  let cell = ''
  let quoted = false

  for (let i = 0; i < source.length; i += 1) {
    const char = source[i]
    if (quoted) {
      if (char === '"') {
        if (source[i + 1] === '"') {
          cell += '"' // 引号翻倍 = 一个字面引号
          i += 1
        } else {
          quoted = false
        }
      } else {
        cell += char
      }
      continue
    }
    if (char === '"') {
      quoted = true
      continue
    }
    if (char === ',') {
      row.push(cell)
      cell = ''
      continue
    }
    if (char === '\r' || char === '\n') {
      // CRLF 只推进一次：遇到 \r 且下一个是 \n 时吞掉 \n
      if (char === '\r' && source[i + 1] === '\n') i += 1
      row.push(cell)
      rows.push(row)
      row = []
      cell = ''
      continue
    }
    cell += char
  }
  row.push(cell)
  rows.push(row)
  // 末尾空行（Excel 另存常留一行空）不算数据：整行仅一个空单元格即剔除
  while (rows.length > 1 && rows[rows.length - 1].every((item) => item === '')) rows.pop()
  return rows
}
