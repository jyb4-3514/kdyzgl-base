<template>
  <div class="employee-page">
    <!-- 筛选区：关键字/部门树/驿站下拉/状态 -->
    <el-card shadow="never" class="filter-card">
      <el-form inline @submit.prevent>
        <el-form-item label="关键字">
          <el-input
            v-model="query.keyword"
            placeholder="姓名 / 登录账号 / 手机号"
            clearable
            style="width: 220px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="部门">
          <el-tree-select
            v-model="query.deptId"
            :data="deptTree"
            :props="treeProps"
            node-key="id"
            check-strictly
            clearable
            placeholder="全部部门（含子部门）"
            style="width: 190px"
          />
        </el-form-item>
        <el-form-item label="驿站">
          <el-select v-model="query.stationId" clearable placeholder="全部驿站" style="width: 160px">
            <el-option v-for="item in stations" :key="item.id" :label="item.stationName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 110px">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <!-- 工具栏：新增/导入/导出 + 刷新 -->
      <div class="table-toolbar">
        <div class="toolbar-left">
          <el-button type="primary" @click="openCreateDialog">新增员工</el-button>
          <el-button @click="openImportDialog">批量导入</el-button>
          <el-button :loading="exporting" @click="handleExport">导出</el-button>
        </div>
        <el-button :icon="Refresh" circle text :loading="loading" @click="refreshPage" />
      </div>

      <!-- 分页表格：手机号脱敏展示（后端出参已脱敏）、性别/角色/状态中文映射 -->
      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column label="登录账号" prop="username" min-width="110" />
        <el-table-column label="姓名" prop="realName" min-width="90" show-overflow-tooltip />
        <el-table-column label="性别" width="70" align="center">
          <template #default="{ row }">{{ genderMap[row.gender] || '未知' }}</template>
        </el-table-column>
        <el-table-column label="手机号" prop="phone" min-width="120" />
        <el-table-column label="部门" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.deptName || '-' }}</template>
        </el-table-column>
        <el-table-column label="驿站" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.stationName || '-' }}</template>
        </el-table-column>
        <el-table-column label="角色" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'info'" effect="plain">
              {{ roleMap[row.role] || row.role }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="入职日期" width="105" align="center">
          <template #default="{ row }">{{ row.entryDate || '-' }}</template>
        </el-table-column>
        <el-table-column label="最后登录" min-width="150">
          <template #default="{ row }">{{ row.lastLoginTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
            <el-button link type="primary" :disabled="isSelf(row)" @click="openResetDialog(row)">
              重置密码
            </el-button>
            <el-button
              link
              :type="row.status === 1 ? 'warning' : 'success'"
              :disabled="isSelf(row)"
              @click="handleToggleStatus(row)"
            >
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-button link type="danger" :disabled="isSelf(row)" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="fetchList"
        />
      </div>
    </el-card>

    <!-- 新增/编辑员工弹窗 -->
    <el-dialog
      v-model="formDialog.visible"
      :title="formDialog.isEdit ? '编辑员工' : '新增员工'"
      width="680px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form ref="employeeFormRef" :model="employeeForm" :rules="employeeRules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="登录账号" prop="username">
              <el-input
                v-model.trim="employeeForm.username"
                :disabled="formDialog.isEdit"
                maxlength="30"
                placeholder="字母开头，4-30 位字母/数字/下划线"
              />
              <div v-if="formDialog.isEdit" class="form-tip">登录账号创建后不可修改</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item v-if="!formDialog.isEdit" label="初始密码" prop="password">
              <el-input
                v-model="employeeForm.password"
                type="password"
                show-password
                placeholder="8-20 位，须包含字母和数字"
              />
            </el-form-item>
            <el-form-item v-else label="登录密码">
              <el-input disabled placeholder="如需变更请使用列表「重置密码」" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="姓名" prop="realName">
              <el-input v-model.trim="employeeForm.realName" maxlength="50" placeholder="请输入姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号" prop="phone">
              <el-input
                v-model.trim="employeeForm.phone"
                maxlength="11"
                :placeholder="formDialog.isEdit ? '请输入完整手机号（原号码不回显）' : '请输入 11 位手机号'"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="性别" prop="gender">
              <el-radio-group v-model="employeeForm.gender">
                <el-radio :value="0">未知</el-radio>
                <el-radio :value="1">男</el-radio>
                <el-radio :value="2">女</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="角色" prop="role">
              <el-radio-group v-model="employeeForm.role">
                <el-radio value="ADMIN">管理员</el-radio>
                <el-radio value="STAFF">员工</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属部门" prop="deptId">
              <el-tree-select
                v-model="employeeForm.deptId"
                :data="deptTree"
                :props="treeProps"
                node-key="id"
                check-strictly
                clearable
                placeholder="请选择部门（可选）"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属驿站" prop="stationId">
              <el-select v-model="employeeForm.stationId" clearable placeholder="仅可选启用中的驿站" style="width: 100%">
                <el-option v-for="item in enabledStations" :key="item.id" :label="item.stationName" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="入职日期" prop="entryDate">
              <el-date-picker
                v-model="employeeForm.entryDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择入职日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input
                v-model="employeeForm.remark"
                type="textarea"
                :rows="2"
                maxlength="255"
                show-word-limit
                placeholder="选填，最多 255 字符"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="formDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="formDialog.submitting" @click="submitEmployeeForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码弹窗 -->
    <el-dialog
      v-model="resetDialog.visible"
      :title="`重置密码 - ${resetDialog.employee ? resetDialog.employee.realName : ''}`"
      width="440px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-alert
        class="reset-tip"
        type="warning"
        :closable="false"
        title="重置后该账号将被强制下线，需使用新密码重新登录并强制改密"
        show-icon
      />
      <el-form ref="resetFormRef" :model="resetForm" :rules="resetRules" label-width="90px">
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="resetForm.newPassword"
            type="password"
            show-password
            placeholder="8-20 位，须包含字母和数字"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="resetDialog.submitting" @click="submitResetPassword">确定</el-button>
      </template>
    </el-dialog>

    <!-- 批量导入弹窗 -->
    <el-dialog v-model="importDialog.visible" title="批量导入员工" width="600px" :close-on-click-modal="false">
      <div class="import-toolbar">
        <el-button :loading="templateDownloading" link type="primary" @click="handleDownloadTemplate">
          <el-icon><Download /></el-icon>下载导入模板
        </el-button>
        <span class="import-tip">模板仅含表头与批注说明，无示例数据行</span>
      </div>
      <el-upload
        ref="uploadRef"
        drag
        accept=".xlsx"
        :limit="1"
        :auto-upload="false"
        :on-change="handleImportFileChange"
        :on-remove="handleImportFileRemove"
        :on-exceed="handleImportExceed"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">将 .xlsx 文件拖到此处，或<em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip">仅支持 .xlsx 文件，大小不超过 10MB，数据行不超过 1000 行；整批校验，全部通过才会入库</div>
        </template>
      </el-upload>

      <!-- 导入成功结果 -->
      <el-alert
        v-if="importSummary && !importSummary.failed"
        class="import-result"
        type="success"
        :closable="false"
        :title="`导入成功：共 ${importSummary.total} 条数据，全部入库`"
        show-icon
      />
      <!-- 5003 行级错误明细 -->
      <template v-if="importSummary && importSummary.failed">
        <el-alert
          class="import-result"
          type="error"
          :closable="false"
          :title="`导入失败：共 ${importSummary.total} 条数据，${importSummary.failCount} 行校验未通过，全部数据未入库，请修正后重新上传`"
          show-icon
        />
        <el-table :data="importErrorRows" border size="small" max-height="260">
          <el-table-column label="行号" prop="row" width="70" align="center" />
          <el-table-column label="字段" prop="field" width="110" />
          <el-table-column label="失败原因" prop="message" min-width="200" show-overflow-tooltip />
        </el-table>
      </template>

      <template #footer>
        <el-button @click="importDialog.visible = false">关闭</el-button>
        <el-button type="primary" :loading="importDialog.submitting" :disabled="!importFile" @click="submitImport">
          开始导入
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  getEmployees,
  getEmployee,
  createEmployee,
  updateEmployee,
  deleteEmployee,
  updateEmployeeStatus,
  resetEmployeePassword,
  downloadImportTemplate,
  importEmployees,
  exportEmployees
} from '../../api/employee'
import { getDepartmentTree } from '../../api/department'
import { getStations } from '../../api/station'
import { useAuthStore } from '../../stores/auth'
import { saveResponseFile, formatDateCompact } from '../../utils/download'

