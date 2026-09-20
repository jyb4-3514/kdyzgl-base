# e2e 端到端套件（Playwright）

三端 Demo 的浏览器级回归网：页面加载、导航与深链、刷新与会话、表单、渲染、六档视口、网络条件。

## 怎么跑

```bash
# 1. 前置：被测服务必须已在 5188 端口就绪（strictPort），测试自身不拉起服务
npm run dev

# 2. 另开一个终端跑套件（串行，约 3-7 分钟）
npm run e2e

# 3. 看 HTML 报告
npm run e2e:report
```

- ⚠️ **必须带 `--config e2e/playwright.config.js`**（`npm run e2e` 已带）。直接 `npx playwright test` 会以
  当前目录为 `testDir`，把 vitest 的 `src/**/*.spec.js` 一起吞进 Playwright 运行器并全部报错。
- 用例依赖 **dev server 的未打包产物**（如 A2-2 断言深链回退到 `pc.html`，其内含 `/src/pc/main.js`），
  故不能改用 `dist` 静态托管。
- 端口被占用时先停掉占用进程；套件跑完记得停掉 dev server。

## 产物落在哪

| 产物                            | 路径                                                 |
| ------------------------------- | ---------------------------------------------------- |
| 截图证据 / 实测指标 / 结果 JSON | `e2e/evidence/`（`metrics.json`、`pw-results.json`） |
| 失败工件（error-context 等）    | `e2e/evidence/artifacts/`                            |
| HTML 报告                       | `e2e/playwright-report/`                             |

以上三类均已在 `e2e/.gitignore` 中排除，不入 Git；每轮开跑前由 `global-setup.js` 清理上一轮证据。
