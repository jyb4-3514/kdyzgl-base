<script setup>
import zhCn from 'element-plus/es/locale/lang/zh-cn'

/**
 * 全局中文语言包（T04）：Element Plus 按需引入后不再有 app.use(ElementPlus, { locale })，
 * 改用官方 ConfigProvider 承接（https://element-plus.org/en-US/guide/i18n.html#configprovider）。
 * ElConfigProvider 的 render 只做 renderSlot(slots, 'default')，不产生任何包裹元素，
 * 故 #app 内 DOM 与改造前完全一致（已核对 node_modules/element-plus/es/components/config-provider/src/config-provider.mjs）。
 */
</script>

<template>
  <el-config-provider :locale="zhCn">
    <router-view />
  </el-config-provider>
</template>
