import { chromium } from '@playwright/test'
import { ACCOUNT, DEMO_PWD, pcLoginAs, pcNavigate, recordMetric, shimViteVirtualModules } from './harness.js'

/**
 * 关键操作耗时专项测量（独立脚本，非用例）：把「菜单切换 / 表格筛选 / 抽屉打开 / 表单提交」等
 * 单独计时，避免被登录与模块加载时间污染。结果同时打印并追加进 evidence/metrics.json。
 */
const browser = await chromium.launch()
const median = (arr) => arr.slice().sort((a, b) => a - b)[Math.floor(arr.length / 2)]

/* ========== PC ========== */
{
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, baseURL: 'http://localhost:5188' })
  const page = await context.newPage()
  await shimViteVirtualModules(page)
  await pcLoginAs(page, ACCOUNT.boss)

  // 菜单切换：首页 → 考勤管理 往返各 3 次
  const switches = []
  for (let i = 0; i < 3; i += 1) {
    let t0 = Date.now()
    await pcNavigate(page, '/attendance')
    await page.locator('.mini-stats__grid').waitFor({ state: 'visible' })
    switches.push(Date.now() - t0)
    t0 = Date.now()
    await pcNavigate(page, '/dashboard')
    await page.locator('.app-main').waitFor({ state: 'visible' })
    switches.push(Date.now() - t0)
  }
  recordMetric({ case: 'PERF', item: 'PC 菜单切换（往返 6 次中位数）', ms: median(switches) })
  console.log('PC 菜单切换 ms:', switches.join(','), '→ median', median(switches))

  // 表格筛选：工单页关键字查询 → 空态出现
  await pcNavigate(page, '/work-order')
  await page.getByPlaceholder('工单号 / 标题').waitFor({ state: 'visible' })
  const tFilter = Date.now()
  await page.getByPlaceholder('工单号 / 标题').fill('zzz-not-exist-9f8e')
  await page.getByRole('button', { name: '查询' }).click()
  await page.locator('.state-block--empty').waitFor({ state: 'visible' })
  const filterMs = Date.now() - tFilter
  recordMetric({ case: 'PERF', item: 'PC 表格筛选（关键字查询→结果就绪）', ms: filterMs })
  console.log('PC 表格筛选 ms:', filterMs)

  // 抽屉打开
  await pcNavigate(page, '/notification')
  await page.getByRole('button', { name: '发布通知' }).waitFor({ state: 'visible' })
  let t0 = Date.now()
  await page.getByRole('button', { name: '发布通知' }).click()
  await page.locator('.el-drawer').waitFor({ state: 'visible' })
  const drawerMs = Date.now() - t0
  recordMetric({ case: 'PERF', item: 'PC 发布通知抽屉打开', ms: drawerMs })
  console.log('PC 抽屉打开 ms:', drawerMs)

  // 表单提交：填标题正文 → 发布（含二次确认）
  const drawer = page.locator('.el-drawer')
  await drawer.locator('input.el-input__inner').fill('性能测量通知')
  await drawer.locator('textarea').fill('用于测量发布通知提交耗时。')
  t0 = Date.now()
  await drawer.getByRole('button', { name: '发布' }).click()
  const confirm = page.locator('.el-message-box')
  if (await confirm.isVisible().catch(() => false)) {
    await confirm.getByRole('button', { name: '确认发布' }).click()
  }
  await page.locator('.el-message--success').first().waitFor({ state: 'visible' })
  const submitMs = Date.now() - t0
  recordMetric({ case: 'PERF', item: 'PC 发布通知提交（点击→成功提示）', ms: submitMs })
  console.log('PC 表单提交 ms:', submitMs)

  await context.close()
}

/* ========== 移动端 ========== */
{
  const context = await browser.newContext({ viewport: { width: 375, height: 812 }, baseURL: 'http://localhost:5188' })
  const page = await context.newPage()
  await shimViteVirtualModules(page)
  await page.goto('http://localhost:5188/mobile.html#/login', { waitUntil: 'domcontentloaded' })
  await page.locator('.login__card input').nth(0).fill(ACCOUNT.staff)
  await page.locator('.login__card input').nth(1).fill(DEMO_PWD)
  await page.locator('.login__submit button').click()
  await page.locator('.van-tabbar').waitFor({ state: 'visible' })

  const tabTimes = []
  for (const [text, url] of [
    ['消息', /#\/staff\/message/],
    ['我的', /#\/staff\/me/],
    ['首页', /#\/staff\/home/]
  ]) {
    const t0 = Date.now()
    await page.locator('.van-tabbar-item', { hasText: text }).first().click()
    await page.waitForURL(url)
    tabTimes.push(Date.now() - t0)
  }
  recordMetric({ case: 'PERF', item: '移动端 Tabbar 切换（3 次中位数）', ms: median(tabTimes) })
  console.log('移动端 Tabbar 切换 ms:', tabTimes.join(','), '→ median', median(tabTimes))

  // 移动端表单提交：新建工单
  await page.goto('http://localhost:5188/mobile.html#/staff/workorder/create', { waitUntil: 'domcontentloaded' })
  await page.locator('.create-page').waitFor({ state: 'visible' })
  await page.getByPlaceholder('一句话说明问题').fill('性能测量工单')
  await page.getByPlaceholder('补充现场情况、客户诉求等').fill('用于测量工单提交耗时。')
  const t0 = Date.now()
  await page.getByRole('button', { name: '提交工单' }).click()
  // 必须等「本次提交的成功文案」出现，否则会误命中上一条残留 Toast（会得到不符合 Mock 延迟的假值）
  await page.locator('.van-toast', { hasText: '工单已创建' }).first().waitFor({ state: 'visible' })
  const orderMs = Date.now() - t0
  recordMetric({ case: 'PERF', item: '移动端新建工单提交（点击→成功提示）', ms: orderMs })
  console.log('移动端工单提交 ms:', orderMs)

  await context.close()
}

await browser.close()
