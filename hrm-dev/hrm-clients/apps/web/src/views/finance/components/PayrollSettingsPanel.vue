<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import StateBlock from '../../../components/StateBlock.vue'
import { PAYROLL_SETTING_LOG_ACTION } from '@kdyzgl/shared/constants/dict.js'
import { FINANCE_CODE } from '@kdyzgl/shared/constants/errorCode.js'
import { getPayrollSetting, getPayrollSettingLogs, getPayrollSettings, savePayrollSetting } from '../../../api/finance.js'

/**
 * 驿站算薪日设置（I-1 / I-2 / I-3 / I-9，设计规范 §2）
 *
 * 9406（尚未配置）不是错误态：编辑弹层以默认值呈现、保存一次即创建。
 * 变更历史独立取数（列表接口不背负时间线），ENABLE 动作须醒目标识（M-9）。
 */
const loading = ref(false)
const error = ref(false)
const list = ref([])
const enabledFilter = ref('')

const editVisible = ref(false)
const editLoading = ref(false)
const editError = ref(false)
const saving = ref(false)
const editStation = ref(null)
const notConfigured = ref(false)
const form = ref({ enabled: 0, payrollDay: 1, payrollTime: '09:00', notifyEnabled: 1, remark: '' })
const dayError = ref('')

const logs = ref([])
const logsLoading = ref(false)
const logsError = ref(false)

const DEFAULT_FORM = { enabled: 0, payrollDay: 1, payrollTime: '09:00', notifyEnabled: 1, remark: '' }

async function load() {
  loading.value = true
  error.value = false
  try {
    const params = { pageNum: 1, pageSize: 100 }
    if (enabledFilter.value !== '') params.enabled = enabledFilter.value
    const page = await getPayrollSettings(params)
    list.value = page.list || []
  } catch (e) {
    error.value = true
  } finally {
    loading.value = false
  }
}

function refresh() {
  load()
}

function enabledText(value) {
  return value === 1 ? '已启用' : '未启用'
}

function planText(row) {
  return row.payrollDay == null || !row.payrollTime ? '尚未配置' : `每月 ${row.payrollDay} 日 ${row.payrollTime}`
}

async function openEdit(row) {
  editStation.value = row
  editVisible.value = true
  editLoading.value = true
  editError.value = false
  dayError.value = ''
  notConfigured.value = false
  try {
    const data = await getPayrollSetting(row.stationId)
    form.value = {
      enabled: Number(data.enabled) === 1 ? 1 : 0,
      payrollDay: Number(data.payrollDay) || 1,
      payrollTime: data.payrollTime || '09:00',
      notifyEnabled: Number(data.notifyEnabled) === 1 ? 1 : 0,
      remark: data.remark || ''
    }
    loadLogs(row.stationId)
  } catch (e) {
    if (e.code === FINANCE_CODE.SETTING_NOT_CONFIGURED) {
      notConfigured.value = true
      form.value = { ...DEFAULT_FORM }
      logs.value = []
    } else {
      editError.value = true
    }
  } finally {
    editLoading.value = false
  }
}

async function loadLogs(stationId) {
  logsLoading.value = true
  logsError.value = false
  try {
    const page = await getPayrollSettingLogs(stationId, { pageNum: 1, pageSize: 20 })
    logs.value = page.list || []
  } catch (e) {
    logsError.value = true
  } finally {
    logsLoading.value = false
  }
}

function validateDay() {
  const day = Number(form.value.payrollDay)
  dayError.value = Number.isInteger(day) && day >= 1 && day <= 31 ? '' : '算薪日须为 1–31 的整数，月末自动钳位到当月最后一天'
  return !dayError.value
}

