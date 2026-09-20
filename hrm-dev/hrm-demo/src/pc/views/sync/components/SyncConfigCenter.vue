<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { saveResponseFile, formatDateCompact } from '@admin/utils/download'
import { getSyncConfigs } from '../../../api/syncConfig.js'
import {
  deleteConfigItem,
  deleteConfigOption,
  exportSyncConfig,
  getConfigItemImpact,
  getConfigOptionImpact,
  getGlobalConfig,
  updateConfigItem,
  updateConfigOption
} from '../../../api/syncConfigCenter.js'
import { useSyncConfigMeta } from '../composables/useSyncConfigMeta.js'
import { itemDeleteConfirm, optionDeleteConfirm } from '../utils/configCenter.js'
import ConfigItemTable from './ConfigItemTable.vue'
import OptionSetPanel from './OptionSetPanel.vue'
import ConfigItemDrawer from './ConfigItemDrawer.vue'
import OptionEditDialog from './OptionEditDialog.vue'
import GlobalDefaultForm from './GlobalDefaultForm.vue'
import StationOverrideTable from './StationOverrideTable.vue'
import StationOverrideDrawer from './StationOverrideDrawer.vue'
import ExportScopeDialog from './ExportScopeDialog.vue'
import ConfigImportDrawer from './ConfigImportDrawer.vue'
import DeleteConfirmDialog from './DeleteConfirmDialog.vue'
import StateBlock from '../../../components/StateBlock.vue'

/**
 * 配置管理（设计 B.1.2 编排层）
 * 分段控件 + 单内容区（避免 Tab 套 Tab），三段各自独立维护 loading / error / 空态。
 * 本组件是唯一的写入口：增删改、导入导出都在这里调接口并统一刷新元数据 / 全局默认 / 驿站配置，
 * 子组件只负责展示与事件上报（单一数据源，避免同一份数据被两处各自刷新出不一致）。
 *
 * TODO(扩展): 配置项数量增长后，左表与覆盖矩阵需接分页/虚拟滚动（当前为 5 项规模，未分页）
 * TODO(扩展): 若后续允许站长只读查看配置管理，需在此加 canWrite 降级（当前整块仅 ADMIN 渲染）
 */
const meta = useSyncConfigMeta()

const REGIONS = [
  { value: 'items', label: '配置项与选项集' },
  { value: 'global', label: '全局默认' },
  { value: 'station', label: '驿站覆盖' }
]

const region = ref('items')
const selectedKey = ref('')
const focusItemKey = ref('')
const rootRef = ref(null)

const globalValues = ref({})
const globalLoading = ref(false)
const globalError = ref(false)
const configs = ref([])
const configLoading = ref(false)
const configError = ref(false)

const itemDrawer = reactive({ visible: false, item: null })
const optionDialog = reactive({ visible: false, option: null })
const overrideDrawer = reactive({ visible: false, station: null })
const exportDialog = reactive({ visible: false, exporting: false, error: '' })
const importVisible = ref(false)
const deleteState = reactive({
  visible: false,
  title: '',
  message: '',
  stations: [],
  confirmText: '确认删除',
  altText: '',
  loading: false
})

/** 待删除对象：确认框与 API 调用之间传参用 */
let pendingDelete = null

const allItems = computed(() => meta.items.value)
const usableItems = computed(() => meta.enabledItems())
/** 覆盖矩阵/抽屉只处理可覆盖项（GLOBAL 生效范围不参与驿站覆盖，A.1） */
const overrideItems = computed(() => usableItems.value.filter((item) => item.scope === 'STATION'))
const selectedItem = computed(() => allItems.value.find((item) => item.itemKey === selectedKey.value) || null)
const selectedOptionSet = computed(() =>
  selectedItem.value ? meta.getOptionSet(selectedItem.value.optionSetKey) : null
)
const optionSetOf = (setKey) => meta.getOptionSet(setKey)

async function loadGlobal() {
  globalLoading.value = true
  globalError.value = false
  try {
    const data = await getGlobalConfig()
    globalValues.value = (data && data.values) || {}
  } catch (e) {
    globalValues.value = {}
    globalError.value = true
  } finally {
    globalLoading.value = false
  }
}

