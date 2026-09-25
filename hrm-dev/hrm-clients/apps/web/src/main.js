import { createApp } from 'vue'
import { createPinia } from 'pinia'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

/**
 * Element Plus 样式按需引入（T04）。
 * 组件样式由 unplugin-vue-components 随组件注入（element-plus/es/components/<name>/style/css），
 * 但下面几项必须在这里显式引入：
 * 1) base/style/css 定义了 :root 上的全部 --el-* 变量。必须最先引入 —— 按需后组件样式会进异步 chunk，
 *    若 base 跟着组件后到，会把 tokens.scss 里的品牌色覆盖回 Element 默认蓝（顺序敏感，见 tokens.scss 头部说明）。
 * 2) message / message-box / notification 是函数式调用（ElMessage.xxx()），不走模板解析器，
 *    而各页面都是 `import { ElMessage } from 'element-plus'` 显式引入，解析器不会为它们补样式 ——
 *    不显式引入就会出现「弹框有内容、无样式」。loading 同理（v-loading 指令样式，双保险）。
 */
import 'element-plus/es/components/base/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/notification/style/css'
import 'element-plus/es/components/loading/style/css'

// @admin 只读引用一期资产：仅剩样式基座（登录页/请求层/登录态均已自建，见 src/stores/auth.js 与 src/utils/http.js）
import '@admin/styles/index.scss'
// 本端 Design Token 必须压在一期基座之后：tokens 里的 body 字体/底色与 Element 变量要靠后声明才生效
import './styles/tokens.scss'
import './styles/element-overrides.scss'

import adminRequest from '@admin/utils/request'
import App from './App.vue'
import router from './router/index.js'
import http, { UNAUTHORIZED_EVENT } from './utils/http.js'
import { readToken, readUser } from './utils/authStorage.js'
import { useAuthStore } from './stores/auth.js'
import { initClientLog } from '@kdyzgl/shared/clientLog.js'

/**
 * 网页端入口（B5 独立工程）
 *
 * 与一期 main.js 的差异：路由表换成本端独立表（登录 / 首页分流）；请求层统一走 @kdyzgl/api-client；
 * Element Plus 改按需引入（locale 移交 App.vue 的 el-config-provider）；图标仍全量全局注册，与一期一致。
 */

/**
 * 一期 request 实例的 baseURL 硬编码为 '/api/v1'（hrm-admin/src/utils/request.js:16，禁改）；
 * 这里在运行期用实例属性覆盖，实现「不改一期也能配 API 基址」。
 * 为什么本端仍需 import 它：路由内只读引用的一期页面（员工/部门/驿站/个人中心等）内部经它取数，
 * 故 Mock 必须挂到同一实例上（见下方 installMock），否则演示态下这些页面会漏到真实网络。
 */
adminRequest.defaults.baseURL = import.meta.env.VITE_API_BASE || '/api/v1'

const app = createApp(App)
app.use(createPinia())
app.use(router)

// 与一期 main.js 一致：整包注册图标组件（一期页面模板里直接用全局图标名）
Object.entries(ElementPlusIconsVue).forEach(([name, component]) => {
  app.component(name, component)
})

/**
 * 运行日志采集（M11 D6）：必须在挂载前初始化 —— 挂载即触发首屏请求与路由组件渲染，装晚了首屏异常就漏采。
 * 采集口径单点在 @kdyzgl/shared（三端同源），本端上报 source=PC。
 */
initClientLog({
  source: 'PC',
  instance: http,
  getToken: readToken,
  getUserId: () => {
    const user = readUser()
    return user ? user.id : null
  },
  app
})

/**
 * 登录失效兜底：401（被顶下线/禁用/手动退出）与 1108（会话 3 天到期）由 createHttp 幂等广播自定义事件，
 * 这里统一清登录态并回登录页（不在拦截器里 import router，避免 router → 页面 → api → http 循环引用）。
 * 1108 带 expired 告知，401 不带 redirect，避免污染下次登录的落地页。
 */
window.addEventListener(UNAUTHORIZED_EVENT, (event) => {
  const expired = !!(event && event.detail && event.detail.code === 1108)
  useAuthStore().clearAuth()
  if (router.currentRoute.value.path === '/login') return
  if (expired) router.replace({ path: '/login', query: { expired: '1' } })
  else router.replace('/login')
})

function mount() {
  app.mount('#app')
}

/**
 * 只有显式 true 才装 Mock；未配置 / 拼错一律视为关闭（fail-safe 向生产倾斜）。
 * 必须先于 mount 装配：挂载即触发路由与首屏请求，装晚了请求会漏到真实网络；
 * 动态 import 让 Rollup 在生产构建整块剔除 mock 数据层（用 .then 而非顶层 await 以兼容构建目标）。
 * 两个实例一并装载：本端 http（自有 api/*）+ 一期 request（@admin 只读页面的取数）。
 */
if (import.meta.env.VITE_MOCK_ENABLED === 'true') {
  import('@kdyzgl/mock/install.js').then(({ installMock }) => {
    installMock(http, adminRequest)
    mount()
  })
} else {
  mount()
}

/**
 * 真机/局域网调试用：只在 dev 构建下动态引入，生产产物零体积。
 * 放在 Mock 装配之后，不影响 installMock 对 axios adapter 的接管。
 */
if (import.meta.env.DEV) {
  import('vconsole').then(({ default: VConsole }) => new VConsole())
}
