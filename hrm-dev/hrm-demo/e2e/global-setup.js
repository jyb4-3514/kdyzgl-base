import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const evidenceDir = fileURLToPath(new URL('./evidence/', import.meta.url))

/**
 * 每轮开跑前清理上一轮证据，并确认被测服务在线。
 * 服务必须由外部提供（端口 5188 strictPort），这里只做连通性体检，不负责拉起服务。
 */
export default async function globalSetup() {
  fs.mkdirSync(evidenceDir, { recursive: true })
  fs.rmSync(path.join(evidenceDir, 'artifacts'), { recursive: true, force: true })
  fs.writeFileSync(path.join(evidenceDir, 'metrics.json'), '[]', 'utf8')
  // 只清顶层的历史遗留图；shots/ 子目录保留，避免分文件执行时后一次运行把前一次的截图清掉
  for (const file of fs.readdirSync(evidenceDir)) {
    if (file.endsWith('.png')) fs.rmSync(path.join(evidenceDir, file))
  }

  const base = process.env.HRM_DEMO_BASE_URL || 'http://localhost:5188'
  const res = await fetch(`${base}/`)
  if (!res.ok) throw new Error(`dev server 未就绪：GET ${base}/ 返回 HTTP ${res.status}`)
  console.log(`[global-setup] dev server OK → ${base}；证据目录 → ${evidenceDir}`)
}
