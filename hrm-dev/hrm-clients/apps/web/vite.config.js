import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { fileURLToPath, URL } from 'node:url'
import { readFileSync } from 'node:fs'

/** package.json 的 version：注入为构建期常量 __APP_VERSION__（与其它端同口径，不硬编码版本串） */
const pkg = JSON.parse(readFileSync(new URL('./package.json', import.meta.url), 'utf-8'))

/**
 * 网页端（PC 管理后台）构建配置（B5 独立工程）
 *
 * 关键纪律（ADR §4.1 / §4.2，逐条保留）：
 * - 单入口 index.html + history 路由；`base: '/web/'`（终态子路径，ADR B-5）→ 需 Nginx `try_files` 兜底深链
 * - 演示态/生产态两条构建：demo 含 Mock、production 剥离 Mock（动态 import 由 Rollup 整块剔除）
 * - fail-safe：仅 `VITE_MOCK_ENABLED === 'true'` 才装 Mock；`--mode demo` 下未开即抛错（快速失败）
 * - `resolve.dedupe` 保留：@admin 只读引用的一期工程自带 node_modules，不去重会 activePinia 分裂 → 白屏
 * - sourcemap：生产 `hidden`（生成 .map 不写 sourceMappingURL），Nginx 侧禁公开下载
 */
