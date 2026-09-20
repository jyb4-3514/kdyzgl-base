<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getEmployees } from '@admin/api/employee'
import { WORK_ORDER_PRIORITY, WORK_ORDER_SLA_HOURS, WORK_ORDER_TYPE } from '@/shared/constants/dict'
import { formatDateTime } from '@/shared/domain/time.js'
import { getSchedules } from '../../../api/attendance.js'
import { createWorkOrder } from '../../../api/workOrder.js'

/**
 * 新建工单弹窗（需求3，B3.2）
 *
 * 用弹窗而不是抽屉：字段 7 个、无嵌套结构，抽屉 520px 宽度里会空一半；且新建是「一次性提交」而非「边看边改」。
 * SLA 预览口径直接取 WORK_ORDER_SLA_HOURS，禁止前端另写一份时长表（否则与服务端建单口径漂移）。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  isAdmin: { type: Boolean, default: false },
  // ADMIN 可选驿站列表；非 ADMIN 归属驿站由登录态决定，只读展示
  stations: { type: Array, default: () => [] },
  defaultStationId: { type: [Number, String], default: null },
  fixedStationName: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue', 'created'])

const formRef = ref(null)
const submitting = ref(false)
const assignees = ref([])
const assigneesLoading = ref(false)

const form = reactive({
  stationId: null,
  type: null,
  priority: 1,
  title: '',
  content: '',
  waybillNo: '',
  assigneeId: null
})

const RULES = {
  stationId: [{ required: true, message: '请选择归属驿站', trigger: 'change' }],
  type: [{ required: true, message: '请选择工单类型', trigger: 'change' }],
  priority: [{ required: true, message: '请选择优先级', trigger: 'change' }],
  title: [
    { required: true, message: '请填写工单标题', trigger: 'blur' },
    { min: 1, max: 100, message: '标题长度须为 1-100 字符', trigger: 'blur' }
  ]
}

/** SLA 截止预览：随优先级实时变化，与 Mock 建单时的 sla_deadline 计算同口径 */
const slaPreview = computed(() => {
  const hours = WORK_ORDER_SLA_HOURS[form.priority]
  if (!hours) return '—'
  return `${formatDateTime(new Date(Date.now() + hours * 3600000))}（${WORK_ORDER_PRIORITY[form.priority].label}优先级 ${hours} 小时）`
})

async function loadAssignees() {
  const stationId = props.isAdmin ? form.stationId : props.defaultStationId
  assignees.value = []
  if (stationId == null) return
  assigneesLoading.value = true
  try {
    if (props.isAdmin) {
      const page = await getEmployees({ stationId, status: 1, pageNum: 1, pageSize: 100 })
      assignees.value = page.list.map((item) => ({ id: item.id, label: item.realName, stationId: item.stationId }))
    } else {
      // 非 ADMIN 拿不到 /employees（专属接口），复用排班矩阵的本站名册 —— 与转单候选同口径
      const matrix = await getSchedules({ stationId })
      assignees.value = matrix.employees.map((item) => ({ id: item.employeeId, label: item.employeeName, stationId }))
    }
  } catch (e) {
    /* 名册加载失败不阻塞建单：处理人本就可跳过 */
  } finally {
    assigneesLoading.value = false
  }
}

function resetForm() {
  // 默认驿站由页面给出（ADMIN 取当前筛选值，站长即本站），此处不做角色分支
  form.stationId = props.defaultStationId
  form.type = null
  form.priority = 1
  form.title = ''
  form.content = ''
  form.waybillNo = ''
  form.assigneeId = null
  loadAssignees()
}

/** 驿站切换后原处理人可能不属于新驿站，必须清空，否则会提交一个必然被 8004 拒绝的指派 */
function handleStationChange() {
  form.assigneeId = null
  loadAssignees()
}