async function loadConfigs() {
  configLoading.value = true
  configError.value = false
  try {
    configs.value = await getSyncConfigs()
  } catch (e) {
    configs.value = []
    configError.value = true
  } finally {
    configLoading.value = false
  }
}

/** 写操作后的统一刷新：三项都刷新，因为删项/删选项会同时影响定义、全局默认与驿站覆盖 */
async function reloadAll(forceMeta = true) {
  await Promise.all([meta.loadMeta(forceMeta), loadGlobal(), loadConfigs()])
}

async function loadAll() {
  await Promise.all([meta.loadMeta(), loadGlobal(), loadConfigs()])
}

/** 刷新入口（页头「刷新」按钮调用） */
function refresh() {
  return loadAll()
}

/**
 * 分段切换后把「分段控件 + 新内容」滚到可视位置。
 * 实际滚动容器是 layout 的 .app-main（overflow-y:auto），用原生 scrollIntoView（零新增依赖）；
 * 否则在长内容区域切到另一区域时，新内容可能停在视口下方，被误认为「点了没反应」。
 */
async function handleRegionChange() {
  await nextTick()
  if (rootRef.value) rootRef.value.scrollIntoView({ block: 'start' })
}

function selectItem(row) {
  selectedKey.value = row.itemKey
}

function openItemDrawer(item) {
  itemDrawer.item = item
  itemDrawer.visible = true
}

function openOptionDialog(option) {
  if (!selectedItem.value) return
  optionDialog.option = option
  optionDialog.visible = true
}

async function handleItemToggle(row, value) {
  try {
    await updateConfigItem(row.itemKey, { enabled: value })
    ElMessage.success(value ? '配置项已启用' : '配置项已停用，存量值保留')
    await meta.loadMeta(true)
  } catch (e) {
    ElMessage.error((e && e.message) || '操作失败，请重试')
  }
}

async function handleOptionToggle(row, value) {
  if (!selectedItem.value) return
  try {
    await updateConfigOption(selectedItem.value.itemKey, row.optionKey, { enabled: value })
    ElMessage.success(value ? '选项已启用' : '选项已停用，存量值保留可读')
    await meta.loadMeta(true)
  } catch (e) {
    ElMessage.error((e && e.message) || '操作失败，请重试')
  }
}

/** 删除配置项：先查影响面 → builtin 直接阻断 → 有覆盖走确认框（B.6） */
async function handleItemRemove(row) {
  const impact = await getConfigItemImpact(row.itemKey).catch((e) => {
    ElMessage.error((e && e.message) || '影响面查询失败，请重试')
    return null
  })
  if (!impact) return
  if (!impact.canDelete) {
    ElMessageBox.alert(impact.blockers[0] ? impact.blockers[0].message : '该配置项不可删除', '无法删除', {
      type: 'warning',
      confirmButtonText: '知道了'
    })
    return
  }
  const text = itemDeleteConfirm(row, impact)
  pendingDelete = { kind: 'item', key: row.itemKey }
  Object.assign(deleteState, {
    visible: true,
    title: text.title,
    message: text.message,
    stations: impact.referencedStations,
    confirmText: text.confirmText,
    altText: '',
    loading: false
  })
}

/** 删除选项：内置阻断；全局默认引用引导改停用；被驿站引用走确认框（B.6 / C.4） */
async function handleOptionRemove(row) {
  if (!selectedItem.value) return
  const item = selectedItem.value
  const impact = await getConfigOptionImpact(item.itemKey, row.optionKey).catch((e) => {
    ElMessage.error((e && e.message) || '影响面查询失败，请重试')
    return null
  })
  if (!impact) return
  if (impact.builtin) {
    ElMessageBox.alert(impact.blockers[0] ? impact.blockers[0].message : '该选项不可删除', '无法删除', {
      type: 'warning',
      confirmButtonText: '知道了'
    })
    return
  }
  const sameSetItems = allItems.value.filter((owner) => owner.optionSetKey === item.optionSetKey)
  // 全局默认正在引用：后端已判定为不可删，主操作改为「改为停用」
  if (!impact.canDelete) {
    pendingDelete = { kind: 'option', itemKey: item.itemKey, optionKey: row.optionKey }
    Object.assign(deleteState, {
      visible: true,
      title: '删除选项',
      message: impact.blockers[0] ? impact.blockers[0].message : '该选项不可删除',
      stations: [],
      confirmText: '确认删除',
      altText: '改为停用',
      loading: false
    })
    return
  }
  const text = optionDeleteConfirm(row, impact, { sameSetItems })
  pendingDelete = { kind: 'option', itemKey: item.itemKey, optionKey: row.optionKey }
  Object.assign(deleteState, {
    visible: true,
    title: text.title,
    message: text.message,
    stations: impact.referencedStations,
    confirmText: text.confirmText,
    altText: '',
    loading: false
  })
}

