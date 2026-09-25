import { defineConfig } from '@playwright/test'
import { fileURLToPath } from 'node:url'

/**
 * 员工端「驿站助手」端到端测试配置（B3 独立 config + 自有 baseURL 5189）
 *
 * ⚠️ 必须带 --config 运行（`npm run e2e`）：不带 --config 时 Playwright 默认 testDir 为当前目录，
 * 会把 vitest 的 src 下 *.spec.js 一起吞进运行器（两套框架 spec 同名不同语义）。
 *
 * 约束：
 * 1. 被测服务必须由外部启动（`npm run dev`，端口 5189 strictPort）；本配置不启动也不重启服务；
 * 2. `baseURL=http://localhost:5189`；应用以 `base:'/staff/'` 托管，故页面路径形如 `/staff/#/login`（hash 路由）；
 * 3. workers 固定 1：多 worker 会并发打同一 dev server，且 Mock 数据落在同一浏览器 storage，串行才能归因；
 * 4. 通用工具（harness）单点在 workspace 根 `hrm-clients/e2e-utils/`，各端引用不复制（ADR D10）。
 */
const e2eDir = fileURLToPath(new URL('.', import.meta.url))

export default defineConfig({
  testDir: e2eDir,
  testMatch: '**/*.spec.js',
  outputDir: fileURLToPath(new URL('./evidence/artifacts', import.meta.url)),
  globalSetup: fileURLToPath(new URL('./global-setup.js', import.meta.url)),
  timeout: 180_000,
  expect: { timeout: 30_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  forbidOnly: true,
  reporter: [
    ['list'],
    ['json', { outputFile: fileURLToPath(new URL('./evidence/pw-results.json', import.meta.url)) }],
    ['html', { outputFolder: fileURLToPath(new URL('./playwright-report', import.meta.url)), open: 'never' }]
  ],
  use: {
    baseURL: 'http://localhost:5189',
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
    viewport: { width: 375, height: 812 },
    actionTimeout: 25_000,
    navigationTimeout: 60_000,
    trace: 'off',
    video: 'off'
  },
  projects: [{ name: 'chromium', use: { browserName: 'chromium' } }]
})
