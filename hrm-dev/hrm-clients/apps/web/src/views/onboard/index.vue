<template>
  <div class="onboard-page">
    <PageHeader title="入离职" :sub="headerSub" :loading="listLoading">
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="openStart('ONBOARDING')">发起入职流程</el-button>
        <el-button :icon="Minus" @click="openStart('OFFBOARDING')">发起离职流程</el-button>
        <el-button :icon="Refresh" @click="fetchList">刷新</el-button>
      </template>
    </PageHeader>

    <el-tabs v-model="activeTab" class="onboard-tabs">
      <el-tab-pane name="instance" label="流程实例" />
      <el-tab-pane name="template" label="流程模板" />
    </el-tabs>

    <template v-if="activeTab === 'instance'">
      <el-card shadow="never" class="filter-card">
        <el-form inline>
          <el-form-item label="流程类型">
            <!-- 入职与离职是两个独立接口且各自分页，故类型为单选而不做「全部」的客户端合并 -->
            <el-radio-group v-model="query.flowType" @change="resetPage">
              <el-radio-button value="ONBOARDING">入职</el-radio-button>
              <el-radio-button value="OFFBOARDING">离职</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" clearable placeholder="全部状态" style="width: 140px" @change="resetPage">
              <el-option v-for="(dict, key) in FLOW_STATUS" :key="key" :value="key" :label="dict.label" />
            </el-select>
          </el-form-item>
          <el-form-item label="驿站">
            <el-select
              v-model="query.stationId"
              clearable
              placeholder="全部驿站"
              style="width: 150px"
              @change="resetPage"
            >
              <el-option v-for="item in stations" :key="item.id" :label="item.stationName" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="关键字">
            <el-input
              v-model="query.keyword"
              placeholder="姓名 / 流程号"
              clearable
              style="width: 180px"
              @keyup.enter="resetPage"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="resetPage">查询</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <el-card shadow="never" class="content-card">
        <StateBlock v-if="listError" variant="error" title="流程列表加载失败" @action="fetchList" />
        <StateBlock
          v-else-if="!listLoading && !list.length"
          variant="empty"
          :title="query.flowType === 'ONBOARDING' ? '暂无入职流程' : '暂无离职流程'"
          description="发起新流程后可在这里跟进步骤进度"
          :action-text="query.flowType === 'ONBOARDING' ? '发起入职流程' : '发起离职流程'"
          @action="openStart(query.flowType)"
        />
        <template v-else>
          <el-table v-loading="listLoading" :data="list" row-key="id">
            <el-table-column prop="flowNo" label="流程号" min-width="176" show-overflow-tooltip>
              <template #default="{ row }">
                <el-button link type="primary" @click="openDetail(row)">{{ row.flowNo }}</el-button>
              </template>
            </el-table-column>
            <el-table-column label="类型" width="88">
              <template #default="{ row }">
                <StatusTag :dict="FLOW_TYPE" :value="row.flowType" :variant="FLOW_TYPE[row.flowType].variant" />
              </template>
            </el-table-column>
            <el-table-column
              prop="employeeName"
              :label="query.flowType === 'ONBOARDING' ? '候选人' : '员工'"
              min-width="110"
              show-overflow-tooltip
            />
            <el-table-column prop="stationName" label="驿站" min-width="110" show-overflow-tooltip />
            <el-table-column label="当前步骤" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">
                <span>{{ row.currentStepName || '全部完成' }}</span>
                <span v-if="row.status === 'REJECTED'" class="onboard-page__rejected">（已驳回）</span>
              </template>
            </el-table-column>
            <el-table-column label="进度" width="92">
              <template #default="{ row }">{{ row.progress.done }} / {{ row.progress.total }}</template>
            </el-table-column>
            <el-table-column label="状态" width="104">
              <template #default="{ row }">
                <StatusTag :dict="FLOW_STATUS" :value="row.status" :variant="FLOW_STATUS[row.status].variant" />
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="发起时间" width="164" />
            <el-table-column label="操作" width="88" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openDetail(row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrap">
            <el-pagination
              v-model:current-page="query.pageNum"
              v-model:page-size="query.pageSize"
              :total="total"
              :page-sizes="[20, 50, 100]"
              layout="total, sizes, prev, pager, next"
              @size-change="resetPage"
              @current-change="fetchList"
            />
          </div>
        </template>
      </el-card>
    </template>

    <el-card v-else shadow="never" class="content-card">
      <StateBlock
        variant="empty"
        title="流程模板尚未开放"
        description="契约未提供流程模板的读写接口，当前入职 6 步、离职 6 步由服务端固定；模板可配置待接口补齐后再开放。"
      />
    </el-card>

    <StartFlowDialog
      v-model="startVisible"
      :type="startType"
      :stations="stations"
      :departments="departmentOptions"
      @created="fetchList"
    />
    <FlowDetailDrawer
      v-model="detailVisible"
      :flow-type="detailType"
      :flow-id="detailId"
      :can-write="true"
      @changed="fetchList"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { Minus, Plus, Refresh } from '@element-plus/icons-vue'
import { useOrgStore } from '../../stores/org.js'
import { getOffboardings, getOnboardings } from '../../api/hr.js'
import { FLOW_STATUS, FLOW_TYPE } from '@kdyzgl/shared/constants/dict.js'
import PageHeader from '../../components/PageHeader.vue'
import StateBlock from '../../components/StateBlock.vue'
import StatusTag from '../../components/StatusTag.vue'
import StartFlowDialog from './components/StartFlowDialog.vue'
import FlowDetailDrawer from './components/FlowDetailDrawer.vue'

/**
 * 入离职流程（需求10）
 *
 * 为什么独立成页而不是并入员工管理：导入是批量建档，流程是逐人状态推进，
 * 两者的数据模型与错误语义都不同（导入失败 vs 流程驳回），硬塞在一起会让提示串味（A11-3）。
 *
 * 入口与权限：侧边栏「组织人事 → 入离职」，契约的流程读写全部是 ADMIN；
 * TODO(扩展): 契约无流程模板接口（/flows/templates 未实现）与撤销接口，对应 Tab 与操作保留占位说明。
 */
// 驿站与部门都是跨页基础数据，取数收口到 org store
const orgStore = useOrgStore()
const { stations, departmentOptions } = storeToRefs(orgStore)

const activeTab = ref('instance')

const list = ref([])
const total = ref(0)
const listLoading = ref(false)
const listError = ref(false)
const query = reactive({
  flowType: 'ONBOARDING',
  status: undefined,
  stationId: undefined,
  keyword: '',
  pageNum: 1,
  pageSize: 20
})

async function fetchList() {
  listLoading.value = true
  listError.value = false
  try {
    const params = {
      status: query.status,
      stationId: query.stationId,
      keyword: query.keyword || undefined,
      pageNum: query.pageNum,
      pageSize: query.pageSize
    }
    const page = query.flowType === 'ONBOARDING' ? await getOnboardings(params) : await getOffboardings(params)
    list.value = page.list || []
    total.value = page.total || 0
  } catch (e) {
    listError.value = true
  } finally {
    listLoading.value = false
  }
}

function resetPage() {
  query.pageNum = 1
  fetchList()
}

const startVisible = ref(false)
const startType = ref('ONBOARDING')

function openStart(type) {
  startType.value = type
  startVisible.value = true
}

const detailVisible = ref(false)
const detailId = ref(null)
const detailType = ref('ONBOARDING')

function openDetail(row) {
  detailId.value = row.id
  detailType.value = row.flowType
  detailVisible.value = true
}

const headerSub = computed(() => `当前筛选共 ${total.value} 个流程 · 每步显示责任方与状态，驳回不回滚已产生的数据`)

async function loadBaseData() {
  // 部门树失败降级为空下拉；驿站失败仍向上抛（发起流程时的归属驿站依赖它）
  await Promise.all([orgStore.loadStations(), orgStore.loadDepartments().catch(() => [])])
}

onMounted(async () => {
  await loadBaseData()
  fetchList()
})
</script>

<style scoped lang="scss">
.onboard-page {
  .onboard-tabs {
    :deep(.el-tabs__header) {
      margin-bottom: var(--sp-4);
    }

    :deep(.el-tabs__item) {
      height: 40px;
      font-size: var(--fs-body);
      color: var(--text-2);
    }

    :deep(.el-tabs__item.is-active) {
      color: var(--color-primary-strong);
      font-weight: var(--fw-medium);
    }

    :deep(.el-tabs__active-bar) {
      height: 2px;
      background-color: var(--color-primary-strong);
    }
  }

  .content-card {
    margin-bottom: 0;
  }

  &__rejected {
    margin-left: var(--sp-1);
    color: var(--state-danger-fg);
    font-size: var(--fs-caption);
  }
}
</style>