async function confirmDelete() {
  if (!pendingDelete) return
  deleteState.loading = true
  try {
    if (pendingDelete.kind === 'item') {
      await deleteConfigItem(pendingDelete.key, { params: { confirm: true } })
    } else {
      await deleteConfigOption(pendingDelete.itemKey, pendingDelete.optionKey, { params: { confirm: true } })
    }
    ElMessage.success('已删除')
    deleteState.visible = false
    if (pendingDelete.kind === 'item' && selectedKey.value === pendingDelete.key) selectedKey.value = ''
    await reloadAll()
  } catch (e) {
    ElMessage.error((e && e.message) || '删除失败，请重试')
  } finally {
    deleteState.loading = false
  }
}

/** 全局默认引用下的替代动作：停用而非删除（B.6 主按钮「改为停用」） */
async function altDelete() {
  if (!pendingDelete || pendingDelete.kind !== 'option') return
  deleteState.loading = true
  try {
    await updateConfigOption(pendingDelete.itemKey, pendingDelete.optionKey, { enabled: false })
    ElMessage.success('选项已停用，存量值保留可读')
    deleteState.visible = false
    await reloadAll()
  } catch (e) {
    ElMessage.error((e && e.message) || '停用失败，请重试')
  } finally {
    deleteState.loading = false
  }
}

/** 配置项增删改后父组件回调：刷新元数据 + 保持选中项（编辑时 Key 不变） */
function handleItemSaved() {
  reloadAll()
}

function handleOptionSaved() {
  meta.loadMeta(true)
}

function handleOverrideSaved() {
  Promise.all([loadConfigs(), loadGlobal()])
}

const exportFilenameOf = (scope) => {
  if (scope === 'TEMPLATE') return '同步配置_导入模板.csv'
  const label = scope === 'ITEMS' ? '配置项' : scope === 'ALL' ? '全部' : '含全局默认'
  return `同步配置_${label}_${formatDateCompact()}.csv`
}

async function runExport(scope) {
  const response = await exportSyncConfig(scope)
  saveResponseFile(response, exportFilenameOf(scope))
}

async function handleExport(scope) {
  exportDialog.exporting = true
  exportDialog.error = ''
  try {
    await runExport(scope)
    ElMessage.success('导出完成')
    exportDialog.visible = false
  } catch (e) {
    // 弹窗内提示且不关闭，允许改范围后重试
    exportDialog.error = (e && e.message) || '导出失败，请稍后重试'
  } finally {
    exportDialog.exporting = false
  }
}

async function handleTemplate() {
  try {
    await runExport('TEMPLATE')
    ElMessage.success('导入模板已下载')
  } catch (e) {
    ElMessage.error((e && e.message) || '模板下载失败，请重试')
  }
}

function handleImported() {
  reloadAll()
}

function locateItem(itemKey) {
  region.value = 'station'
  focusItemKey.value = itemKey
}

onMounted(loadAll)

defineExpose({ refresh })
</script>