export default defineConfig(({ mode }) => ({
  base: '/web/',
  plugins: [
    vue(),
    /**
     * Element Plus 按需引入（官方推荐 unplugin-vue-components + unplugin-auto-import）。
     * 两个插件只作用于「源码里出现但未 import 的 El* 标识」，显式 import 的组件不受影响 ——
     * 因此 @admin 一期源码（只读）里的 <el-*> 与 ElMessage 无需改动即可继续工作。
     * dts: false —— 本工程无 TypeScript，同时避免构建时在仓库里生成 components.d.ts / auto-imports.d.ts。
     */
    AutoImport({ imports: [], resolvers: [ElementPlusResolver()], dts: false }),
    Components({ resolvers: [ElementPlusResolver()], dts: false }),
    {
      name: 'web-mock-guard',
      /** 演示模式必须显式开启 Mock —— 快速失败优于静默关闭（漏配会全屏空状态、排查成本高） */
      config(_config, { mode: hookMode }) {
        const env = loadEnv(hookMode, process.cwd(), 'VITE_')
        if (hookMode === 'demo' && env.VITE_MOCK_ENABLED !== 'true') {
          throw new Error('演示模式（--mode demo）必须显式设置 VITE_MOCK_ENABLED=true，请检查 .env.demo')
        }
      }
    },
    {
      /**
       * history 深链回退目标修正（仅 dev 生效，不影响构建产物）。
       * 本端 `base:'/web/'` + history 路由：`/web/dashboard` 这类无扩展名路径必须回退到 /web/index.html，
       * 否则 dev 下刷新深链会 404。
       * Vite 内部虚拟模块（/@vite/client、/@id/*、/@fs/*、/node_modules/*）同样不带扩展名，
       * 但它们不是页面导航：一旦被改写成 HTML，浏览器会拿 HTML 当 JS 模块解析 → SFC 导入失败 → 整页白屏。
       */
      name: 'web-history-fallback',
      configureServer(server) {
        const VITE_INTERNAL = /^\/(?:web\/)?(?:@vite|@id|@fs|node_modules)\//
        server.middlewares.use((req, res, next) => {
          const path = (req.url || '').split('?')[0]
          const isRootOrFile = path === '/' || path === '/web/' || /\.[a-z0-9]+$/i.test(path)
          if (req.method === 'GET' && !isRootOrFile && !VITE_INTERNAL.test(path)) req.url = '/web/index.html'
          next()
        })
      }
    }
  ],
  define: {
    __APP_VERSION__: JSON.stringify(pkg.version)
  },
  /**
   * 依赖预打包（消除 dev 期「运行中才发现新依赖 → Vite 整页 reload」）。
   *
   * 为什么必须显式 include：Element Plus 按需样式由 unplugin-vue-components 在 **SFC 转换期**注入，
   * Vite 的静态依赖扫描（esbuild）看不到这些裸导入，只能在浏览器真正走到该路由时才发现 →
   * 触发 `optimized dependencies changed. reloading` 整页刷新。这会打断 SPA 内路由跳转，
   * 使 e2e「点菜单 → 断言 URL 变化」在**冷缓存首跑**必然随机失败（实测 A2-1/A5-2/A5-4）。
   * 显式列出后启动即预打包，运行期不再发现新依赖。
   * TODO(扩展): 新增 Element Plus 组件时，把其 `element-plus/es/components/<name>/style/css` 补进本列表
   */
  optimizeDeps: {
    include: [
      'element-plus/es',
      'element-plus/es/components/base/style/css',
      'element-plus/es/components/message/style/css',
      'element-plus/es/components/message-box/style/css',
      'element-plus/es/components/notification/style/css',
      'element-plus/es/components/loading/style/css',
      'element-plus/es/components/config-provider/style/css',
      'element-plus/es/components/card/style/css',
      'element-plus/es/components/tabs/style/css',
      'element-plus/es/components/tab-pane/style/css',
      'element-plus/es/components/form/style/css',
      'element-plus/es/components/button/style/css',
      'element-plus/es/components/form-item/style/css',
      'element-plus/es/components/input/style/css',
      'element-plus/es/components/alert/style/css',
      'element-plus/es/components/container/style/css',
      'element-plus/es/components/main/style/css',
      'element-plus/es/components/header/style/css',
      'element-plus/es/components/dropdown/style/css',
      'element-plus/es/components/dropdown-menu/style/css',
      'element-plus/es/components/dropdown-item/style/css',
      'element-plus/es/components/breadcrumb/style/css',
      'element-plus/es/components/breadcrumb-item/style/css',
      'element-plus/es/components/aside/style/css',
      'element-plus/es/components/menu/style/css',
      'element-plus/es/components/sub-menu/style/css',
      'element-plus/es/components/menu-item/style/css',
      'element-plus/es/components/icon/style/css',
      'element-plus/es/components/row/style/css',
      'element-plus/es/components/col/style/css',
      'element-plus/es/components/skeleton/style/css',
      'element-plus/es/components/radio-group/style/css',
      'element-plus/es/components/radio-button/style/css',
      'element-plus/es/components/radio/style/css',
      'element-plus/es/components/progress/style/css',
      'element-plus/es/components/upload/style/css',
      'element-plus/es/components/dialog/style/css',
      'element-plus/es/components/date-picker/style/css',
      'element-plus/es/components/pagination/style/css',
      'element-plus/es/components/table/style/css',
      'element-plus/es/components/table-column/style/css',
      'element-plus/es/components/tag/style/css',
      'element-plus/es/components/select/style/css',
      'element-plus/es/components/option/style/css',
      'element-plus/es/components/tree-select/style/css',
      'element-plus/es/components/drawer/style/css',
      'element-plus/es/components/input-number/style/css',
      'element-plus/es/components/time-select/style/css',
      'element-plus/es/components/switch/style/css',
      'element-plus/es/components/descriptions/style/css',
      'element-plus/es/components/descriptions-item/style/css',
      'element-plus/es/components/timeline/style/css',
      'element-plus/es/components/timeline-item/style/css',
      'element-plus/es/components/collapse/style/css',
      'element-plus/es/components/collapse-item/style/css',
      'element-plus/es/components/steps/style/css',
      'element-plus/es/components/step/style/css',
      'element-plus/es/components/tooltip/style/css'
    ]
  },
  resolve: {
    /**
     * 依赖去重（必须保留，删掉即白屏）：@admin 别名指向 ../../../hrm-admin/src，而 hrm-admin 自带 node_modules，
     * vue / pinia / axios / element-plus 各有独立一份。不去重时同一依赖被解析成两份物理副本：
     * pinia 的 activePinia 分裂 —— main.js 用 A 副本 createPinia()，而 @admin/stores/auth 的 defineStore()
     * 读的是 B 副本的 activePinia（恒为 undefined），useAuthStore() 直接抛 `Cannot read properties of undefined (reading '_s')`。
     */
    dedupe: ['vue', 'vue-router', 'pinia', 'axios', 'element-plus', '@element-plus/icons-vue'],
    alias: [
      { find: /^@$/, replacement: fileURLToPath(new URL('./src', import.meta.url)) },
      { find: /^@\//, replacement: `${fileURLToPath(new URL('./src', import.meta.url))}/` },
      /**
       * @admin 只读引用一期 PC 端（D2 短期保留）：登录页/请求层/登录态均已自建，仅余
       * 一期页面（员工/部门/驿站/个人中心等 9 张）、样式基座与下载工具为只读消费。零修改 hrm-admin。
       */
      { find: /^@admin$/, replacement: fileURLToPath(new URL('../../../hrm-admin/src', import.meta.url)) },
      { find: /^@admin\//, replacement: `${fileURLToPath(new URL('../../../hrm-admin/src', import.meta.url))}/` },
      /**
       * 共享包指向 workspace 真源（而非 node_modules 软链路径）：
       * Vite/插件（如 @vitejs/plugin-vue）默认按路径排除 node_modules，软链路径会把共享包内的 .vue
       * 当成 JS 解析（`invalid JS syntax`）。别名到真实源码路径即可正常编译。
       */
      { find: /^@kdyzgl\/shared$/, replacement: fileURLToPath(new URL('../../packages/shared/src/index.js', import.meta.url)) },
      { find: /^@kdyzgl\/shared\//, replacement: `${fileURLToPath(new URL('../../packages/shared/src', import.meta.url))}/` },
      { find: /^@kdyzgl\/api-client$/, replacement: fileURLToPath(new URL('../../packages/api-client/src/index.js', import.meta.url)) },
      { find: /^@kdyzgl\/api-client\//, replacement: `${fileURLToPath(new URL('../../packages/api-client/src', import.meta.url))}/` },
      { find: /^@kdyzgl\/mock$/, replacement: fileURLToPath(new URL('../../packages/mock/src/index.js', import.meta.url)) },
      { find: /^@kdyzgl\/mock\//, replacement: `${fileURLToPath(new URL('../../packages/mock/src', import.meta.url))}/` }
    ]
  },
  server: {
    port: 5191,
    strictPort: true,
    open: false,
    // workspace 共享包位于 apps/ 同级 packages/，@admin 位于更上一级 hrm-dev/，放行以防 dev 读源码 403
    fs: {
      allow: ['..', '../..', '../../..']
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
