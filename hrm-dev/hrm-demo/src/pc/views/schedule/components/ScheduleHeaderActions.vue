<script setup>
import { ArrowDown, MagicStick, Refresh } from '@element-plus/icons-vue'

/**
 * 排班页头操作区（驿站切换 / 批量工具 / 撤销 / 保存 / 刷新）
 *
 * 从页面壳里拆出来：这五个控件与 dirty 状态强相关，但全部是纯展示与回抛，
 * 页面壳因此只剩下「把状态接到控件上」这一件事。
 */
defineProps({
  isAdmin: { type: Boolean, default: false },
  stations: { type: Array, default: () => [] },
  stationId: { type: [Number, String], default: null },
  canWrite: { type: Boolean, default: false },
  dirtyCount: { type: Number, default: 0 },
  saving: { type: Boolean, default: false }
})

const emit = defineEmits(['stationChange', 'batchCommand', 'discard', 'save', 'refresh'])
</script>

<template>
  <!-- 受控写法：切换前要先确认「未保存改动是否丢弃」，确认不通过时不改 model，界面不会先行跳站 -->
  <el-select
    v-if="isAdmin"
    :model-value="stationId"
    class="header-station"
    placeholder="选择驿站"
    @update:model-value="emit('stationChange', $event)"
  >
    <el-option v-for="item in stations" :key="item.id" :label="item.stationName" :value="item.id" />
  </el-select>
  <!-- 批量工具（需求2）：把逐人逐天的 112 次点击降到 1~4 次；非写权限角色整个下拉禁用并说明原因 -->
  <el-dropdown v-if="canWrite" trigger="click" :disabled="!canWrite" @command="emit('batchCommand', $event)">
    <el-button
      :icon="MagicStick"
      :disabled="!canWrite"
      :title="canWrite ? '批量铺排 / 复制 / 清空' : '仅超级管理员可执行批量排班'"
    >
      批量工具
      <el-icon class="el-icon--right"><ArrowDown /></el-icon>
    </el-button>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item command="spread">一键铺排…</el-dropdown-item>
        <el-dropdown-item command="copy">复制上一周</el-dropdown-item>
        <el-dropdown-item command="batch">整行 / 整列批量设置…</el-dropdown-item>
        <el-dropdown-item divided command="clear">清空本周…</el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
  <template v-if="canWrite">
    <el-button v-if="dirtyCount" @click="emit('discard')">撤销修改</el-button>
    <el-button type="primary" :loading="saving" :disabled="!dirtyCount" @click="emit('save')">
      {{ dirtyCount ? `保存排班（${dirtyCount}）` : '保存排班' }}
    </el-button>
  </template>
  <el-button :icon="Refresh" @click="emit('refresh')">刷新</el-button>
</template>

<style scoped lang="scss">
.header-station {
  width: 160px;
}
</style>
