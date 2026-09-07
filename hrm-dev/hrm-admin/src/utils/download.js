/**
 * 文件流下载工具
 * - 从响应头 Content-Disposition 解析文件名（支持 filename*=UTF-'' 与 filename= 两种形式）
 * - 解析失败时使用调用方传入的兜底文件名（含中文名，Blob 本地保存天然支持）
 */
export function saveResponseFile(response, fallbackFilename) {
  const disposition = (response.headers && response.headers['content-disposition']) || ''
  let filename = fallbackFilename
  if (disposition) {
    const starMatch = disposition.match(/filename\*=(?:UTF-8'')?([^;]+)/i)
    const plainMatch = disposition.match(/filename="?([^";]+)"?/i)
    if (starMatch && starMatch[1]) {
      try {
        filename = decodeURIComponent(starMatch[1].trim().replace(/^"|"$/g, ''))
      } catch (e) {
        filename = starMatch[1].trim()
      }
    } else if (plainMatch && plainMatch[1]) {
      filename = plainMatch[1].trim()
    }
  }

  const blob = new Blob([response.data])
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
  return filename
}

/** 生成 yyyyMMdd 格式日期串（导出兜底文件名用） */
export function formatDateCompact(date = new Date()) {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}${m}${d}`
}
