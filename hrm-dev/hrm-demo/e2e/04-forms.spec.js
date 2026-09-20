import { test, expect } from '@playwright/test'
import {
  ACCOUNT,
  attachCollector,
  expectClean,
  mobileLoginAs,
  pcLoginAs,
  pcNavigate,
  recordMetric,
  shot,
  shimViteVirtualModules,
  timeIt
} from './utils/harness.js'

test.beforeEach(async ({ page }) => {
  await shimViteVirtualModules(page)
})

/**
 * A4 表单提交：必填校验 / 正常提交成功反馈 / 边界与异常输入（超长被 maxlength 截断、非法输入被拦）。
 * 覆盖三处：
 *   1) 移动端员工端「新建工单」src/mobile/views/staff/workorderCreate.vue
 *   2) 移动端员工端「补卡申请」src/mobile/views/staff/attendance.vue（+ makeupList.vue 回读）
 *   3) PC 端「发布通知」抽屉 src/pc/views/notification/components/PublishDrawer.vue
 */

const LONG_TEXT = '测试'.repeat(120)

test.describe('A4 表单提交', () => {
  test('A4-1 移动端新建工单：必填校验 / 超长截断 / 正常提交成功', async ({ page }) => {
    const collector = attachCollector(page)
    await mobileLoginAs(page, ACCOUNT.staff)
    await page.goto('/mobile.html#/staff/workorder/create', { waitUntil: 'domcontentloaded' })
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
    await mobileLoginAs(page, ACCOUNT.staff)
    await page.goto('/mobile.html#/staff/attendance', { waitUntil: 'domcontentloaded' })
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
    await page.goto('/mobile.html#/staff/attendance/makeup', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.makeup-list')).toBeVisible({ timeout: 15_000 })
    await expect(page.locator('.mk-item').first()).toContainText('2026-09-20')
    await expect(page.locator('.mk-item').first()).toContainText('审批中')
    await shot(page, 'A4-2-补卡列表回读')
    expectClean(collector, 'A4-2')
  })

  test('A4-3 移动端补卡列表：状态筛选切换与空态文案', async ({ page }) => {
    await mobileLoginAs(page, ACCOUNT.staff)
    await page.goto('/mobile.html#/staff/attendance/makeup', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.makeup-list')).toBeVisible()
    const chips = page.locator('.fchip')
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

  test('A4-4 PC 发布通知抽屉：必填提示 / 超长截断 / 正常提交成功', async ({ page }) => {
    const collector = attachCollector(page)
    await pcLoginAs(page, ACCOUNT.boss)
    await pcNavigate(page, '/notification')
    await expect(page.locator('.app-main')).toContainText('通知')

    const openCost = await timeIt(async () => {
      await page.getByRole('button', { name: '发布通知' }).click()
      await expect(page.locator('.el-drawer')).toBeVisible({ timeout: 15_000 })
    })
    recordMetric({ case: 'A4-4', item: '发布通知抽屉打开', ms: openCost })

    const drawer = page.locator('.el-drawer')
    const publishBtn = drawer.getByRole('button', { name: '发布' })
    const recipient = drawer.locator('.recipient__count')

    // 必填提示：标题为空时按钮禁用，且 title 文案指明缺哪一项（B4.3）
    await expect(publishBtn).toBeDisabled()
    await expect(publishBtn).toHaveAttribute('title', '请先填写通知标题')
    await expect(recipient).toContainText('名在职员工')

    // 只填标题 → 提示切换到正文
    const titleInput = drawer.locator('input.el-input__inner')
    await titleInput.fill('演示通知')
    await expect(publishBtn).toHaveAttribute('title', '请先填写通知正文')

    // 边界：标题 maxlength=100，逐字输入 160 字后实际值被截到 100
    await titleInput.fill('')
    await titleInput.click()
    await titleInput.pressSequentially('通'.repeat(160), { delay: 0 })
    const titleLen = (await titleInput.inputValue()).length
    expect(titleLen, `PC 通知标题超长未被截断，实际长度 ${titleLen}`).toBe(100)
    recordMetric({ case: 'A4-4', item: 'PC 通知标题 maxlength 截断实测长度', value: titleLen })

    // 正常提交：全员范围人数 > 20 会触发二次确认（B4.4）
    await titleInput.fill('演示通知：今日盘点安排')
    await drawer.locator('textarea').fill('今日 18:00 后各驿站盘点包裹，请站长配合完成并回复结果。')
    await expect(publishBtn).toBeEnabled()
    const countText = await recipient.innerText()
    recordMetric({ case: 'A4-4', item: '发布范围全员收件人数文案', value: countText })

    await publishBtn.click()
    const confirmBox = page.locator('.el-message-box')
    if (await confirmBox.isVisible().catch(() => false)) {
      await confirmBox.getByRole('button', { name: '确认发布' }).click()
    }
    await expect(page.locator('.el-message--success').first()).toContainText('已发布给', { timeout: 15_000 })
    await shot(page, 'A4-4-发布通知成功')
    expectClean(collector, 'A4-4')
  })
})
