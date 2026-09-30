<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import StateBlock from '../../../components/StateBlock.vue'
import { PAYROLL_LOG_ACTION } from '@kdyzgl/shared/constants/dict.js'
import { getManualAdjustmentSummary, getPayrollLogs, getPayrolls } from '../../../api/finance.js'
import { netImpactClass, signedMoney } from '../model/payrollAutomation.js'

/**
 * 手工调整对账视图（I-10，设计规范 §8）
 *
 * 真源是 payroll_log 的 employee_id + month 冗余列：generate 覆盖重建会物理删除旧单，
 * 按 payroll_id 关联会漏「已删单」上的加扣款留痕。
 * 下钻到留痕明细时以 employee + month 反查工资单再取 I-7；取不到（单据已被重建覆盖）走降级文案。
 */
const props = defineProps({
  stations: { type: Array, default: () => [] }
})

const month = ref('')
const stationId = ref(null)
const loading = ref(false)
const error = ref(false)
const loaded = ref(false)
const list = ref([])
const total = ref(null)

const drillVisible = ref(false)
const drillLoading = ref(false)
const drillError = ref(false)
const drillDegraded = ref(false)
const drillEmployee = ref(null)
const drillLines = ref([])

const canQuery = computed(() => !!month.value)

async function load() {
  if (!canQuery.value) {
    ElMessage.warning('请选择账期月份')
    return
  }
  loading.value = true
  error.value = false
  try {
    const params = { month: month.value }
    if (stationId.value != null) params.stationId = stationId.value
    const data = await getManualAdjustmentSummary(params)
    list.value = data.list || []
    total.value = data.total || null
    loaded.value = true
  } catch (e) {
    error.value = true
    loaded.value = false
  } finally {
    loading.value = false
  }
}

function refresh() {
  if (loaded.value) load()
}

/** 合计行：末行固定「合计」，与列表项同字段（employeeId=null） */
function summaryMethod({ columns }) {
  const keys = [null, 'additionCount', 'additionTotal', 'deductionCount', 'deductionTotal', 'netImpact', null]
  const t = total.value || {}
  return columns.map((column, index) => {
    const key = keys[index]
    if (index === 0) return '合计'
    if (!key) return '—'
    if (key === 'additionTotal') return `+${signedMoney(t[key], false)}`
    if (key === 'deductionTotal') return `-${signedMoney(t[key], false)}`
    if (key === 'netImpact') return signedMoney(t[key], true)
    return String(t[key] == null ? 0 : t[key])
  })
}

function actionText(action) {
  return (PAYROLL_LOG_ACTION[action] || {}).label || action
}

/** 下钻：按 employee + month 反查工资单拿 id → 取 I-7 留痕（ITEM_ADD / ITEM_UPDATE） */
async function openDrill(row) {
  if (!row || row.employeeId == null) return
  drillEmployee.value = row
  drillLines.value = []
  drillDegraded.value = false
  drillError.value = false
  drillVisible.value = true
  drillLoading.value = true
  try {
    const params = { month: month.value, employeeId: row.employeeId, pageNum: 1, pageSize: 100 }
    const page = await getPayrolls(params)
    const payroll = (page.list || [])[0]
    if (!payroll) {
      // 单据已被覆盖重建物理删除：按 payroll_id 下钻取不到，走降级说明（设计 §8.2）
      drillDegraded.value = true
      return
    }
    const logs = await getPayrollLogs(payroll.id)
    drillLines.value = (logs || [])
      .filter((log) => log.action === 'ITEM_ADD' || log.action === 'ITEM_UPDATE')
      .map((log) => ({
        id: log.id,
        action: log.action,
        time: log.time,
        reason: log.reason,
        operatorName: log.operatorName,
        changes: describe(log)
      }))
    if (!drillLines.value.length) drillDegraded.value = true
  } catch (e) {
    drillError.value = true
  } finally {
    drillLoading.value = false
  }
}

/** 留痕内容：ITEM_ADD 列新增项、ITEM_UPDATE 列前后金额变化与合计变化 */
function describe(log) {
  const lines = []
  const before = log.before || {}
  const after = log.after || {}
  if (log.action === 'ITEM_ADD') {
    ;(after.items || []).forEach((item) => {
      lines.push(`${item.itemType === 'ADDITION' ? '+' : '-'}${item.itemName} ${item.amount}`)
    })
  } else {
    const beforeItems = before.items || []
    ;(after.items || []).forEach((item) => {
      const prev = beforeItems.find((row) => row.itemKey === item.itemKey)
      if (prev && Number(prev.amount) !== Number(item.amount)) {
        lines.push(`${item.itemName}：${prev.amount} → ${item.amount}`)
      }
    })
  }
  if (before.netAmount !== undefined && after.netAmount !== undefined && Number(before.netAmount) !== Number(after.netAmount)) {
    lines.push(`实发：${before.netAmount} → ${after.netAmount}`)
  }
  return lines
}

