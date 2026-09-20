// 交付包 / 仓库通用静态服务：托管 dist/ 产物，只用 Node 内置模块，零第三方依赖
// 为什么不用 `npm run dev`：交付包不带 node_modules 与源码，而 Mock 数据全部打包在产物内，
// 纯静态托管即可运行，审核机器无需重新装依赖。
import { createServer } from 'node:http'
import { existsSync } from 'node:fs'
import { readFile, stat } from 'node:fs/promises'
import { extname, join, normalize, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

// 本文件在交付包中与 dist 同级，在仓库中位于 scripts/ 下（dist 在上一级），两种位置都要能直接跑
const SCRIPT_DIR = fileURLToPath(new URL('.', import.meta.url))
const CANDIDATES = [resolve(SCRIPT_DIR, 'dist'), resolve(SCRIPT_DIR, '..', 'dist')]
const ROOT = CANDIDATES.find(existsSync) || CANDIDATES[0]
const PORT = Number(process.env.PORT || 5188)

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf'
}

if (process.argv.includes('--help') || process.argv.includes('-h')) {
  console.log(`hrm-demo 静态预览服务（托管构建产物，需先 npm run build / build:prod）

用法：node <本文件路径> [--help]（仓库内为 node scripts/serve.mjs，交付包内为 node serve.mjs）
环境变量：PORT  监听端口，默认 5188
产物目录：${ROOT}`)
  process.exit(0)
}

const server = createServer(async (req, res) => {
  try {
    const pathname = decodeURIComponent(new URL(req.url, 'http://localhost').pathname)
    let filePath = resolve(join(ROOT, normalize(pathname)))
    // 目录穿越防护：normalize 之后仍必须落在 ROOT 内
    if (!filePath.startsWith(ROOT)) {
      res.writeHead(403).end('Forbidden')
      return
    }
    let info = await stat(filePath).catch(() => null)
    if (!info || info.isDirectory()) {
      // 只对「无扩展名的路径」做前端路由回退；缺资源的请求必须老实回 404 ——
      // 若也回 HTML，浏览器会拿 HTML 当 JS 模块解析，真实错误会被掩盖成难以定位的模块错误。
      if (extname(pathname)) {
        res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' }).end('404 Not Found: ' + pathname)
        return
      }
      // 回退目标不是 index.html 而是 pc.html：三个入口里只有「网页端」用 history 路由
      // （/dashboard、/parcel/sync 这类真实路径），移动端是 hash 路由（#/staff/home）、
      // 入口页是静态页，都不需要回退。若统一回入口页，刷新 PC 深链会跳到端选择页，
      // 看起来像「登录态丢了」。
      filePath = pathname === '/' ? join(ROOT, 'index.html') : join(ROOT, 'pc.html')
      info = await stat(filePath).catch(() => null)
      if (!info) {
        res.writeHead(404).end('404 Not Found')
        return
      }
    }
    res.writeHead(200, {
      'Content-Type': MIME[extname(filePath).toLowerCase()] || 'application/octet-stream',
      'Cache-Control': 'no-cache'
    })
    res.end(await readFile(filePath))
  } catch {
    res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' }).end('404 Not Found')
  }
}).listen(PORT, () => {
  console.log(`[hrm-demo] 静态服务已启动: http://localhost:${PORT}/`)
  console.log(`[hrm-demo] 产物目录: ${ROOT}`)
})

// 端口被占时要显式报错并退出：两个服务同时监听同一个端口时（IPv4/IPv6 各绑一个），
// 浏览器可能落到另一个服务上，表现为「产物明明更新了但页面还是旧的」，极难排查。
server.on('error', (err) => {
  if (err.code === 'EADDRINUSE') {
    console.error(`[hrm-demo] 端口 ${PORT} 已被占用，请先关闭占用它的进程，或换端口重新运行本脚本：`)
    console.error(`  先设置环境变量 PORT=5199（PowerShell: $env:PORT=5199 / CMD: set PORT=5199）再启动`)
  } else {
    console.error('[hrm-demo] 服务启动失败:', err.message)
  }
  process.exit(1)
})
