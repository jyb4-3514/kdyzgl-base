import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

/**
 * 单测配置 —— 管理端「驿站精灵」（与 vite.config.js 分离：主配置是应用入口，单测只需别名）
 *
 * 环境策略：默认 node（纯逻辑用）；组件与路由用例在文件首行 `// @vitest-environment jsdom` 单独声明。
 * 组件测试导入共享包 `@kdyzgl/shared/ui/*`（由 workspace node_modules 解析到 packages/ 真源）。
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    dedupe: ['vue', 'vue-router', 'pinia'],
    /** 共享包别名到 workspace 真源（同 vite.config.js）：避免 node_modules 软链路径下 .vue 不被 plugin-vue 编译 */
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
  test: {
    include: ['src/**/*.spec.js'],
    environment: 'node',
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json-summary']
    }
  }
})
