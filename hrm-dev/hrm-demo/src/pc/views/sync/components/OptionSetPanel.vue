<script setup>
import { computed } from 'vue'
import { VALUE_TYPE_LABEL, extraAttrsText } from '../utils/configCenter.js'
import StateBlock from '../../../components/StateBlock.vue'
import StatusTag from '../../../components/StatusTag.vue'

/**
 * 选项集面板（设计 B.2.3 / E.1 Molecule）
 * 右栏跟随左侧选中项：仅单选项展示候选表，其它值类型给一行说明（不隐藏右栏，避免布局跳动）。
 * source=MIGRATED 的历史纳管选项需人工确认（A.4.2），故给顶部提示条 + 行内 warning 浅底。
 */
const props = defineProps({
  item: { type: Object, default: null },
  optionSet: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  error: { type: Boolean, default: false },
  canWrite: { type: Boolean, default: true }
})

const emit = defineEmits(['add', 'edit', 'toggle', 'remove', 'retry'])

const MIGRATED_DICT = { MIGRATED: { label: '待确认', type: 'warning' } }

const isSingleSelect = computed(() => !!props.item && props.item.valueType === 'SINGLE_SELECT')

const options = computed(() => (props.optionSet ? [...props.optionSet.options].sort((a, b) => a.sort - b.sort) : []))

/** 历史自动纳管的选项数量：>0 时顶部提示「请确认显示名与启用状态」 */
const migratedCount = computed(() => options.value.filter((option) => option.source === 'MIGRATED').length)

function rowClassOf({ row }) {
  return row.source === 'MIGRATED' ? 'is-migrated' : ''
}
</script>

<template>
  <section class="option-panel">
    <div class="option-panel__head">
      <div class="option-panel__title-wrap">
        <h3 class="option-panel__title">
          选项集
          <template v-if="item">· {{ item.name }}（{{ VALUE_TYPE_LABEL[item.valueType] }}）</template>
        </h3>
        <p v-if="item" class="option-panel__sub">
          选中项：{{ item.itemKey }}
          <template v-if="optionSet"> · 选项集 {{ optionSet.setKey }}</template>
        </p>
      </div>
      <el-button v-if="canWrite && isSingleSelect" type="primary" plain @click="emit('add')">新增选项</el-button>
    </div>

    <StateBlock v-if="error" variant="error" title="选项集加载失败" @action="emit('retry')" />

    <StateBlock
      v-else-if="!item"
      variant="empty"
      title="请在左侧选择一个配置项"
      description="选中单选项后可在右侧维护候选项"
    />

    <StateBlock
      v-else-if="!isSingleSelect"
      variant="empty"
      :title="`该值为${VALUE_TYPE_LABEL[item.valueType] || ''}型，无候选项`"
      description="数值 / 文本 / 时间型配置项直接填写取值，无需维护选项集"
    />

    <template v-else>
      <el-alert
        v-if="migratedCount > 0"
        class="option-panel__alert"
        type="warning"
        :closable="false"
        show-icon
        :title="`有 ${migratedCount} 个历史候选项已自动纳管，请确认显示名与启用状态`"
      />

      <el-table v-loading="loading" class="option-panel__table" :data="options" border :row-class-name="rowClassOf">
        <el-table-column prop="sort" label="排序" width="64" align="center" />
        <el-table-column prop="optionKey" label="选项Key" width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="option-panel__key">{{ row.optionKey }}</span>
          </template>
        </el-table-column>
        <el-table-column label="显示名" min-width="150">
          <template #default="{ row }">
            <span class="option-panel__label">
              {{ row.label }}
              <StatusTag v-if="row.source === 'MIGRATED'" :dict="MIGRATED_DICT" value="MIGRATED" variant="soft" />
            </span>
          </template>
        </el-table-column>
        <el-table-column label="附加属性" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ extraAttrsText(optionSet.setKey, row.extraAttrs) }}</template>
        </el-table-column>
        <el-table-column label="启用" width="80" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled"
              :disabled="!canWrite"
              :title="row.enabled ? '停用后不可被新选择，存量值保留可读' : '重新启用该选项'"
              :aria-label="`${row.label}启用开关`"
              @change="(value) => emit('toggle', row, value)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-if="canWrite" link type="primary" @click="emit('edit', row)">编辑</el-button>
            <el-button
              v-if="canWrite"
              link
              type="danger"
              :disabled="row.builtin"
              :title="row.builtin ? '系统内置选项不可删除，可停用' : '删除该选项'"
              @click="emit('remove', row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </template>
  </section>
</template>

<style scoped lang="scss">
.option-panel {
  &__head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: var(--sp-3);
    margin-bottom: var(--sp-3);
  }

  &__title {
    margin: 0;
    font-size: var(--fs-h3);
    font-weight: var(--fw-semibold);
    color: var(--text-1);
  }

  &__sub {
    margin: var(--sp-1) 0 0;
    font-size: var(--fs-caption);
    color: var(--text-3);
  }

  &__label {
    display: inline-flex;
    align-items: center;
    gap: var(--sp-2);
  }

  &__key {
    font-family: var(--font-num);
    font-variant-numeric: tabular-nums;
    color: var(--text-2);
  }

  &__alert {
    margin-bottom: var(--sp-3);
  }

  // 历史纳管行：整行浅底标记（B.5 之外的第二处 warning 语义，仍取既有状态 Token）
  :deep(.el-table__row.is-migrated) {
    background-color: var(--state-warning-bg);
  }

  // 内置选项的删除为禁用态：改用禁用文字色，与可用的 danger 红拉开差异（与配置项表同口径）
  :deep(.el-button.is-link.is-disabled) {
    color: var(--text-disabled);
  }
}
</style>