/** 打印（本轮不硬做导出，契约无导出端点）：打印友好样式由浏览器承接 */
function onPrint() {
  window.print()
}

onMounted(() => {
  // 默认账期给当前月，减少一次点击；仍允许改
  const now = new Date()
  month.value = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
})

defineExpose({ refresh })
</script>

<template>
  <div class="adjust-panel">
    <div class="panel-card">
      <el-form :inline="true" class="panel-filter">
        <el-form-item label="账期" required>
          <el-date-picker
            v-model="month"
            type="month"
            value-format="YYYY-MM"
            placeholder="请选择账期月份"
            style="width: 150px"
          />
        </el-form-item>
        <el-form-item label="驿站">
          <el-select v-model="stationId" clearable placeholder="全部驿站" style="width: 160px">
            <el-option v-for="s in props.stations" :key="s.id" :label="s.stationName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :disabled="!canQuery" @click="load">查询</el-button>
          <el-button :disabled="!loaded" @click="onPrint">打印</el-button>
        </el-form-item>
      </el-form>
      <p class="panel-hint">按 payroll_log 的「员工 + 账期」聚合手工加扣款与改金额；单据被覆盖重建后仍会计入。</p>
    </div>

    <div class="panel-card panel-card--table">
      <StateBlock v-if="error" variant="error" title="对账数据加载失败" @action="load" />
      <StateBlock
        v-else-if="loaded && !list.length"
        variant="empty"
        :title="`${month} 无手工加扣款记录`"
        description="换一个账期或清空驿站筛选再试"
      />
      <StateBlock v-else-if="!loaded && !loading" variant="empty" title="请选择账期后查询" />
      <el-table
        v-else
        v-loading="loading"
        :data="list"
        size="small"
        border
        show-summary
        :summary-method="summaryMethod"
      >
        <el-table-column prop="employeeName" label="员工" min-width="120" show-overflow-tooltip />
        <el-table-column prop="additionCount" label="加款笔数" width="100" align="right" />
        <el-table-column label="加款总额" width="120" align="right">
          <template #default="{ row }">
            <span class="is-plus">+{{ signedMoney(row.additionTotal, false) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="deductionCount" label="扣款笔数" width="100" align="right" />
        <el-table-column label="扣款总额" width="120" align="right">
          <template #default="{ row }">
            <span class="is-minus">-{{ signedMoney(row.deductionTotal, false) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="净影响" width="120" align="right">
          <template #default="{ row }">
            <span :class="netImpactClass(row.netImpact)">{{ signedMoney(row.netImpact, true) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDrill(row)">明细</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="drillVisible" title="手工调整留痕明细" width="620px">
      <p class="drill-head">
        {{ drillEmployee ? drillEmployee.employeeName : '' }} · {{ month }}
      </p>
      <div v-loading="drillLoading">
        <StateBlock v-if="drillError" variant="error" title="留痕加载失败" @action="openDrill(drillEmployee)" />
        <el-empty v-else-if="drillDegraded" description="该员工当月单据已被重建覆盖，历史留痕暂不可下钻" />
        <el-timeline v-else>
          <el-timeline-item v-for="line in drillLines" :key="line.id" :timestamp="line.time" placement="top">
            <p class="drill-action">{{ actionText(line.action) }} · {{ line.operatorName }}</p>
            <ul v-if="line.changes.length" class="drill-changes">
              <li v-for="(item, index) in line.changes" :key="index">{{ item }}</li>
            </ul>
            <p v-if="line.reason" class="drill-reason">事由：{{ line.reason }}</p>
          </el-timeline-item>
        </el-timeline>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.panel-card {
  padding: var(--sp-4);
  margin-bottom: var(--sp-4);
  background: var(--surface-card);
  border: 1px solid var(--border-line);
  border-radius: var(--r-md);

  &--table {
    padding-bottom: var(--sp-3);
  }
}

.panel-filter {
  :deep(.el-form-item) {
    margin-bottom: 0;
  }
}

.panel-hint {
  margin: var(--sp-3) 0 0;
  font-size: var(--fs-caption);
  color: var(--text-3);
}

.is-plus {
  color: var(--color-success);
  font-variant-numeric: tabular-nums;
}

.is-minus {
  color: var(--color-danger);
  font-variant-numeric: tabular-nums;
}

.drill-head {
  margin: 0 0 var(--sp-3);
  font-size: var(--fs-body-strong);
  font-weight: var(--fw-medium);
  color: var(--text-1);
}

.drill-action {
  margin: 0;
  font-size: var(--fs-body);
  color: var(--text-1);
}

.drill-changes {
  padding-left: var(--sp-4);
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-caption);
  color: var(--text-2);
}

.drill-reason {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-caption);
  color: var(--text-3);
}
</style>
