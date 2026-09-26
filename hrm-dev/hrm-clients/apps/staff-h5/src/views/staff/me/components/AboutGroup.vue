<script setup>
import { APP_NAME_STAFF, APP_VERSION } from '@/constants/appName.js'

/**
 * 「关于」区（员工端专属，原位替换原「运行环境」）
 * 只读诊断信息，降级为 Caption，不参与信息层级；应用名与版本取命名真源常量，不各写一份字面量。
 * 演示态才标注 Mock 口径（版本后缀 + 数据来源），生产态显示真实后端口径；
 * 演示分支用 v-if(demoEnabled) 表达 —— 编译期常量使生产构建把演示文案整块剔除，不残留进产物。
 */
const demoEnabled = import.meta.env.VITE_MOCK_ENABLED === 'true'
</script>

<template>
  <div class="section-title">关于</div>
  <van-cell-group inset class="about">
    <van-cell title="应用名称" :value="APP_NAME_STAFF" />
    <van-cell v-if="demoEnabled" title="版本" :value="APP_VERSION + '（演示版）'" />
    <van-cell v-else title="版本" :value="APP_VERSION" />
    <van-cell v-if="demoEnabled" title="数据来源" value="全量 Mock，不发起真实请求" />
    <van-cell v-else title="数据来源" value="真实后端（hrm-api）" />
  </van-cell-group>
</template>

<style scoped>
.about {
  --van-cell-font-size: var(--fs-caption);
  --van-cell-text-color: var(--text-3);
  --van-cell-value-color: var(--text-3);
}
</style>