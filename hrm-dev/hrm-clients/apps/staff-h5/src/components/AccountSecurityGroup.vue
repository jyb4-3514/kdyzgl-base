<script setup>
/**
 * 账号安全分组（跨端共享）
 * 标题 + 缩进 cell-group + 改密入口，行为只有一处实现，管理端 / 员工端共用同一份缩进口径。
 */
defineProps({
  /** 改密页路由；两端共用同一页，需要时由调用方覆盖 */
  to: { type: String, default: '/staff/me/password' }
})
</script>

<template>
  <div class="section-title">账号安全</div>
  <van-cell-group inset>
    <van-cell title="修改密码" is-link :to="to" />
  </van-cell-group>
</template>