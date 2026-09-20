import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

/**
 * 单测配置（刻意不复用 vite.config.js）
 *
 * 为什么独立：主配置是 MPA 三入口，rollupOptions.input 会把 index/pc/mobile 三个 HTML 当成构建目标，
 * 单测跑不起来；单测只需要 @ 与 @admin 两个别名。
 *
 * 环境策略：默认 node（shared/domain 全是纯函数，不需要 DOM）；
 * 组件与剪贴板用例在文件首行用 `// @vitest-environment jsdom` 单独声明。
 *
 * 覆盖率门槛只压 shared/domain（纯工具层，能被断言完全覆盖）；
 * views/stores/components 不设门槛 —— 为凑覆盖率写「快照式」用例没有回归价值。
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      '@admin': fileURLToPath(new URL('../hrm-admin/src', import.meta.url))
    }
  },
  test: {
    include: ['src/**/*.spec.js'],
    environment: 'node',
    coverage: {
      provider: 'v8',
      include: ['src/shared/domain/**'],
      reporter: ['text', 'json-summary'],
      thresholds: { statements: 90 }
    }
  }
})