const authStore = useAuthStore()

// 常量映射
const genderMap = { 0: '未知', 1: '男', 2: '女' }
const roleMap = { ADMIN: '管理员', STAFF: '员工' }
const treeProps = { label: 'deptName', children: 'children' }

// 列表状态
const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  deptId: null,
  stationId: null,
  status: null
})

// 筛选数据源
const deptTree = ref([])
const stations = ref([])
// 新增/编辑员工只能归属启用中的驿站（api.md 4004）
const enabledStations = computed(() => stations.value.filter((item) => item.status === 1))

function hasValue(value) {
  return value !== null && value !== undefined && value !== ''
}

async function fetchList() {
  loading.value = true
  try {
    const params = { pageNum: query.pageNum, pageSize: query.pageSize }
    if (hasValue(query.keyword)) params.keyword = query.keyword
    if (hasValue(query.deptId)) params.deptId = query.deptId
    if (hasValue(query.stationId)) params.stationId = query.stationId
    if (hasValue(query.status)) params.status = query.status
    const data = await getEmployees(params)
    list.value = (data && data.list) || []
    total.value = (data && data.total) || 0
  } catch (err) {
    // 错误已由拦截器统一提示
  } finally {
    loading.value = false
  }
}

async function loadFilterData() {
  try {
    deptTree.value = (await getDepartmentTree()) || []
  } catch (err) {
    /* 已提示 */
  }
  try {
    stations.value = (await getStations()) || []
  } catch (err) {
    /* 已提示 */
  }
}

