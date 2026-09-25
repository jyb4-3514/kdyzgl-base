import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const evidenceDir = fileURLToPath(new URL('./evidence/', import.meta.url))

/**
 * 每轮开跑前清理上一轮证据，并确认被测服务在线。
 * 服务必须由外部提供（`npm run dev`，端口 5189 strictPort），这里只做连通性体检，不负责拉起服务。
 */
export default async function globalSetup() {
  fs.mkdirSync(evidenceDir, { recursive: true })
  fs.rmSync(path.join(evidenceDir, 'artifacts'), { recursive: true, force: true })
  fs.writeFileSync(path.join(evidenceDir, 'metrics.json'), '[]', 'utf8')
  for (const file of fs.readdirSync(evidenceDir)) {
    if (file.endsWith('.png')) fs.rmSync(path.join(evidenceDir, file))
  }

  // 应用以 base:'/staff/' 托管，健康检查探 /staff/
  const base = process.env.HRM_STAFF_BASE_URL || 'http://localhost:5189'
  const res = await fetch(`${base}/staff/`)
  if (!res.ok) throw new Error(`dev server 未就绪：GET ${base}/staff/ 返回 HTTP ${res.status}`)
  console.log(`[global-setup] staff dev server OK → ${base}/staff/；证据目录 → ${evidenceDir}`)
}