<template>
  <div ref="rootRef" class="config-center">
    <StateBlock v-if="meta.error.value" variant="error" title="配置元数据加载失败" @action="loadAll" />

    <template v-else>
      <div class="config-center__toolbar">
        <!-- 分段控件：三段共用一个内容区，不做子 Tab 套 Tab（B.1.2） -->
        <el-radio-group
          v-model="region"
          class="config-center__segmented"
          aria-label="配置管理分区"
          @change="handleRegionChange"
        >
          <el-radio-button v-for="item in REGIONS" :key="item.value" :value="item.value">{{
            item.label
          }}</el-radio-button>
        </el-radio-group>

        <div class="config-center__actions">
          <el-button @click="handleTemplate">下载导入模板</el-button>
          <el-button @click="importVisible = true">导入配置</el-button>
          <el-button type="primary" @click="exportDialog.visible = true">导出配置</el-button>
        </div>
      </div>

      <el-card shadow="never" class="config-center__card">
        <!-- 区域一：配置项与选项集（主从布局，md 以下上下堆叠，F.3） -->
        <el-row v-if="region === 'items'" :gutter="16">
          <el-col :xs="24" :sm="24" :md="24" :lg="15">
            <ConfigItemTable
              :items="allItems"
              :loading="meta.loading.value"
              :error="meta.error.value"
              :selected-key="selectedKey"
              :option-set-of="optionSetOf"
              @select="selectItem"
              @add="openItemDrawer(null)"
              @edit="openItemDrawer"
              @toggle="handleItemToggle"
              @remove="handleItemRemove"
              @retry="loadAll"
            />
          </el-col>
          <el-col :xs="24" :sm="24" :md="24" :lg="9" class="config-center__aside">
            <OptionSetPanel
              :item="selectedItem"
              :option-set="selectedOptionSet"
              :loading="meta.loading.value"
              :error="meta.error.value"
              @add="openOptionDialog(null)"
              @edit="openOptionDialog"
              @toggle="handleOptionToggle"
              @remove="handleOptionRemove"
              @retry="loadAll"
            />
          </el-col>
        </el-row>

        <!-- 区域二：全局默认 -->
        <GlobalDefaultForm
          v-else-if="region === 'global'"
          :items="usableItems"
          :global-values="globalValues"
          :configs="configs"
          :loading="globalLoading || meta.loading.value"
          :error="globalError"
          :option-set-of="optionSetOf"
          @retry="loadGlobal"
          @saved="handleOverrideSaved"
          @locate="locateItem"
        />

        <!-- 区域三：驿站覆盖 -->
        <StationOverrideTable
          v-else
          :configs="configs"
          :items="overrideItems"
          :global-values="globalValues"
          :loading="configLoading || meta.loading.value"
          :error="configError"
          :focus-item-key="focusItemKey"
          :option-set-of="optionSetOf"
          @retry="loadConfigs"
          @configure="
            (station) => {
              overrideDrawer.station = station
              overrideDrawer.visible = true
            }
          "
        />
      </el-card>
    </template>

    <ConfigItemDrawer
      v-model="itemDrawer.visible"
      :item="itemDrawer.item"
      :option-sets="meta.optionSets.value"
      @saved="handleItemSaved"
    />

    <OptionEditDialog
      v-model="optionDialog.visible"
      :config-item-key="selectedItem ? selectedItem.itemKey : ''"
      :set-key="selectedItem ? selectedItem.optionSetKey : ''"
      :set-name="selectedOptionSet ? selectedOptionSet.name : ''"
      :option="optionDialog.option"
      @saved="handleOptionSaved"
    />

    <StationOverrideDrawer
      v-model="overrideDrawer.visible"
      :station="overrideDrawer.station"
      :items="overrideItems"
      :global-values="globalValues"
      :option-set-of="optionSetOf"
      @saved="handleOverrideSaved"
    />

    <ExportScopeDialog
      v-model="exportDialog.visible"
      :exporting="exportDialog.exporting"
      :error="exportDialog.error"
      @confirm="handleExport"
    />

    <ConfigImportDrawer v-model="importVisible" @template="handleTemplate" @imported="handleImported" />

    <DeleteConfirmDialog
      v-model="deleteState.visible"
      :title="deleteState.title"
      :message="deleteState.message"
      :stations="deleteState.stations"
      :confirm-text="deleteState.confirmText"
      :alt-text="deleteState.altText"
      :loading="deleteState.loading"
      @confirm="confirmDelete"
      @alt="altDelete"
    />
  </div>
</template>

<style scoped lang="scss">
.config-center {
  &__toolbar {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: var(--sp-3);
    margin-bottom: var(--sp-4);
  }

  &__segmented {
    flex-wrap: wrap;
  }

  &__actions {
    display: flex;
    gap: var(--sp-2);
  }

  &__card {
    min-height: 320px;
  }

  &__aside {
    margin-top: var(--sp-4);
  }
}

// lg 断点下右栏与左栏并排，不需要顶部间距（F.3）
@media (min-width: 1200px) {
  .config-center__aside {
    margin-top: 0;
  }
}
</style>
