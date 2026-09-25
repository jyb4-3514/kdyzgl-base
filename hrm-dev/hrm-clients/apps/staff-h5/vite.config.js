import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
import { readFileSync } from 'node:fs'

/** package.json 的 version：注入为构建期常量 __APP_VERSION__（与其它端同口径，不硬编码版本串） */
const pkg = JSON.parse(readFileSync(new URL('./package.json', import.meta.url), 'utf-8'))

/**
 * 员工端「驿站助手」构建配置（B3 独立工程）
 *
 * 关键纪律（ADR §4.1 / §4.2，逐条保留）：
 * - 单入口 index.html（hash 路由，无需服务端 rewrite 兜底）；`base: '/staff/'`（终态子路径，ADR B-5）
 * - 演示态/生产态两条构建：demo 含 Mock、production 剥离 Mock（动态 import 由 Rollup 整块剔除）
 * - fail-safe：仅 `VITE_MOCK_ENABLED === 'true'` 才装 Mock；`--mode demo` 下未开即抛错（快速失败）
 * - `resolve.dedupe` 保留：workspace 下若出现多份 vue/pinia（app 内独立 install）会 activePinia 分裂 → 白屏
 * - sourcemap：生产 `hidden`（生成 .map 不写 sourceMappingURL），Nginx 侧禁公开下载
 */
export default defineConfig(({ mode }) => ({
  base: '/staff/',
  plugins: [
    vue(),
    {
      name: 'staff-mock-guard',
      /** 演示模式必须显式开启 Mock —— 快速失败优于静默关闭（漏配会全屏空状态、排查成本高） */
      config(_config, { mode: hookMode }) {
        const env = loadEnv(hookMode, process.cwd(), 'VITE_')
        if (hookMode === 'demo' && env.VITE_MOCK_ENABLED !== 'true') {
          throw new Error('演示模式（--mode demo）必须显式设置 VITE_MOCK_ENABLED=true，请检查 .env.demo')
        }
      }
    }
  ],
  define: {
    __APP_VERSION__: JSON.stringify(pkg.version)
  },
  resolve: {
    dedupe: ['vue', 'vue-router', 'pinia', 'axios', 'vant'],
    /**
     * 共享包指向 workspace 真源（而非 node_modules 软链路径）：
     * Vite/插件（如 @vitejs/plugin-vue）默认按路径排除 node_modules，软链路径会把共享包内的 .vue
     * 当成 JS 解析（`invalid JS syntax`）→ 中立页无法编译。别名到真实源码路径即可正常编译。
     */
    alias: [
      { find: /^@$/, replacement: fileURLToPath(new URL('./src', import.meta.url)) },
      { find: /^@\//, replacement: `${fileURLToPath(new URL('./src', import.meta.url))}/` },
      { find: /^@kdyzgl\/shared$/, replacement: fileURLToPath(new URL('../../packages/shared/src/index.js', import.meta.url)) },
      { find: /^@kdyzgl\/shared\//, replacement: `${fileURLToPath(new URL('../../packages/shared/src', import.meta.url))}/` },
      { find: /^@kdyzgl\/api-client$/, replacement: fileURLToPath(new URL('../../packages/api-client/src/index.js', import.meta.url)) },
      { find: /^@kdyzgl\/api-client\//, replacement: `${fileURLToPath(new URL('../../packages/api-client/src', import.meta.url))}/` },
      { find: /^@kdyzgl\/mock$/, replacement: fileURLToPath(new URL('../../packages/mock/src/index.js', import.meta.url)) },
      { find: /^@kdyzgl\/mock\//, replacement: `${fileURLToPath(new URL('../../packages/mock/src', import.meta.url))}/` }
    ]
  },
  server: {
    port: 5189,
    strictPort: true,
    open: false,
    // workspace 共享包位于 apps/ 同级 packages/，放行工作区根（否则 dev 读共享包源码 403）
    fs: {
      allow: ['..', '../..']
    },
    /**
     * 可选反向代理：导出 VITE_API_PROXY_TARGET 时才把 /api/v1 转发到本机后端；
     * 未配置即不代理 —— 演示态保持零后端，不与 Mock 适配器抢流量。
     */
    proxy: process.env.VITE_API_PROXY_TARGET
      ? { '/api/v1': { target: process.env.VITE_API_PROXY_TARGET, changeOrigin: true } }
      : undefined
  },
  build: {
    outDir: 'dist',
    sourcemap: mode === 'production' ? 'hidden' : false
  }
}))
