import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import './styles/index.scss'

const app = createApp(App)

// Pinia 需先于路由安装：路由守卫中会使用 auth store
app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

// 全局注册 Element Plus 图标组件（整包引入，一期规模下可接受）
Object.entries(ElementPlusIconsVue).forEach(([name, component]) => {
  app.component(name, component)
})

app.mount('#app')
