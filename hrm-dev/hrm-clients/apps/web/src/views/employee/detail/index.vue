<template>
  <div class="emp-detail">
    <PageHeader :title="profileName" :sub="headerSub" :loading="loading">
      <template #actions>
        <el-button :icon="Refresh" @click="loadAll">刷新</el-button>
      </template>
    </PageHeader>

    <StateBlock v-if="baseError" variant="error" title="员工信息加载失败" @action="loadAll" />

    <el-row v-else :gutter="16">
      <el-col :xs="24" :lg="10">
        <el-card shadow="never" class="emp-detail__card">
          <template #header><span class="emp-detail__card-title">基本信息</span></template>
          <el-descriptions v-loading="loading" :column="columnCount" size="small" border>
            <el-descriptions-item label="姓名">{{ employee.realName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="登录账号">{{ employee.username || '—' }}</el-descriptions-item>
            <el-descriptions-item label="手机号">{{ employee.phone || '—' }}</el-descriptions-item>
            <el-descriptions-item label="归属驿站">{{ employee.stationName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="部门">{{ employee.deptName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="终审角色">{{ roleLabel }}</el-descriptions-item>
            <el-descriptions-item label="入职日期">{{ employee.entryDate || '—' }}</el-descriptions-item>
            <el-descriptions-item label="账号状态">{{
              employee.status === 1 ? '启用' : '已停用'
            }}</el-descriptions-item>
          </el-descriptions>
        </el-card>

        <el-card shadow="never" class="emp-detail__card">
          <template #header>
            <div class="emp-detail__card-head">
              <span class="emp-detail__card-title">人事信息</span>
              <StatusTag
                v-if="contractWarn"
                :dict="CONTRACT_WARN"
                :value="contractWarn"
                :variant="CONTRACT_WARN[contractWarn].variant"
              />
            </div>
          </template>
          <StateBlock v-if="hrError" variant="error" title="人事档案加载失败" @action="loadHr" />
          <StateBlock
            v-else-if="!loading && !hrProfile"
            variant="empty"
            title="该员工尚未建立人事档案"
            description="可前往人事管理模块建档"
          />
          <el-descriptions v-else v-loading="loading" :column="columnCount" size="small" border>
            <el-descriptions-item label="学历">{{ hrProfile.educationLabel || '—' }}</el-descriptions-item>
            <el-descriptions-item label="合同类型">{{ hrProfile.contractTypeLabel || '—' }}</el-descriptions-item>
            <el-descriptions-item label="合同起止">
              {{ hrProfile.contractStart || '—' }} ~ {{ hrProfile.contractEnd || '无固定期限' }}
            </el-descriptions-item>
            <el-descriptions-item label="试用期"
              >{{ hrProfile.probationMonths }} 个月（转正 {{ hrProfile.regularDate || '—' }}）</el-descriptions-item
            >
            <el-descriptions-item label="社保基数">{{
              hrProfile.socialSecurityBase == null ? '—' : `${hrProfile.socialSecurityBase} 元`
            }}</el-descriptions-item>
            <el-descriptions-item label="紧急联系人">
              {{ hrProfile.emergencyContactName || '—'
              }}{{ hrProfile.emergencyContactRelation ? `（${hrProfile.emergencyContactRelation}）` : '' }}
              {{ hrProfile.emergencyContactPhone || '' }}
            </el-descriptions-item>
            <el-descriptions-item label="开户行">{{ hrProfile.bankName || '—' }}</el-descriptions-item>
            <!-- 银行卡出参已由服务端脱敏，前端不回显完整卡号（需求8 硬要求） -->
            <el-descriptions-item label="银行卡号">{{ hrProfile.bankAccount || '—' }}</el-descriptions-item>
          </el-descriptions>
          <p class="emp-detail__hint">银行卡与紧急联系人手机号为服务端脱敏值，本页不展示完整信息。</p>
        </el-card>

        <el-card shadow="never" class="emp-detail__card">
          <template #header><span class="emp-detail__card-title">入离职流程</span></template>
          <StateBlock v-if="!flowList.length" variant="empty" title="暂无该员工的入离职流程" />
          <el-table v-else :data="flowList" size="small" row-key="flowNo">
            <el-table-column label="类型" width="80">
              <template #default="{ row }">
                <StatusTag :dict="FLOW_TYPE" :value="row.flowType" :variant="FLOW_TYPE[row.flowType].variant" />
              </template>
            </el-table-column>
            <el-table-column prop="flowNo" label="流程号" min-width="150" show-overflow-tooltip />
            <el-table-column prop="currentStepName" label="当前步骤" min-width="110" show-overflow-tooltip />
            <el-table-column label="状态" width="96">
              <template #default="{ row }">
                <StatusTag :dict="FLOW_STATUS" :value="row.status" :variant="FLOW_STATUS[row.status].variant" />
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="14">
        <el-card shadow="never" class="emp-detail__card">
          <template #header><span class="emp-detail__card-title">工资单</span></template>
          <StateBlock
            v-if="!payrollList.length"
            variant="empty"
            title="暂无该员工的工资单"
            description="工资单由财务管理模块按月生成"
          />
          <el-table v-else :data="payrollList" size="small" row-key="id">
            <el-table-column prop="month" label="月份" width="90" />
            <el-table-column prop="payrollNo" label="单号" min-width="150" show-overflow-tooltip />
            <el-table-column label="应发" width="100" align="right">
              <template #default="{ row }">{{ row.grossAmount }}</template>
            </el-table-column>
            <el-table-column label="实发" width="100" align="right">
              <template #default="{ row }">
                <span class="emp-detail__net">{{ row.netAmount }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="104">
              <template #default="{ row }">
                <StatusTag :dict="PAYROLL_STATUS" :value="row.status" :variant="PAYROLL_STATUS[row.status].variant" />
              </template>
            </el-table-column>
          </el-table>
          <p class="emp-detail__hint">工资单明细与审核流转在「财务管理 → 工资单」中处理，本页只做聚合展示。</p>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { getEmployee } from '@/api/employee.js'
import { ROLE_LABEL } from '@kdyzgl/shared/constants/role'
import { getOffboardings, getOnboardings, getHrProfile } from '../../../api/hr.js'
import { getPayrolls } from '../../../api/finance.js'
import { CONTRACT_WARN, FLOW_STATUS, FLOW_TYPE, PAYROLL_STATUS } from '@kdyzgl/shared/constants/dict.js'
import PageHeader from '../../../components/PageHeader.vue'
import StateBlock from '../../../components/StateBlock.vue'
import StatusTag from '../../../components/StatusTag.vue'

/**
 * 员工档案聚合页（A11-1 / A11-2）
 * 存在的理由：一期员工页冻结不可改，而管理员需要「看某个人时，人事 / 流程 / 工资在一屏内」，
 * 所以把跨模块的只读画像收在一个页面里，各模块仍由各自页面维护，本页只读不写。
 *
 * MVP 裁剪：原「KPI 考核」卡随 KPI 模块下架移除（含 /api/kpi 取数与 KpiGauge 环图），
 * 本页只保留人事档案、入离职流程与工资单三块。
 *
 * 窄屏退让：el-descriptions 在 <1200px 时降为 1 列（A5-3 同一规则），避免长字段换行挤在一起。
 */
const route = useRoute()
const employeeId = computed(() => Number(route.params.id))

const CONTRACT_WARN_DAYS = 30

const loading = ref(false)
const baseError = ref(false)
const hrError = ref(false)

const employee = ref({})
const hrProfile = ref(null)
const flowList = ref([])
const payrollList = ref([])

/** <1200px 降 1 列：抽屉/详情页窄视口下两列会把长字段压到 200px 以内 */
const columnCount = ref(window.innerWidth < 1200 ? 1 : 2)

const profileName = computed(() => employee.value.realName || `员工 #${employeeId.value}`)
const roleLabel = computed(() => ROLE_LABEL[employee.value.role] || employee.value.role || '—')

const headerSub = computed(() => {
  const parts = [employee.value.username, employee.value.stationName, employee.value.deptName].filter(Boolean)
  return parts.length ? parts.join(' · ') : ''
})

/** 合同到期预警（C2）：>30 天不标记，剩余 0–30 天 warning，已过期 danger */
const contractWarn = computed(() => {
  const end = hrProfile.value && hrProfile.value.contractEnd
  if (!end) return ''
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const endDate = new Date(`${end}T00:00:00`)
  const days = Math.round((endDate - today) / 86400000)
  if (days < 0) return 'EXPIRED'
  if (days <= CONTRACT_WARN_DAYS) return 'SOON'
  return ''
})

async function loadBase() {
  baseError.value = false
  try {
    employee.value = await getEmployee(employeeId.value)
  } catch (e) {
    baseError.value = true
  }
}

async function loadHr() {
  hrError.value = false
  try {
    hrProfile.value = await getHrProfile(employeeId.value)
  } catch (e) {
    // 9301（未建档）不是故障：转成空态引导，只有真正的加载失败才进错误态
    if (e && e.code === 9301) hrProfile.value = null
    else hrError.value = true
  }
}

/** 契约没有「按员工查流程」的接口，故取列表后按 employeeId 过滤；列表上限 100 条，超出需后端补参数 */
async function loadFlows() {
  const [onboardPage, offboardPage] = await Promise.all([
    getOnboardings({ pageNum: 1, pageSize: 100 }),
    getOffboardings({ pageNum: 1, pageSize: 100 })
  ])
  flowList.value = [...(onboardPage.list || []), ...(offboardPage.list || [])].filter(
    (flow) => Number(flow.employeeId) === employeeId.value
  )
}

async function loadPayrolls() {
  const page = await getPayrolls({ employeeId: employeeId.value, pageNum: 1, pageSize: 20 })
  payrollList.value = page.list || []
}

async function loadAll() {
  loading.value = true
  await Promise.all([
    loadBase(),
    loadHr(),
    loadFlows().catch(() => {
      flowList.value = []
    }),
    loadPayrolls().catch(() => {
      payrollList.value = []
    })
  ])
  loading.value = false
}

onMounted(loadAll)
</script>

<style scoped lang="scss">
.emp-detail {
  &__card {
    margin-bottom: var(--sp-4);
  }

  &__card-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--sp-3);
  }

  &__card-title {
    font-size: var(--fs-h3);
    font-weight: var(--fw-semibold);
    color: var(--text-1);
  }

  &__hint {
    margin: var(--sp-3) 0 0;
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    color: var(--text-3);
  }

  &__net {
    font-weight: var(--fw-semibold);
    color: var(--text-1);
  }
}
</style>
