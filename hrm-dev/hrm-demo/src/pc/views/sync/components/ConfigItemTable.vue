<script setup>
import { VALUE_TYPE_LABEL, SCOPE_LABEL, constraintsText, valueTextOf } from '../utils/configCenter.js'
import StateBlock from '../../../components/StateBlock.vue'
import StatusTag from '../../../components/StatusTag.vue'

/**
 * 配置项表（设计 B.2 / E.1 Molecule）：配置项定义的增删改入口
 * 只做展示与事件上报，API 调用由配置管理容器统一处理（同一份数据源的读写不散到两处）。
 * 行点击选中 → 右侧选项集面板随之切换；选中高亮用行类名而非仅颜色（B0.2 边界/无障碍）。
 */
const props = defineProps({
  items: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  error: { type: Boolean, default: false },
  selectedKey: { type: String, default: '' },
  canWrite: { type: Boolean, default: true },
  // 单选项显示默认值时要用选项集做 Key → label 映射
  optionSetOf: { type: Function, default: null }
})

const emit = defineEmits(['select', 'add', 'edit', 'toggle', 'remove', 'retry'])

const REQUIRED_DICT = { YES: { label: '是', type: 'warning' } }

const defaultValueText = (item) => {
  const set = props.optionSetOf ? props.optionSetOf(item.optionSetKey) : null
  return valueTextOf(item, item.defaultValue, set)
}

function rowClassOf({ row }) {
  const classes = []
  if (row.itemKey === props.selectedKey) classes.push('is-selected')
  if (!row.enabled) classes.push('is-disabled')
  return classes.join(' ')
}

/** 内置项删除按钮禁用原因写进 title，避免用户反复点击无响应（B0.2 禁用态） */
const removeTitle = (row) => (row.builtin ? '系统内置配置项不可删除，可停用' : '删除该配置项')
</script>

<template>
  <section class="item-table">
    <div class="item-table__head">
      <h3 class="item-table__title">配置项</h3>
      <el-button v-if="canWrite" type="primary" @click="emit('add')">新增配置项</el-button>
    </div>

    <StateBlock v-if="error" variant="error" title="配置项加载失败" @action="emit('retry')" />

    <StateBlock
      v-else-if="!loading && !items.length"
      variant="empty"
      title="暂无配置项"
      description="点击右上角「新增配置项」开始定义可配置项"
    />

    <el-table
      v-else
      v-loading="loading"
      class="sticky-table"
      :data="items"
      border
      row-key="itemKey"
      :row-class-name="rowClassOf"
      @row-click="(row) => emit('select', row)"
    >
      <el-table-column prop="sort" label="排序" width="70" align="center" />
      <el-table-column prop="itemKey" label="项 Key" width="170" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="item-table__key">{{ row.itemKey }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="显示名" width="140" show-overflow-tooltip />
      <el-table-column label="值类型" width="120">
        <template #default="{ row }">{{ VALUE_TYPE_LABEL[row.valueType] || row.valueType }}</template>
      </el-table-column>
      <el-table-column label="必填" width="70" align="center">
        <template #default="{ row }">
          <StatusTag v-if="row.required" :dict="REQUIRED_DICT" value="YES" variant="soft" />
          <span v-else class="item-table__muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="默认值" width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ defaultValueText(row) }}</template>
      </el-table-column>
      <el-table-column label="约束/单位" width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ constraintsText(row) }}</template>
      </el-table-column>
      <el-table-column label="生效范围" width="100">
        <template #default="{ row }">{{ SCOPE_LABEL[row.scope] || row.scope }}</template>
      </el-table-column>
      <el-table-column label="启用" width="80" align="center">
        <template #default="{ row }">
          <el-switch
            :model-value="row.enabled"
            :disabled="!canWrite"
            :title="row.enabled ? '停用后该项不在默认配置与驿站覆盖中出现，存量值保留' : '重新启用该项'"
            :aria-label="`${row.name}启用开关`"
            @change="(value) => emit('toggle', row, value)"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right" align="center">
        <template #default="{ row }">
          <el-button v-if="canWrite" link type="primary" @click.stop="emit('edit', row)">编辑</el-button>
          <el-button v-if="canWrite" link type="warning" @click.stop="emit('toggle', row, !row.enabled)">
            {{ row.enabled ? '停用' : '启用' }}
          </el-button>
          <el-button
            v-if="canWrite"
            link
            type="danger"
            :disabled="row.builtin"
            :title="removeTitle(row)"
            @click.stop="emit('remove', row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <p v-if="!canWrite" class="item-table__readonly">只读视角：配置项维护仅超级管理员可执行。</p>
  </section>
</template>

<style scoped lang="scss">
.item-table {
  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: var(--sp-3);
  }

  &__title {
    margin: 0;
    font-size: var(--fs-h3);
    font-weight: var(--fw-semibold);
    color: var(--text-1);
  }

  &__key {
    font-family: var(--font-num);
    font-variant-numeric: tabular-nums;
    color: var(--text-2);
  }

  &__muted {
    color: var(--text-disabled);
  }

  &__readonly {
    margin: var(--sp-3) 0 0;
    font-size: var(--fs-caption);
    color: var(--text-3);
  }

  // 选中行：左侧描边 + 浅底（双重标识，不只靠颜色）
  :deep(.el-table__row.is-selected) {
    background-color: var(--surface-hover);

    td.el-table__cell:first-child {
      /* 选中行左侧 2px 描边：装饰性竖线不承载白字，按硬规则走 500 档 */
      box-shadow: inset 2px 0 0 var(--color-primary-icon);
    }
  }

  // 停用项整行文字转弱（边界态）
  :deep(.el-table__row.is-disabled) {
    color: var(--text-3);
  }

  // 内置项的删除为禁用态：改用禁用文字色，与可用的 danger 红拉开差异（行内按钮间距小，仅靠不可点难辨识）
  :deep(.el-button.is-link.is-disabled) {
    color: var(--text-disabled);
  }
}
</style>
