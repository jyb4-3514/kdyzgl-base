import { describe, expect, it } from 'vitest'
import { APP_NAME } from './brand.js'

/**
 * 应用名回归网
 * 重点钉住：名字被改动时必须显式改到这里 —— 侧栏、浏览器标题、设置页都引用它，
 * 一旦有人顺手把字面量写回某个组件，本用例不会报错，但下面的字符形态断言能挡住"半截名""带空格"这类低级错误。
 */
describe('APP_NAME · 网页端应用名', () => {
  it('为「快递驿站智慧管理系统」', () => {
    expect(APP_NAME).toBe('快递驿站智慧管理系统')
  })

  it('10 个汉字、无空格无标点（侧栏 210px 下按 1em/字核算，多一个字就会挤到图标）', () => {
    expect(APP_NAME).toHaveLength(10)
    expect(APP_NAME).toMatch(/^[\u4e00-\u9fa5]+$/)
  })
})
