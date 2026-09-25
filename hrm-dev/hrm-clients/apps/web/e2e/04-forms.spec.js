import { test, expect } from '@playwright/test'
import { ACCOUNT, attachCollector, expectClean, recordMetric, shimViteVirtualModules, timeIt } from '../../../e2e-utils/harness.js'
import { webLoginAs, webNavigate } from './support.js'

test.beforeEach(async ({ page }) => {
  await shimViteVirtualModules(page)
})

/**
 * A4 表单提交 · 网页端归属子集（对照表 §三：A4-4 为 PC 发布通知抽屉 → apps/web）
 * A4-1/A4-2/A4-3（移动端员工端）不在本端。
 * 被测：PC 端「发布通知」抽屉 src/views/notification/components/PublishDrawer.vue
 */

test.describe('A4 表单提交（网页端）', () => {
  test('A4-4 网页端发布通知抽屉：必填提示 / 超长截断 / 正常提交成功', async ({ page }) => {
    const collector = attachCollector(page)
    await webLoginAs(page, ACCOUNT.boss)
    await webNavigate(page, '/notification')
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
    expect(titleLen, `网页端通知标题超长未被截断，实际长度 ${titleLen}`).toBe(100)
    recordMetric({ case: 'A4-4', item: '网页端通知标题 maxlength 截断实测长度', value: titleLen })

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
    expectClean(collector, 'A4-4')
  })
})
