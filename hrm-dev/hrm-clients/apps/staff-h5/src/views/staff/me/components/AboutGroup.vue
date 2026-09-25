<script setup>
import { APP_NAME_STAFF, APP_VERSION } from '@/constants/appName.js'

/**
 * 「关于」区（员工端专属，原位替换原「运行环境」）
 * 只读诊断信息，降级为 Caption，不参与信息层级；应用名与版本取命名真源常量，不各写一份字面量。
 */
const versionText = `${APP_VERSION}（演示版）`
</script>

<template>
  <div class="section-title">关于</div>
  <van-cell-group inset class="about">
    <van-cell title="应用名称" :value="APP_NAME_STAFF" />
    <van-cell title="版本" :value="versionText" />
    <van-cell title="数据来源" value="全量 Mock，不发起真实请求" />
  </van-cell-group>
</template>

<style scoped>
.about {
  --van-cell-font-size: var(--fs-caption);
  --van-cell-text-color: var(--text-3);
  --van-cell-value-color: var(--text-3);
}
</style>