function handleSearch() {
  query.pageNum = 1
  fetchList()
}

function handleReset() {
  query.keyword = ''
  query.deptId = null
  query.stationId = null
  query.status = null
  handleSearch()
}

function handleSizeChange() {
  query.pageNum = 1
  fetchList()
}

function refreshPage() {
  fetchList()
  loadFilterData()
}

onMounted(() => {
  fetchList()
  loadFilterData()
})

/* ==================== 新增/编辑员工 ==================== */
const formDialog = reactive({ visible: false, isEdit: false, submitting: false })
const employeeFormRef = ref()
const employeeForm = reactive({
  id: null,
  username: '',
  password: '',
  realName: '',
  phone: '',
  gender: 0,
  deptId: null,
  stationId: null,
  role: 'STAFF',
  entryDate: null,
  remark: ''
})

// 密码强度校验：8-20 位且同时包含字母和数字（与后端规则一致）
function validatePasswordStrength(rule, value, callback) {
  if (!value) {
    callback(new Error('请输入密码'))
    return
  }
  if (value.length < 8 || value.length > 20 || !/[A-Za-z]/.test(value) || !/\d/.test(value)) {
    callback(new Error('密码须为 8-20 位，且同时包含字母和数字'))
    return
  }
  callback()
}

const employeeRules = computed(() => ({
  username: [
    { required: true, message: '请输入登录账号', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z][a-zA-Z0-9_]{3,29}$/,
      message: '账号须以字母开头，4-30 位字母/数字/下划线',
      trigger: 'blur'
    }
  ],
  password: formDialog.isEdit
    ? []
    : [
        { required: true, message: '请输入初始密码', trigger: 'blur' },
        { validator: validatePasswordStrength, trigger: 'blur' }
      ],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }]
}))

function resetEmployeeForm() {
  employeeForm.id = null
  employeeForm.username = ''
  employeeForm.password = ''
  employeeForm.realName = ''
  employeeForm.phone = ''
  employeeForm.gender = 0
  employeeForm.deptId = null
  employeeForm.stationId = null
  employeeForm.role = 'STAFF'
  employeeForm.entryDate = null
  employeeForm.remark = ''
}

