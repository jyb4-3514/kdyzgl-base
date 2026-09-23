/**
 * 端选择入口页（index.html）
 *
 * 为什么纯 DOM 不引框架：这一页只做跳转、剧本自检与重置，引 Vue 只会拖慢首屏；
 * 渲染逻辑一次性执行，无响应式需求。三张卡片对应三个独立 HTML 入口（MPA）。
 */
const title = import.meta.env.VITE_DEMO_TITLE || '快递驿站智汇系统 · 三端演示 Demo'
const parcelCount = Number(import.meta.env.VITE_MOCK_PARCEL_COUNT || 0).toLocaleString('zh-CN')

document.getElementById('portal-title').textContent = title
document.getElementById('portal-env').innerHTML = `
  <div>· 数据来源：全量 Mock（axios 适配器拦截），<b>不发起任何真实 HTTP 请求</b>，无需后端与数据库</div>
  <div>· 一期规模：员工 56 / 驿站 8 / 部门 6；二期包裹索引 ${parcelCount} 条（确定性生成，刷新不变）</div>
  <div>· 演示账号：<code>admin</code>（管理员）、<code>admin_pwd0</code>（首登改密）、<code>st001_admin</code>（城东驿站站长）、<code>st001_staff</code>（城东驿站员工）</div>
  <div>· 统一演示密码：<code>demo1234</code>（仅存于 Mock 数据，非任何环境真实凭据）</div>
`

/** 三张卡片：移动端两张带 ?as=，登录页据此预填演示身份，省掉演示现场手输账号 */
const cards = [
  {
    tag: 'PC / Desktop',
    name: '网页端（管理后台）',
    role: 'ADMIN 全量功能 · STATION_ADMIN 本站包裹与工单 · STAFF 仅个人中心',
    link: 'pc.html',
    note: '→ 进入网页端'
  },
  {
    tag: 'Mobile / WebView',
    name: '老板端（经营视角）',
    role: 'ADMIN 身份的移动视图：经营总览、趋势、驿站排行、异常预警',
    link: 'mobile.html#/login?as=boss',
    note: '→ 进入移动端（已预填「老板」账号）'
  },
  {
    tag: 'Mobile / WebView',
    name: '员工端 · 驿站助手（作业视角）',
    role: 'STATION_ADMIN / STAFF：取件核销、工单处理、本站包裹与同步状态',
    link: 'mobile.html#/login?as=station',
    note: '→ 进入移动端（已预填「站长」账号）'
  }
]

document.getElementById('portal-cards').innerHTML = cards
  .map(
    (card) => `
    <a class="card" href="${card.link}">
      <div class="card__tag">${card.tag}</div>
      <div class="card__name">${card.name}</div>
      <div class="card__role">${card.role}</div>
      <div class="card__link">${card.note}</div>
    </a>`
  )
  .join('')

/**
 * 演示剧本依赖 Mock 数据层，仅 Mock 态加载；关闭后本页不预置数据
 * （生产构建只打包 pc / mobile 两个入口，本入口不进产物；用 .then 而非顶层 await 以兼容构建目标）
 */
if (import.meta.env.VITE_MOCK_ENABLED === 'true') {
  import('@/demo/scenario.js').then(({ applyScenario, checkScenario, resetDemoData }) => {
    const scenarioBox = document.getElementById('portal-scenario')
    const hint = document.getElementById('portal-reset-hint')
    const resetBtn = document.getElementById('portal-reset')
    const recheckBtn = document.getElementById('portal-recheck')

    /** 渲染剧本自检结果：任一项不达标即标红，避免演示现场才发现数据不全 */
    function renderScenario(result) {
      const { data, items } = result
      scenarioBox.innerHTML = `
        ${items
          .map(
            (item) => `
          <div class="check">
            <span>${item.ok ? '✅' : '❌'}</span>
            <span class="check__label">${item.label}</span>
            <span class="check__value ${item.ok ? 'check__ok' : 'check__bad'}">${item.value}</span>
          </div>`
          )
          .join('')}
        <div class="check">
          <span>🎯</span>
          <span class="check__label">演示运单号归属</span>
          <span class="check__value">${data.demoStationName || '未生成'}</span>
        </div>
      `
    }

    /** 首次进入即预置剧本（含指定演示运单号），保证「重置」与「直接进移动端」两种路径行为一致 */
    renderScenario(checkScenario())

    recheckBtn.addEventListener('click', () => {
      renderScenario({ data: applyScenario(), items: checkScenario().items })
      hint.textContent = '已重新预置剧本数据'
    })

    resetBtn.addEventListener('click', () => {
      resetBtn.disabled = true
      hint.textContent = '重置中…'
      try {
        renderScenario(resetDemoData())
        hint.textContent = `已重置：覆盖层与种子数据回到初始态，移动端需重新登录（${new Date().toLocaleTimeString('zh-CN')}）`
      } catch (error) {
        hint.textContent = `重置失败：${error.message}`
      } finally {
        resetBtn.disabled = false
      }
    })
  })
}
