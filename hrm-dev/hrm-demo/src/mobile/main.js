import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router/index.js'
import { setupVant } from './vant.js'
import { initShell } from './utils/bridge.js'
import http from './utils/http.js'
import { readToken, readUser } from './utils/authStorage.js'
import { initClientLog } from '@/shared/clientLog.js'
// 顺序不可调换：vant.js 先注入组件样式，再上 tokens 覆盖其变量，最后 mobile.scss 消费变量
import './styles/tokens.scss'
import './styles/mobile.scss'

/**
 * 移动端入口（老板端 + 员工端）
 *
 * MPA 独立入口的意义：Vant 不进入 PC 包、Element Plus 不进入移动包，两套全局样式物理隔离。
 * 真实形态：安卓 WebView 加载同一 mobile.html，通过 HrmBridge/HrmShell 交换设备与返回键信息
 * （bridge.js 在浏览器下为空实现，见 T13 验收项）。
 */
// 桌面浏览器调试触摸事件（Vant 官方生态，仅开发环境加载，不进生产包）
if (import.meta.env.DEV) {
  import('@vant/touch-emulator')
}

const app = createApp(App)

app.use(createPinia())
app.use(router)
setupVant(app)
initShell(router)

/**
 * 运行日志采集（M11 D6）：必须在挂载前初始化 —— 挂载即触发首屏请求与路由组件渲染，装晚了首屏异常就漏采。
 * 安卓壳加载的是同一份 H5，故上报端固定 H5（壳侧若要区分，由壳注入后再调一次不同 source，见 clientLog 的初始化幂等）。
 */
initClientLog({
  source: 'H5',
  instance: http,
  getToken: readToken,
  getUserId: () => {
    const user = readUser()
    return user ? user.id : null
  },
  app
})

/**
 * 只有显式 true 才装 Mock；未配置 / 拼错一律视为关闭（fail-safe 向生产倾斜）。
 * 必须先于 mount 装配：挂载即触发路由与首屏请求，装晚了请求会漏到真实网络；
 * 动态 import 让 Rollup 在生产构建整块剔除 mock 数据层（用 .then 而非顶层 await 以兼容构建目标）。
 */
if (import.meta.env.VITE_MOCK_ENABLED === 'true') {
  import('@/shared/mock/install.js').then(({ installMock }) => {
    installMock(http)
    app.mount('#app')
  })
} else {
  app.mount('#app')
}

/**
 * 真机/局域网调试用：只在 dev 构建下动态引入，生产产物零体积（安卓壳真机排查依赖它）。
 * 放在 Mock 装配之后，不影响 installMock(http) 对 axios adapter 的接管。
 */
if (import.meta.env.DEV) {
  import('vconsole').then(({ default: VConsole }) => new VConsole())
}
