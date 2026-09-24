// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import MeSection from './MeSection.vue'

/**
 * 管理端「我的」页零变化护栏
 * 拆分共享组件后，MeSection 仍须只渲染管理端的「账号信息」5 行 + 「运行环境」；
 * 员工端的「我的数据」两群不得出现 —— 防止拆分把两端 IA 混回同一组件。
 */
vi.mock('@/mobile/utils/authStorage.js', () => ({
  readToken: () => 'demo-token',
  readUser: () => ({
    realName: '演示管理员',
    role: 'ADMIN',
    username: 'admin',
    phone: '13800000000',
    stationName: '总部',
    deptName: '管理部',
    lastLoginTime: '2026-09-23 10:00'
  }),
  writeAuth: () => {},
  clearAuth: () => {}
}))
vi.mock('@/mobile/api/auth.js', () => ({ getMe: vi.fn(), login: vi.fn(), logout: vi.fn(), updatePassword: vi.fn() }))

// van-cell 文案在 props 上，默认元素桩断言不到内容，这里换成能渲染 title/value 的桩
const VanCell = { props: ['title', 'label', 'value'], template: '<div class="cell">{{ title }}|{{ label }}|{{ value }}</div>' }

function mountMe() {
  // 空路由表会产生 "No match found" 噪声告警，给一条兜底路由即可（本组件不依赖具体页面）
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/:pathMatch(.*)*', component: { template: '<div />' } }]
  })
  return mount(MeSection, {
    global: {
      plugins: [createPinia(), router],
      stubs: {
        'van-cell': VanCell,
        'van-cell-group': { template: '<div><slot /></div>' },
        'van-button': true
      }
    }
  })
}

describe('MeSection · 管理端内容护栏', () => {
  it('仍渲染「账号信息」5 行', () => {
    const text = mountMe().text()
    for (const label of ['登录账号', '手机号', '所属驿站', '所属部门', '最后登录']) {
      expect(text).toContain(label)
    }
  })

  it('仍渲染管理端「管理与配置」与「运行环境」', () => {
    const text = mountMe().text()
    expect(text).toContain('管理与配置')
    expect(text).toContain('运行环境')
    expect(text).toContain('修改密码')
  })

  it('不含员工端「我的数据」两群（IA 未被员工端结构替换）', () => {
    const text = mountMe().text()
    expect(text).not.toContain('我的数据')
    expect(text).not.toContain('薪酬与考核')
    expect(text).not.toContain('考勤与流程')
  })
})