async function onSave() {
  if (!validateDay()) return
  if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(form.value.payrollTime || '')) {
    ElMessage.warning('时间格式须为 HH:mm（如 09:00）')
    return
  }
  if (String(form.value.remark || '').length > 255) {
    ElMessage.warning('备注不可超过 255 字')
    return
  }
  saving.value = true
  try {
    await savePayrollSetting(editStation.value.stationId, {
      enabled: form.value.enabled,
      payrollDay: Number(form.value.payrollDay),
      payrollTime: form.value.payrollTime,
      notifyEnabled: form.value.notifyEnabled,
      remark: form.value.remark
    })
    ElMessage.success('算薪设置已保存')
    notConfigured.value = false
    await Promise.all([load(), loadLogs(editStation.value.stationId)])
  } catch (e) {
    if (e.code === FINANCE_CODE.SETTING_DAY_INVALID) dayError.value = e.message
    else ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

/* ==================== 变更历史渲染（I-9） ==================== */

const BOOL_LABEL = { 1: '开', 0: '关' }
const FIELD_LABEL = { payrollDay: '算薪日', payrollTime: '执行时间', enabled: '启用', notifyEnabled: '推送', remark: '备注' }

function fieldValue(key, value) {
  if (key === 'enabled' || key === 'notifyEnabled') return BOOL_LABEL[Number(value)] || '关'
  return value == null || value === '' ? '—' : String(value)
}

function actionText(action) {
  return (PAYROLL_SETTING_LOG_ACTION[action] || {}).label || action
}

function changeLines(log) {
  const after = log.after || {}
  const before = log.before || {}
  if (log.action === 'CREATE' || !log.before) {
    return Object.keys(FIELD_LABEL)
      .filter((key) => after[key] !== undefined)
      .map((key) => `${FIELD_LABEL[key]} ${fieldValue(key, after[key])}`)
  }
  return Object.keys(FIELD_LABEL)
    .filter((key) => after[key] !== undefined && before[key] !== after[key])
    .map((key) => `${FIELD_LABEL[key]} ${fieldValue(key, before[key])} → ${fieldValue(key, after[key])}`)
}

const editTitle = computed(() =>
  editStation.value ? `算薪设置 · ${editStation.value.stationName}` : '算薪设置'
)

onMounted(load)

defineExpose({ refresh })
</script>

<template>
  <div class="settings-panel">
    <div class="panel-card">
      <el-form :inline="true" class="panel-filter">
        <el-form-item label="启用状态">
          <el-select v-model="enabledFilter" placeholder="全部" style="width: 130px" @change="load">
            <el-option label="全部" value="" />
            <el-option label="已启用" :value="1" />
            <el-option label="未启用" :value="0" />
          </el-select>
        </el-form-item>
      </el-form>
      <p class="panel-hint">启用后系统在每驿站各自的算薪日自动生成工资单草稿并提交审核；未启用的驿站不自动跑数。</p>
    </div>

    <div class="panel-card panel-card--table">
      <StateBlock v-if="error" variant="error" title="算薪配置加载失败" @action="load" />
      <StateBlock v-else-if="!loading && !list.length" variant="empty" title="还没有可配置的驿站" description="请先在 PC 端维护驿站" />
      <el-table v-else v-loading="loading" :data="list" size="small" border>
        <el-table-column prop="stationName" label="驿站" min-width="130" />
        <el-table-column label="启用状态" width="100">
          <template #default="{ row }">
            <span :class="row.enabled === 1 ? 'state-on' : 'state-off'">{{ enabledText(row.enabled) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="算薪设置" min-width="160">
          <template #default="{ row }">
            <span :class="{ 'is-muted': row.payrollDay == null }">{{ planText(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="推送管理员" width="110" align="center">
          <template #default="{ row }">{{ row.notifyEnabled == null ? '—' : row.notifyEnabled === 1 ? '开' : '关' }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '—' }}</template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="160">
          <template #default="{ row }">{{ row.updateTime || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="editVisible" :title="editTitle" width="620px">
      <div v-loading="editLoading">
        <StateBlock v-if="editError" variant="error" title="算薪配置加载失败" @action="() => openEdit(editStation)" />
        <template v-else>
          <el-alert
            v-if="notConfigured"
            type="warning"
            :closable="false"
            title="该驿站尚未配置算薪设置，保存一次即可创建"
            class="edit-alert"
          />
          <el-form label-width="120px" class="edit-form">
            <el-form-item label="启用自动算薪">
              <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
              <span class="edit-hint">未启用时该驿站不自动跑数</span>
            </el-form-item>
            <el-form-item label="算薪日" :error="dayError" required>
              <el-input-number v-model="form.payrollDay" :min="1" :max="31" :step="1" @blur="validateDay" />
              <span class="edit-hint">当月无该日时，自动取当月最后一天（如 31 → 4 月 30 日）；范围 1–31</span>
            </el-form-item>
            <el-form-item label="执行时间" required>
              <el-time-picker v-model="form.payrollTime" value-format="HH:mm" format="HH:mm" placeholder="HH:mm" />
              <span class="edit-hint">墙钟按 Asia/Shanghai，格式 HH:mm</span>
            </el-form-item>
            <el-form-item label="生成后推送管理员">
              <el-switch v-model="form.notifyEnabled" :active-value="1" :inactive-value="0" />
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="255" show-word-limit placeholder="可空，≤255 字" />
            </el-form-item>
          </el-form>

          <h4 class="edit-title">变更历史</h4>
          <div v-if="logsLoading" v-loading="true" class="edit-logs-sk" />
          <StateBlock v-else-if="logsError" variant="error" title="变更历史加载失败" @action="loadLogs(editStation.stationId)" />
          <el-empty v-else-if="!logs.length" description="暂无变更记录" />
          <el-timeline v-else class="edit-timeline">
            <el-timeline-item
              v-for="log in logs"
              :key="log.id"
              :timestamp="`${log.time} · ${log.operatorName}（${log.operatorRole}）`"
              placement="top"
            >
              <span class="log-tag" :class="{ 'log-tag--enable': log.action === 'ENABLE' }">{{ actionText(log.action) }}</span>
              <ul class="log-changes">
                <li v-for="(line, index) in changeLines(log)" :key="index">{{ line }}</li>
              </ul>
              <p v-if="log.remark" class="log-remark">备注：{{ log.remark }}</p>
            </el-timeline-item>
          </el-timeline>
        </template>
      </div>
      <template #footer>
        <el-button @click="editVisible = false">关闭</el-button>
        <el-button type="primary" :loading="saving" :disabled="editError" @click="onSave">保存设置</el-button>
      </template>
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

.state-on {
  color: var(--color-success);
}

.state-off {
  color: var(--text-3);
}

.is-muted {
  color: var(--color-warning);
}

.edit-alert {
  margin-bottom: var(--sp-3);
}

.edit-form {
  :deep(.el-form-item) {
    margin-bottom: var(--sp-4);
  }
}

.edit-hint {
  margin-left: var(--sp-2);
  font-size: var(--fs-caption);
  color: var(--text-3);
}

.edit-title {
  margin: var(--sp-4) 0 var(--sp-3);
  font-size: var(--fs-body-strong);
  font-weight: var(--fw-semibold);
  color: var(--text-1);
}

.edit-logs-sk {
  height: 120px;
}

.edit-timeline {
  padding-left: var(--sp-1);
}

.log-tag {
  display: inline-flex;
  align-items: center;
  height: var(--tag-h);
  padding: 0 var(--tag-pad-x);
  font-size: var(--fs-micro);
  color: var(--text-2);
  background: var(--surface-sub);
  border-radius: var(--r-full);
}

.log-tag--enable {
  color: var(--text-on-dark);
  background: var(--color-warning);
}

.log-changes {
  padding-left: var(--sp-4);
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-caption);
  color: var(--text-2);
}

.log-remark {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-caption);
  color: var(--text-3);
}
</style>
