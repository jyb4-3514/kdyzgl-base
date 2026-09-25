<template>
  <div class="station-page">
    <el-card shadow="never" class="filter-card">
      <el-form inline @submit.prevent>
        <el-form-item label="状态">
          <el-select
            v-model="queryStatus"
            clearable
            placeholder="全部"
            style="width: 140px"
            @change="loadList"
          >
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="openCreateDialog">新增驿站</el-button>
          <el-button :icon="Refresh" circle text :loading="loading" @click="loadList" />
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <!-- 全量列表：编码/名称/联系人/电话脱敏/状态开关/员工数/地址 -->
      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column label="驿站编码" prop="code" width="120" />
        <el-table-column label="驿站名称" prop="stationName" min-width="130" show-overflow-tooltip />
        <el-table-column label="联系人" width="100">
          <template #default="{ row }">{{ row.contactPerson || '-' }}</template>
        </el-table-column>
        <el-table-column label="联系电话" width="130">
          <template #default="{ row }">{{ row.contactPhone || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="员工数" prop="employeeCount" width="80" align="center" />
        <el-table-column label="地址" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.address || '-' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="170" />
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
            <el-button
              link
              :type="row.status === 1 ? 'warning' : 'success'"
              @click="handleToggleStatus(row)"
            >
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑驿站弹窗 -->
    <el-dialog
      v-model="dialog.visible"
      :title="dialog.isEdit ? '编辑驿站' : '新增驿站'"
      width="520px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="驿站编码" prop="code">
          <el-input
            v-model.trim="form.code"
            maxlength="50"
            placeholder="2-50 位字母/数字/下划线/短横线，全局唯一"
            :disabled="dialog.submitting"
          />
          <div v-if="dialog.isEdit" class="form-tip">编码二期包裹数据同步上线后将冻结，一期可修改</div>
        </el-form-item>
        <el-form-item label="驿站名称" prop="stationName">
          <el-input v-model.trim="form.stationName" maxlength="50" placeholder="请输入驿站名称" />
        </el-form-item>
        <el-form-item label="联系人" prop="contactPerson">
          <el-input v-model.trim="form.contactPerson" maxlength="50" placeholder="选填" />
        </el-form-item>
        <el-form-item label="联系电话" prop="contactPhone">
          <el-input
            v-model.trim="form.contactPhone"
            maxlength="11"
            :placeholder="dialog.isEdit ? '请输入完整手机号（原号码不回显，留空即清空）' : '选填，11 位手机号'"
          />
        </el-form-item>
        <el-form-item label="地址" prop="address">
          <el-input v-model.trim="form.address" maxlength="255" placeholder="选填" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model.trim="form.remark"
            type="textarea"
            :rows="2"
            maxlength="255"
            show-word-limit
            placeholder="选填"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  getStations,
  createStation,
  updateStation,
  updateStationStatus,
  deleteStation
} from '../../api/station'

const loading = ref(false)
const list = ref([])
const queryStatus = ref(null)

async function loadList() {
  loading.value = true
  try {
    const params = {}
    if (queryStatus.value !== null && queryStatus.value !== undefined && queryStatus.value !== '') {
      params.status = queryStatus.value
    }
    list.value = (await getStations(params)) || []
  } catch (err) {
    /* 已提示 */
  } finally {
    loading.value = false
  }
}

onMounted(loadList)

/* ==================== 新增/编辑 ==================== */
const dialog = reactive({ visible: false, isEdit: false, submitting: false })
const formRef = ref()
const form = reactive({
  id: null,
  code: '',
  stationName: '',
  contactPerson: '',
  contactPhone: '',
  address: '',
  remark: ''
})

const rules = {
  code: [
    { required: true, message: '请输入驿站编码', trigger: 'blur' },
    {
      pattern: /^[A-Za-z0-9_-]{2,50}$/,
      message: '编码须为 2-50 位字母、数字、下划线或短横线',
      trigger: 'blur'
    }
  ],
  stationName: [
    { required: true, message: '请输入驿站名称', trigger: 'blur' },
    { min: 1, max: 50, message: '名称须为 1-50 字符', trigger: 'blur' }
  ],
  // 非必填，但填写时须为合法手机号（api.md 4.5.2）
  contactPhone: [
    {
      pattern: /^1[3-9]\d{9}$/,
      message: '联系电话格式不正确，须为 11 位手机号',
      trigger: 'blur'
    }
  ]
}

function openCreateDialog() {
  form.id = null
  form.code = ''
  form.stationName = ''
  form.contactPerson = ''
  form.contactPhone = ''
  form.address = ''
  form.remark = ''
  dialog.isEdit = false
  dialog.visible = true
}

function openEditDialog(row) {
  form.id = row.id
  form.code = row.code
  form.stationName = row.stationName
  form.contactPerson = row.contactPerson || ''
  // 联系电话出参脱敏，编辑时不回显，需用户重新输入完整号码
  form.contactPhone = ''
  form.address = row.address || ''
  form.remark = row.remark || ''
  dialog.isEdit = true
  dialog.visible = true
}

async function submitForm() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  dialog.submitting = true
  try {
    if (dialog.isEdit) {
      await updateStation(form.id, {
        code: form.code,
        stationName: form.stationName,
        contactPerson: form.contactPerson || null,
        contactPhone: form.contactPhone || null,
        address: form.address || null,
        remark: form.remark || null
      })
      ElMessage.success('驿站信息已更新')
    } else {
      await createStation({
        code: form.code,
        stationName: form.stationName,
        contactPerson: form.contactPerson || null,
        contactPhone: form.contactPhone || null,
        address: form.address || null,
        remark: form.remark || null
      })
      ElMessage.success('驿站创建成功')
    }
    dialog.visible = false
    loadList()
  } catch (err) {
    // 4002 编码重复等已由拦截器按后端 message 提示
  } finally {
    dialog.submitting = false
  }
}

/* ==================== 启停/删除 ==================== */
async function handleToggleStatus(row) {
  const target = row.status === 1 ? 0 : 1
  const actionText = target === 1 ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(
      target === 1
        ? `确定启用驿站「${row.stationName}」吗？`
        : `确定停用驿站「${row.stationName}」吗？停用后存量员工归属保留，但新增/编辑员工将不可归属该驿站。`,
      '操作确认',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
  } catch (e) {
    return
  }
  try {
    await updateStationStatus(row.id, target)
    ElMessage.success(`${actionText}成功`)
    loadList()
  } catch (err) {
    /* 已提示 */
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除驿站「${row.stationName}」吗？仅允许删除无归属员工的驿站。`,
      '删除确认',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch (e) {
    return
  }
  try {
    await deleteStation(row.id)
    ElMessage.success('删除成功')
    loadList()
  } catch (err) {
    // 4003 驿站下存在员工，已由拦截器提示
  }
}
</script>

<style scoped lang="scss">
.station-page {
  .form-tip {
    font-size: 12px;
    line-height: 1.4;
    color: var(--text-3);
  }
}
</style>
