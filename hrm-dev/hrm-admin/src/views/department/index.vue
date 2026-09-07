<template>
  <div class="department-page">
    <el-card shadow="never">
      <div class="table-toolbar">
        <el-button type="primary" @click="openCreateDialog()">新增部门</el-button>
        <el-button :icon="Refresh" circle text :loading="loading" @click="loadTree" />
      </div>

      <!-- 树形表格：部门名称/直属员工数/排序 -->
      <el-table
        v-loading="loading"
        :data="deptTree"
        row-key="id"
        :tree-props="{ children: 'children' }"
        default-expand-all
        border
      >
        <el-table-column label="部门名称" prop="deptName" min-width="280" />
        <el-table-column label="直属员工数" prop="employeeCount" width="120" align="center">
          <template #default="{ row }">{{ row.employeeCount }}</template>
        </el-table-column>
        <el-table-column label="排序" prop="sortOrder" width="90" align="center" />
        <el-table-column label="操作" width="240" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openCreateDialog(row)">新增子部门</el-button>
            <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑部门弹窗 -->
    <el-dialog
      v-model="dialog.visible"
      :title="dialog.isEdit ? '编辑部门' : '新增部门'"
      width="480px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="父部门">
          <!-- 编辑时父级不可改（决策 D12：接口拒绝 parentId 变更） -->
          <el-input v-if="dialog.isEdit" :model-value="parentName" disabled />
          <el-tree-select
            v-else
            v-model="form.parentId"
            :data="parentOptions"
            :props="treeProps"
            node-key="id"
            check-strictly
            default-expand-all
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="部门名称" prop="deptName">
          <el-input v-model.trim="form.deptName" maxlength="50" placeholder="同一父部门下名称不可重复" />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" />
          <span class="sort-tip">数值越小排序越靠前</span>
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
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  getDepartmentTree,
  createDepartment,
  updateDepartment,
  deleteDepartment
} from '../../api/department'

const treeProps = { label: 'deptName', children: 'children' }

const loading = ref(false)
const deptTree = ref([])

async function loadTree() {
  loading.value = true
  try {
    deptTree.value = (await getDepartmentTree()) || []
  } catch (err) {
    /* 已提示 */
  } finally {
    loading.value = false
  }
}

onMounted(loadTree)

/* ==================== 新增/编辑 ==================== */
const dialog = reactive({ visible: false, isEdit: false, submitting: false })
const formRef = ref()
const form = reactive({
  id: null,
  parentId: 0,
  deptName: '',
  sortOrder: 0
})

const rules = {
  deptName: [
    { required: true, message: '请输入部门名称', trigger: 'blur' },
    { min: 1, max: 50, message: '部门名称须为 1-50 字符', trigger: 'blur' }
  ]
}

// 父部门选项：根部门（顶级）+ 现有部门树
const parentOptions = computed(() => [
  { id: 0, deptName: '根部门（顶级）', children: deptTree.value }
])

function findDeptName(nodes, id) {
  for (const node of nodes) {
    if (node.id === id) return node.deptName
    if (node.children && node.children.length) {
      const found = findDeptName(node.children, id)
      if (found) return found
    }
  }
  return null
}

const parentName = computed(() => {
  if (form.parentId === 0) return '根部门（顶级）'
  return findDeptName(deptTree.value, form.parentId) || '未知部门'
})

function openCreateDialog(parentRow) {
  form.id = null
  form.parentId = parentRow ? parentRow.id : 0
  form.deptName = ''
  form.sortOrder = 0
  dialog.isEdit = false
  dialog.visible = true
}

function openEditDialog(row) {
  form.id = row.id
  form.parentId = row.parentId
  form.deptName = row.deptName
  form.sortOrder = row.sortOrder
  dialog.isEdit = true
  dialog.visible = true
}

async function submitForm() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  dialog.submitting = true
  try {
    if (dialog.isEdit) {
      // 编辑仅提交名称与排序，不带 parentId（api.md 4.4.3）
      await updateDepartment(form.id, {
        deptName: form.deptName,
        sortOrder: form.sortOrder
      })
      ElMessage.success('部门已更新')
    } else {
      await createDepartment({
        parentId: form.parentId,
        deptName: form.deptName,
        sortOrder: form.sortOrder
      })
      ElMessage.success('部门创建成功')
    }
    dialog.visible = false
    loadTree()
  } catch (err) {
    // 3001/3004 等已由拦截器按后端 message 提示
  } finally {
    dialog.submitting = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除部门「${row.deptName}」吗？仅允许删除无子部门且无归属员工的部门。`,
      '删除确认',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch (e) {
    return
  }
  try {
    await deleteDepartment(row.id)
    ElMessage.success('删除成功')
    loadTree()
  } catch (err) {
    // 3002 存在子部门 / 3003 部门下存在员工，已由拦截器提示
  }
}
</script>

<style scoped lang="scss">
.department-page {
  .sort-tip {
    margin-left: 10px;
    font-size: 12px;
    color: #909399;
  }
}
</style>