async function handleSubmit() {
  const valid = await (formRef.value ? formRef.value.validate().catch(() => false) : true)
  if (!valid) return

  const assignee = assignees.value.find((item) => item.id === form.assigneeId)
  // 跨站指派只有老板可以，先确认再提交（B3.2 二次确认点）
  if (assignee && Number(assignee.stationId) !== Number(form.stationId)) {
    try {
      await ElMessageBox.confirm(`将工单指派给其他驿站的「${assignee.label}」，确认？`, '跨驿站指派', {
        confirmButtonText: '确认指派',
        cancelButtonText: '再想想',
        type: 'warning'
      })
    } catch (e) {
      return
    }
  }

  submitting.value = true
  try {
    const result = await createWorkOrder({
      stationId: form.stationId,
      type: form.type,
      priority: form.priority,
      title: form.title.trim(),
      content: form.content.trim(),
      waybillNo: form.waybillNo.trim() || undefined,
      assigneeId: form.assigneeId || undefined
    })
    ElMessage.success(`工单 ${result.orderNo} 已创建`)
    emit('created', result)
    close()
  } catch (e) {
    ElMessage.error((e && e.message) || '工单创建失败，请重试')
  } finally {
    submitting.value = false
  }
}

function close() {
  emit('update:modelValue', false)
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) resetForm()
  }
)
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="新建工单"
    width="560px"
    :close-on-click-modal="false"
    @update:model-value="close"
  >
    <el-form ref="formRef" :model="form" :rules="RULES" label-width="90px" :disabled="submitting">
      <el-form-item label="归属驿站" prop="stationId">
        <el-select
          v-if="isAdmin"
          v-model="form.stationId"
          class="form-field"
          placeholder="请选择归属驿站"
          @change="handleStationChange"
        >
          <el-option v-for="item in stations" :key="item.id" :label="item.stationName" :value="item.id" />
        </el-select>
        <span v-else class="form-static">{{ fixedStationName || '—' }}</span>
      </el-form-item>
      <el-form-item label="工单类型" prop="type">
        <el-select v-model="form.type" class="form-field" placeholder="请选择工单类型">
          <el-option v-for="(item, key) in WORK_ORDER_TYPE" :key="key" :label="item.label" :value="Number(key)" />
        </el-select>
      </el-form-item>
      <el-form-item label="优先级" prop="priority">
        <el-select v-model="form.priority" class="form-field">
          <el-option v-for="(item, key) in WORK_ORDER_PRIORITY" :key="key" :label="item.label" :value="Number(key)" />
        </el-select>
      </el-form-item>
      <el-form-item label="标题" prop="title">
        <el-input v-model.trim="form.title" maxlength="100" show-word-limit placeholder="一句话描述问题" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input
          v-model="form.content"
          type="textarea"
          :rows="4"
          maxlength="500"
          show-word-limit
          placeholder="补充现场情况、涉及包裹等（选填，≤500 字）"
        />
      </el-form-item>
      <el-form-item label="关联运单号">
        <el-input v-model.trim="form.waybillNo" placeholder="选填，需精确匹配运单号" />
      </el-form-item>
      <el-form-item label="指派处理人">
        <el-select
          v-model="form.assigneeId"
          class="form-field"
          filterable
          clearable
          :loading="assigneesLoading"
          placeholder="选填，默认不指派"
        >
          <el-option v-for="item in assignees" :key="item.id" :label="item.label" :value="item.id" />
        </el-select>
      </el-form-item>
    </el-form>

    <!-- SLA 预览只读回显：让建单人当场看到这条工单的时限承诺 -->
    <p class="sla-preview">SLA 截止：{{ slaPreview }}</p>

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">创建工单</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.form-field {
  width: 100%;
}

.form-static {
  color: var(--text-1);
}

.sla-preview {
  margin: 0;
  padding: var(--sp-2) var(--sp-3);
  border: 1px solid var(--state-primary-border);
  border-radius: var(--r-sm);
  background-color: var(--state-primary-bg);
  color: var(--text-2);
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  font-variant-numeric: tabular-nums;
}
</style>
