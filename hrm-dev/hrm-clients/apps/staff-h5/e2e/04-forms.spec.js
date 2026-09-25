import { test, expect } from '@playwright/test'
import { attachCollector, expectClean, recordMetric, shimViteVirtualModules, shot, timeIt } from '../../../e2e-utils/harness.js'
import { STAFF_ACCOUNT, staffLoginAs } from './support.js'

test.beforeEach(async ({ page }) => {
  await shimViteVirtualModules(page)
})

/**
 * A4 表单提交 · 员工端归属子集（对照表 §三：A4-1/A4-2/A4-3（/staff/*）→ staff；A4-4 为 PC 发布通知抽屉 → apps/web）
 * 覆盖：移动端员工端「新建工单」「补卡申请」（+ 列表回读、状态筛选空态）。
 */

const LONG_TEXT = '测试'.repeat(120)

test.describe('A4 表单提交（员工端）', () => {
  test('A4-1 移动端新建工单：必填校验 / 超长截断 / 正常提交成功', async ({ page }) => {
    const collector = attachCollector(page)
    await staffLoginAs(page, STAFF_ACCOUNT.staff)
    await page.goto('/staff/#/staff/workorder/create', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.create-page')).toBeVisible({ timeout: 15_000 })

    const submit = page.getByRole('button', { name: '提交工单' })
    const titleInput = page.getByPlaceholder('一句话说明问题')
    const contentInput = page.getByPlaceholder('补充现场情况、客户诉求等')

    // 必填校验：标题为空直接提交 → 字段级红字提示（不跳走、不发请求）
    await submit.click()
    await expect(page.locator('.van-field__error-message')).toContainText('请填写工单标题')
    await expect(page).toHaveURL(/#\/staff\/workorder\/create/)
    await shot(page, 'A4-1-工单必填校验')

    // 边界：标题 maxlength=100，逐字输入 240 字后实际值必须被截到 100
    await titleInput.click()
    await titleInput.pressSequentially(LONG_TEXT, { delay: 0 })
    const titleLen = (await titleInput.inputValue()).length
    expect(titleLen, `标题超长未被截断，实际长度 ${titleLen}`).toBe(100)
    recordMetric({ case: 'A4-1', item: '工单标题 maxlength 截断实测长度', value: titleLen })

    // 正常提交：真实填写后创建成功并带 highlight 返回列表
    await titleInput.fill('演示工单：包裹破损待核实')
    await contentInput.fill('客户反馈外包装破损，已拍照留证，请站长核实处理。')
    const cost = await timeIt(async () => {
      await submit.click()
      await expect(page.locator('.van-toast').first()).toContainText('工单已创建', { timeout: 15_000 })
    })
    await expect(page).toHaveURL(/#\/staff\/workorder\?highlight=\d+/, { timeout: 15_000 })
    recordMetric({ case: 'A4-1', item: '新建工单提交(点击→成功反馈)', ms: cost })
    await shot(page, 'A4-1-工单创建成功')
    expectClean(collector, 'A4-1')
  })

  test('A4-2 移动端补卡申请：必填校验 / 超长截断 / 正常提交成功 + 列表回读', async ({ page }) => {
    // 固定到 20:30：城东驿站单时段规则 08:00-18:00（时间窗 07:30-19:00）此时已过窗 → 稳定出现「申请补卡」入口
    await page.clock.setFixedTime(new Date('2026-09-20T20:30:00'))
    const collector = attachCollector(page)
    await staffLoginAs(page, STAFF_ACCOUNT.staff)
    await page.goto('/staff/#/staff/attendance', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.clock')).toBeVisible({ timeout: 15_000 })
    await expect(page.locator('.period-card').first()).toBeVisible({ timeout: 15_000 })

    const makeupTrigger = page.locator('button:has-text("申请补卡")').first()
    await expect(makeupTrigger, '20:30 应存在已过窗时段，未出现补卡入口').toBeVisible({ timeout: 15_000 })
    await makeupTrigger.click()

    const pop = page.locator('.makeup-pop')
    await expect(pop).toBeVisible()
    const reason = pop.locator('textarea')
    const popSubmit = pop.getByRole('button', { name: '提交申请' })

    // 必填/非法校验：理由不足 2 字时提交按钮禁用（页面显式规则 reason.length < 2）
    await expect(popSubmit).toBeDisabled()
    await reason.fill('x')
    await expect(popSubmit).toBeDisabled()
    await shot(page, 'A4-2-补卡理由校验')

    // 边界：maxlength=200，逐字输入 300 字后实际值被截到 200
    await reason.fill('')
    await reason.click()
    await reason.pressSequentially('测'.repeat(300), { delay: 0 })
    const reasonLen = (await reason.inputValue()).length
    expect(reasonLen, `补卡理由超长未被截断，实际长度 ${reasonLen}`).toBe(200)
    recordMetric({ case: 'A4-2', item: '补卡理由 maxlength 截断实测长度', value: reasonLen })

    // 正常提交
    await reason.fill('外出取件错过下班打卡，客户电话可证')
    await expect(popSubmit).toBeEnabled()
    const cost = await timeIt(async () => {
      await popSubmit.click()
      await expect(page.locator('.van-toast').first()).toContainText('补卡申请已提交', { timeout: 15_000 })
    })
    recordMetric({ case: 'A4-2', item: '补卡申请提交(点击→成功反馈)', ms: cost })
    await shot(page, 'A4-2-补卡提交成功')

    // 列表回读：新申请出现在「我的补卡申请」且状态为审批中
    await page.goto('/staff/#/staff/attendance/makeup', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.makeup-list')).toBeVisible({ timeout: 15_000 })
    await expect(page.locator('.mk-item').first()).toContainText('2026-09-20')
    await expect(page.locator('.mk-item').first()).toContainText('审批中')
    await shot(page, 'A4-2-补卡列表回读')
    expectClean(collector, 'A4-2')
  })

  test('A4-3 移动端补卡列表：状态筛选切换与空态文案', async ({ page }) => {
    await staffLoginAs(page, STAFF_ACCOUNT.staff)
    await page.goto('/staff/#/staff/attendance/makeup', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.makeup-list')).toBeVisible()
    const chips = page.locator('.makeup-list .filter-chips__chip')
    expect(await chips.count()).toBeGreaterThan(1)
    await chips.nth(0).click()
    await expect(chips.nth(0)).toHaveAttribute('aria-pressed', 'true')
    // 无论有无数据，筛选后必须给出明确状态（列表或空态），不得停在骨架
    await expect
      .poll(async () => (await page.locator('.mk-item').count()) + (await page.locator('.page-state__block').count()), {
        timeout: 25_000
      })
      .toBeGreaterThan(0)
  })
})