function openCreateDialog() {
  resetEmployeeForm()
  formDialog.isEdit = false
  formDialog.visible = true
}

async function openEditDialog(row) {
  resetEmployeeForm()
  formDialog.isEdit = true
  // 拉取详情保证数据最新；手机号出参脱敏，编辑时需重新输入完整手机号
  try {
    const detail = await getEmployee(row.id)
    employeeForm.id = detail.id
    employeeForm.username = detail.username
    employeeForm.realName = detail.realName
    employeeForm.phone = ''
    employeeForm.gender = detail.gender != null ? detail.gender : 0
    employeeForm.deptId = detail.deptId != null ? detail.deptId : null
    employeeForm.stationId = detail.stationId != null ? detail.stationId : null
    employeeForm.role = detail.role
    employeeForm.entryDate = detail.entryDate || null
    employeeForm.remark = detail.remark || ''
  } catch (err) {
    return
  }
  formDialog.visible = true
}

async function submitEmployeeForm() {
  const valid = await employeeFormRef.value.validate().catch(() => false)
  if (!valid) return

  formDialog.submitting = true
  try {
    if (formDialog.isEdit) {
      // 编辑：无 username / password 字段（api.md 4.3.4）
      await updateEmployee(employeeForm.id, {
        realName: employeeForm.realName,
        phone: employeeForm.phone,
        gender: employeeForm.gender,
        deptId: hasValue(employeeForm.deptId) ? employeeForm.deptId : null,
        stationId: hasValue(employeeForm.stationId) ? employeeForm.stationId : null,
        role: employeeForm.role,
        entryDate: employeeForm.entryDate || null,
        remark: employeeForm.remark || null
      })
      ElMessage.success('员工信息已更新')
    } else {
      await createEmployee({
        username: employeeForm.username,
        password: employeeForm.password,
        realName: employeeForm.realName,
        phone: employeeForm.phone,
        gender: employeeForm.gender,
        deptId: hasValue(employeeForm.deptId) ? employeeForm.deptId : null,
        stationId: hasValue(employeeForm.stationId) ? employeeForm.stationId : null,
        role: employeeForm.role,
        entryDate: employeeForm.entryDate || null,
        remark: employeeForm.remark || null
      })
      ElMessage.success('员工创建成功，该账号首次登录时将强制修改密码')
    }
    formDialog.visible = false
    fetchList()
  } catch (err) {
    // 业务码（1003/2003/3001/4001/4004 等）已由拦截器按后端 message 提示
  } finally {
    formDialog.submitting = false
  }
}

/* ==================== 启停/删除/重置密码 ==================== */
function isSelf(row) {
  return !!authStore.user && row.id === authStore.user.id
}

async function handleToggleStatus(row) {
  const target = row.status === 1 ? 0 : 1
  const actionText = target === 1 ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(
      target === 1
        ? `确定启用员工「${row.realName}」吗？`
        : `确定禁用员工「${row.realName}」吗？禁用后该账号将被强制下线且无法登录。`,
      '操作确认',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
  } catch (e) {
    return
  }
  try {
    await updateEmployeeStatus(row.id, target)
    ElMessage.success(`${actionText}成功`)
    row.status = target
  } catch (err) {
    /* 2001/2002 等已提示 */
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `删除后员工「${row.realName}」将无法登录且从列表移除，确定删除吗？`,
      '删除确认',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch (e) {
    return
  }
  try {
    await deleteEmployee(row.id)
    ElMessage.success('删除成功')
    // 当前页删除最后一条时回退一页
    if (list.value.length === 1 && query.pageNum > 1) {
      query.pageNum -= 1
    }
    fetchList()
  } catch (err) {
    /* 2001/2002 等已提示 */
  }
}

const resetDialog = reactive({ visible: false, submitting: false, employee: null })
const resetFormRef = ref()
const resetForm = reactive({ newPassword: '' })
const resetRules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { validator: validatePasswordStrength, trigger: 'blur' }
  ]
}

