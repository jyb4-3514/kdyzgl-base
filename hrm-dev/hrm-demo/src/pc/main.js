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

// @admin 别名只读复用一期资产：样式基座 + HTTP 封装（Mock 必须挂到同一 axios 实例上才能拦住一期 api/*）
import '@admin/styles/index.scss'
// Demo 端 Design Token 必须压在一期基座之后：tokens 里的 body 字体/底色与 Element 变量要靠后声明才生效
import './styles/tokens.scss'
import './styles/element-overrides.scss'
import request from '@admin/utils/request'
import { useAuthStore, TOKEN_KEY, USER_KEY } from '@admin/stores/auth'

import App from './App.vue'
import router from './router/index.js'
import { initClientLog } from '../shared/clientLog.js'

/**
 * 网页端入口（T10）
 * 与一期 main.js 的差异只有三处：路由表换成 Demo 扩展版、请求走 Mock 适配器、
 * Element Plus 改按需引入（locale 移交 App.vue 的 el-config-provider）；
 * 图标仍全量全局注册，与一期一致，便于一期页面零改动复用。
 */

// 一期 request 实例的 baseURL 硬编码为 '/api/v1'（hrm-admin/src/utils/request.js:16，不可改）；
// 这里在 hrm-demo 侧用实例属性覆盖，实现「不改一期也能配 API 基址」
request.defaults.baseURL = import.meta.env.VITE_API_BASE || '/api/v1'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)

// 与一期 main.js 一致：整包注册图标组件（一期页面模板里直接用全局图标名）
Object.entries(ElementPlusIconsVue).forEach(([name, component]) => {
  app.component(name, component)
})

/**
 * 运行日志采集（M11 D6）：必须在挂载前初始化 —— 挂载即触发首屏请求与路由组件渲染，
 * 装晚了首屏异常就漏采。token / 用户从 localStorage 直读，与 request.js 的取值口径一致，
 * 不依赖 Pinia（此时 Pinia 尚未被任何组件消费）。
 */
initClientLog({
  source: 'PC',
  instance: request,
  getToken: () => localStorage.getItem(TOKEN_KEY) || '',
  getUserId: () => {
    try {
      const user = JSON.parse(localStorage.getItem(USER_KEY) || 'null')
      return user ? user.id : null
    } catch (e) {
      return null
    }
  },
  app
})

/**
 * 登录失效兜底（Demo 集成说明）：
 * 一期 utils/request.js 在 401 时会动态 import 它自己的 router（指向 hrm-admin 单页路由），
 * Demo 无法复用那一跳且不能改一期源码，因此这里订阅登录态——token 被清空即视为登录失效，
 * 由 Demo 自己的路由回登录页（不带 redirect，避免手动退出后污染下次登录的落地页）。
 */
useAuthStore().$subscribe((_mutation, state) => {
  if (!state.token && router.currentRoute.value.path !== '/login') {
    router.replace('/login')
  }
})

function mount() {
  app.mount('#app')
}

/**
 * 只有显式 true 才装 Mock；未配置 / 拼错一律视为关闭（fail-safe 向生产倾斜）。
 * 必须先于 mount 装配：挂载即触发路由与首屏请求，装晚了请求会漏到真实网络；
 * 动态 import 让 Rollup 在生产构建整块剔除 mock 数据层（用 .then 而非顶层 await 以兼容构建目标）。
 */
if (import.meta.env.VITE_MOCK_ENABLED === 'true') {
  import('../shared/mock/install.js').then(({ installMock }) => {
    installMock(request)
    mount()
  })
} else {
  mount()
}

/**
 * 真机/局域网调试用：只在 dev 构建下动态引入，生产产物零体积（安卓壳真机排查依赖它）。
 * 放在 Mock 装配之后，不触碰 installMock(request) 的时序。
 */
if (import.meta.env.DEV) {
  import('vconsole').then(({ default: VConsole }) => new VConsole())
}
