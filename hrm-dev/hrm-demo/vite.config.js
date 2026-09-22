import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { fileURLToPath, URL } from 'node:url'

/**
 * 三端演示 Demo 构建配置
 * - MPA 三入口：index（端选择）/ pc（网页端）/ mobile（移动端），物理隔离 Element Plus 与 Vant 的全局样式
 *   （生产构建只出 pc / mobile：index 与 demo 剧本属演示资产，不进生产包）
 * - @admin 别名指向一期 hrm-admin/src（只读复用，Demo 不复制源码、不改一期文件）
 * - 依赖版本与 hrm-admin 同版本线（Vite ^6），故锁定 build.rollupOptions 写法
 * - Element Plus 按需引入（T04）：官方推荐 unplugin-vue-components + unplugin-auto-import + ElementPlusResolver，
 *   见 https://element-plus.org/en-US/guide/quickstart.html「On-demand Import / Auto import Recommend」
 */
export default defineConfig(({ mode }) => ({
  plugins: [
    vue(),
    /**
     * Element Plus 按需引入。两个插件都只作用于「源码里出现但未 import 的 El* 标识」，
     * 已显式 import 的组件不受影响 —— 因此 @admin 一期源码（只读）里的 <el-*> 与 ElMessage 无需改动即可继续工作。
     * dts: false —— 本工程无 TypeScript，置 false 同时避免构建时在仓库里生成 components.d.ts / auto-imports.d.ts。
     */
    AutoImport({
      /**
       * 不加 'vue' / 'vue-router' 预设：本工程（含冻结的 @admin 与 mock 层）一律显式 import，
       * 预设只会往文件里注入多余的自动导入，反而扩大行为差异面。这里只用解析器兜住未来可能出现的
       * 「未 import 的 El* 函数式调用」（对应 ElementPlusResolver 的 named 解析与样式副作用）。
       */
      imports: [],
      resolvers: [ElementPlusResolver()],
      dts: false
    }),
    Components({
      // 解析器默认 include 覆盖所有 .vue（@admin 别名下的文件同样命中），exclude 只排除 node_modules/.git
      resolvers: [ElementPlusResolver()],
      dts: false
    }),
    {
      name: 'hrm-demo-mock-guard',
      /**
       * 演示模式必须显式开启 Mock —— 快速失败优于静默关闭。
       * 漏配开关时页面会静默走真实 HTTP，而演示环境没有后端，表现为全屏空状态、排查成本高。
       */
      config(_config, { mode: hookMode }) {
        const env = loadEnv(hookMode, process.cwd(), 'VITE_')
        if (hookMode === 'demo' && env.VITE_MOCK_ENABLED !== 'true') {
          throw new Error('演示模式（--mode demo）必须显式设置 VITE_MOCK_ENABLED=true，请检查 .env.demo')
        }
      }
    },
    {
      /**
       * 开发服务器的 history 回退目标修正（仅 dev 生效，不影响构建产物）。
       * Vite 的 MPA 模式下未知路径会统一回退到入口 index.html（端选择页），
       * 而三个入口里只有「网页端」用 history 路由（/dashboard、/parcel/sync 这类真实路径），
       * 移动端是 hash 路由、入口页是静态页，都不需要回退。
       * 不修正的话，在 dev 下刷新 PC 深链会跳到端选择页，看起来像「登录态丢了」。
       */
      name: 'hrm-demo-history-fallback',
      configureServer(server) {
        // Vite 内部模块（/@vite/client、/@id/*、/@fs/*、/node_modules/*）同样不带扩展名，但它们不是页面导航：
        // 一旦被改写成 HTML，浏览器会拿 HTML 当 JS 模块解析 → SFC 导入失败 → 两个入口整页白屏
        // （e2e/00-dev-server.spec.js 对这两条契约做长期回归）
        const VITE_INTERNAL = /^\/(?:@vite|@id|@fs|node_modules)\//
        server.middlewares.use((req, res, next) => {
          const path = (req.url || '').split('?')[0]
          const isRootOrFile = path === '/' || /\.[a-z0-9]+$/i.test(path)
          if (req.method === 'GET' && !isRootOrFile && !VITE_INTERNAL.test(path)) req.url = '/pc.html'
          next()
        })
      }
    }
  ],
  resolve: {
    /**
     * 依赖去重（必须保留，删掉即白屏）：
     * @admin 别名指向 ../hrm-admin/src，而 hrm-admin 自带 node_modules，pinia / vue / axios 各有独立一份。
     * 不去重时同一依赖被解析成两份物理副本、打包出两个互不相干的模块实例，症状：
     * pinia 的 activePinia 分裂 —— main.js 用 A 副本 createPinia() 并 app.use(pinia)，
     * 而 @admin/stores/auth.js 的 defineStore() 读的是 B 副本的 activePinia（恒为 undefined），
     * useAuthStore() 直接抛 Cannot read properties of undefined (reading '_s')，页面白屏。
     * vue 同理：两份 Vue 会形成两套响应式系统，ref/组件互相不认。
     * dedupe 强制统一从本项目 node_modules 解析唯一副本。
     * 实测证据：去重前构建产物中 pinia 内部 `_s.get(` 分别出现在 pc-*.js 与 preload-helper-*.js 两个 chunk。
     */
    dedupe: ['vue', 'vue-router', 'pinia', 'axios', 'element-plus', '@element-plus/icons-vue', 'vant'],
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      '@admin': fileURLToPath(new URL('../hrm-admin/src', import.meta.url))
    }
  },
  server: {
    port: 5188,
    strictPort: true,
    open: false,
    /**
     * 可选反向代理：导出 VITE_API_PROXY_TARGET 时才把 /api/v1 转发到本机后端；
     * 未配置即不代理 —— 演示态保持零后端，不与 Mock 适配器抢流量。
     */
    proxy: process.env.VITE_API_PROXY_TARGET
      ? { '/api/v1': { target: process.env.VITE_API_PROXY_TARGET, changeOrigin: true } }
      : undefined,
    /**
     * 实测结论 Q3（A/B 已验证，见 README「实测结论」）：
     * 不配置该项时，访问 /@fs/.../hrm-admin/src/styles/index.scss 返回 403；
     * Vite 默认 fs.allow 取「工作区根」，而本仓库根无 lockfile/workspaces（lockfile 只在 hrm-admin 内），
     * 探测不到跨目录的工作区根，故必须显式放行上一级 hrm-dev 目录。
     * 依据：Vite 官方《开发服务器选项》server.fs.allow（默认值为「工作区根」）
     */
    fs: {
      allow: ['..']
    }
  },
  build: {
    outDir: 'dist',
    /**
     * sourcemap：生产出 hidden（生成 .map 供线上排障，但不写 sourceMappingURL 注释，浏览器不自动加载），
     * 与运维约定 dist 下 .map 不可公开访问（Nginx 侧禁止直接下载）；演示态关闭，产物直接给审核方。
     */
    sourcemap: mode === 'production' ? 'hidden' : false,
    /**
     * TODO(扩展): PC 入口用 history 路由，构建基址必须是 '/'（Vite 默认）；
     * 安卓 WebView 若以 file:// 加载离线包则需要 base: './'，而 base 是全局配置、无法按入口区分，二者硬冲突。
     * 本次不实施离线包方案：ESM 在 file:// 下能否加载，本机无 Android SDK 无法验证。
     * 若需 file:// 离线包，须先验证 ESM 在 WebView 下能否加载（见 docs 改进建议书附录 B 第 4 项）。
     */
    rollupOptions: {
      // 演示态出三入口；生产只出 pc / mobile，端选择页与 demo 剧本不进生产包
      input:
        mode === 'production'
          ? {
              pc: fileURLToPath(new URL('./pc.html', import.meta.url)),
              mobile: fileURLToPath(new URL('./mobile.html', import.meta.url))
            }
          : {
              index: fileURLToPath(new URL('./index.html', import.meta.url)),
              pc: fileURLToPath(new URL('./pc.html', import.meta.url)),
              mobile: fileURLToPath(new URL('./mobile.html', import.meta.url))
            }
    }
  }
}))