function openResetDialog(row) {
  resetDialog.employee = row
  resetForm.newPassword = ''
  resetDialog.visible = true
}

async function submitResetPassword() {
  const valid = await resetFormRef.value.validate().catch(() => false)
  if (!valid) return
  resetDialog.submitting = true
  try {
    await resetEmployeePassword(resetDialog.employee.id, resetForm.newPassword)
    ElMessage.success('密码已重置，该账号需使用新密码重新登录并强制改密')
    resetDialog.visible = false
  } catch (err) {
    /* 已提示 */
  } finally {
    resetDialog.submitting = false
  }
}

/* ==================== 导入/导出 ==================== */
const importDialog = reactive({ visible: false, submitting: false })
const uploadRef = ref()
const importFile = ref(null)
const importSummary = ref(null)
const importErrorRows = ref([])

function openImportDialog() {
  importFile.value = null
  importSummary.value = null
  importErrorRows.value = []
  importDialog.visible = true
  nextTick(() => uploadRef.value && uploadRef.value.clearFiles())
}

function handleImportFileChange(uploadFile) {
  importFile.value = null
  const raw = uploadFile.raw
  if (!raw) return
  // 前端预校验与后端一致：仅 .xlsx、≤10MB（api.md 4.3.9）
  if (!/\.xlsx$/i.test(raw.name)) {
    ElMessage.error('仅支持 .xlsx 格式文件')
    uploadRef.value && uploadRef.value.clearFiles()
    return
  }
  if (raw.size > 10 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过 10MB')
    uploadRef.value && uploadRef.value.clearFiles()
    return
  }
  importFile.value = raw
}

function handleImportFileRemove() {
  importFile.value = null
}

function handleImportExceed() {
  ElMessage.warning('一次只能上传一个文件，请先移除已选文件')
}

async function submitImport() {
  if (!importFile.value) {
    ElMessage.warning('请先选择要导入的文件')
    return
  }
  importDialog.submitting = true
  importSummary.value = null
  importErrorRows.value = []
  try {
    const data = await importEmployees(importFile.value)
    importSummary.value = {
      total: data.total,
      failed: false,
      failCount: 0
    }
    ElMessage.success(`导入成功，共入库 ${data.successCount != null ? data.successCount : data.total} 条数据`)
    fetchList()
  } catch (err) {
    // 5003：渲染行级错误明细（行号/字段/原因）
    if (err.code === 5003 && err.data) {
      importSummary.value = {
        total: err.data.total,
        failed: true,
        failCount: err.data.failCount
      }
      importErrorRows.value = err.data.errors || []
    }
    // 其余错误（5001/5002 等）已由拦截器提示
  } finally {
    importDialog.submitting = false
  }
}

const templateDownloading = ref(false)
const exporting = ref(false)

async function handleDownloadTemplate() {
  templateDownloading.value = true
  try {
    const response = await downloadImportTemplate()
    saveResponseFile(response, '员工导入模板.xlsx')
  } catch (err) {
    /* 已提示 */
  } finally {
    templateDownloading.value = false
  }
}

async function handleExport() {
  exporting.value = true
  try {
    // 按当前筛选条件全量导出（不含分页参数，api.md 4.3.10）
    const params = {}
    if (hasValue(query.keyword)) params.keyword = query.keyword
    if (hasValue(query.deptId)) params.deptId = query.deptId
    if (hasValue(query.stationId)) params.stationId = query.stationId
    if (hasValue(query.status)) params.status = query.status
    const response = await exportEmployees(params)
    saveResponseFile(response, `员工数据_${formatDateCompact()}.xlsx`)
  } catch (err) {
    /* 已提示 */
  } finally {
    exporting.value = false
  }
}
</script>

<style scoped lang="scss">
.employee-page {
  .import-toolbar {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 12px;

    .import-tip {
      font-size: 12px;
      color: #909399;
    }
  }

  .reset-tip {
    margin-bottom: 16px;
  }

  .import-result {
    margin-top: 12px;
  }
}
</style>
