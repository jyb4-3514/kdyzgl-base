import { defineConfig } from '@playwright/test'
import { fileURLToPath } from 'node:url'

/**
 * 三端 Demo 端到端测试配置
 *
 * ⚠️ 必须带 --config 运行（用 `npm run e2e` 即可）：不带 --config 时 Playwright 的默认 testDir 为当前工作目录，
 * 会把 vitest 的 src 下 *.spec.js 一起吞进 Playwright 运行器并全部报错（两套框架的 spec 同名不同语义）。
 * 配置文件放在 e2e/ 下而不是工程根，就是为了让 --config 成为唯一入口、不给后人留下踩坑的机会。
 *
 * 约束：
 * 1. 被测服务必须由外部启动（http://localhost:5188，strictPort），测试不启动也不重启服务；
 *    B2 网络条件用例与 A2-2 深链回退断言依赖 dev server 的未打包产物（pc.html 内引用 /src/pc/main.js），
 *    故前置条件为 `npm run dev`（跑完记得停掉，端口 5188 被占用时用 PORT 换端口）；
 * 2. workers 固定为 1：多 worker 会并发打同一台 dev server，且 Mock 数据落在同一浏览器 storage，
 *    串行执行才能保证「截图 / 耗时 / 报错」三类证据可归因；
 * 3. 配置与产物全部落在 e2e/ 目录内，不改动 src/ 与 vite.config.js。
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
    // HTML 报告供人查看（npm run e2e:report）；报告产物同样落在 e2e/ 内并被 .gitignore 排除
    ['html', { outputFolder: fileURLToPath(new URL('./playwright-report', import.meta.url)), open: 'never' }]
  ],
  use: {
    baseURL: 'http://localhost:5188',
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
    viewport: { width: 1440, height: 900 },
    actionTimeout: 25_000,
    navigationTimeout: 60_000,
    trace: 'off',
    video: 'off'
  },
  projects: [{ name: 'chromium', use: { browserName: 'chromium' } }]
})
