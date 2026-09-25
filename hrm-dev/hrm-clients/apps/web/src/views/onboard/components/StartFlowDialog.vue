<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getEmployees } from '@/api/employee.js'
import { createOffboarding, createOnboarding } from '../../../api/hr.js'
import { EDUCATION, OFFBOARDING_TYPE } from '@kdyzgl/shared/constants/dict.js'

/**
 * 发起入离职流程弹窗（B10.4）
 * 两类流程字段完全不同，故用同一个组件按 type 分支，避免两份 90% 重复的表单。
 * 校验口径与 hr.js 路由逐条对齐，能在本地判掉的错误不留给服务端。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  type: { type: String, default: 'ONBOARDING' },
  stations: { type: Array, default: () => [] },
  departments: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue', 'created'])

const PHONE_RE = /^1[3-9]\d{9}$/

const isOnboarding = computed(() => props.type === 'ONBOARDING')
const employees = ref([])
const submitting = ref(false)

const onboardForm = ref({})
const offboardForm = ref({})

function reset() {
  const today = new Date()
  const localDate = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
  onboardForm.value = {
    candidateName: '',
    phone: '',
    gender: 1,
    education: 'BACHELOR',
    position: '',
    deptId: undefined,
    stationId: undefined,
    expectedEntryDate: localDate,
    remark: ''
  }
  offboardForm.value = { employeeId: undefined, type: 'RESIGN', reason: '', lastWorkDate: localDate }
}

watch(
  () => props.modelValue,
  async (visible) => {
    if (!visible) return
    reset()
    if (!isOnboarding.value && !employees.value.length) {
      const page = await getEmployees({ pageNum: 1, pageSize: 100 }).catch(() => ({ list: [] }))
      employees.value = (page.list || []).map((item) => ({
        id: item.id,
        realName: item.realName,
        stationName: item.stationName
      }))
    }
  },
  { immediate: true }
)

const errorText = computed(() => {
  if (isOnboarding.value) {
    const form = onboardForm.value
    const name = String(form.candidateName || '').trim()
    if (name.length < 2 || name.length > 20) return '候选人姓名须为 2-20 字'
    if (!PHONE_RE.test(String(form.phone || ''))) return '手机号格式不正确'
    if (!form.stationId) return '请选择归属驿站'
    if (form.remark && form.remark.length > 200) return '备注不可超过 200 字'
    return ''
  }
  const form = offboardForm.value
  if (!form.employeeId) return '请选择离职员工'
  const reason = String(form.reason || '').trim()
  if (reason.length < 2 || reason.length > 200) return '离职原因须为 2-200 字'
  if (!form.lastWorkDate) return '请选择最后工作日'
  return ''
})

const canSubmit = computed(() => !errorText.value)

async function handleSubmit() {
  if (!canSubmit.value) {
    ElMessage.warning(errorText.value)
    return
  }
  submitting.value = true
  try {
    if (isOnboarding.value) {
      const form = onboardForm.value
      await createOnboarding({
        candidateName: String(form.candidateName).trim(),
        phone: String(form.phone).trim(),
        gender: Number(form.gender),
        education: form.education,
        position: form.position || undefined,
        deptId: form.deptId || undefined,
        stationId: form.stationId || undefined,
        expectedEntryDate: form.expectedEntryDate || undefined,
        remark: form.remark || undefined
      })
    } else {
      const form = offboardForm.value
      await createOffboarding({
        employeeId: form.employeeId,
        type: form.type,
        reason: String(form.reason).trim(),
        lastWorkDate: form.lastWorkDate
      })
    }
    ElMessage.success(isOnboarding.value ? '入职流程已发起' : '离职流程已发起')
    emit('created')
    emit('update:modelValue', false)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="isOnboarding ? '发起入职流程' : '发起离职流程'"
    width="560px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form label-position="top">
      <template v-if="isOnboarding">
        <div class="start-flow__grid">
          <el-form-item label="候选人姓名" required>
            <el-input v-model="onboardForm.candidateName" maxlength="20" />
          </el-form-item>
          <el-form-item label="手机号" required>
            <el-input v-model="onboardForm.phone" maxlength="11" placeholder="11 位手机号" />
          </el-form-item>
          <el-form-item label="性别">
            <el-select v-model="onboardForm.gender" style="width: 100%">
              <el-option :value="1" label="男" />
              <el-option :value="2" label="女" />
              <el-option :value="0" label="未填写" />
            </el-select>
          </el-form-item>
          <el-form-item label="学历">
            <el-select v-model="onboardForm.education" style="width: 100%">
              <el-option v-for="(dict, key) in EDUCATION" :key="key" :value="key" :label="dict.label" />
            </el-select>
          </el-form-item>
          <el-form-item label="拟任岗位">
            <el-input v-model="onboardForm.position" maxlength="20" placeholder="如：快递员" />
          </el-form-item>
          <el-form-item label="期望入职日期">
            <el-date-picker
              v-model="onboardForm.expectedEntryDate"
              type="date"
              value-format="YYYY-MM-DD"
              :clearable="false"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="归属驿站" required>
            <el-select v-model="onboardForm.stationId" style="width: 100%" placeholder="选择驿站">
              <el-option v-for="item in stations" :key="item.id" :value="item.id" :label="item.stationName" />
            </el-select>
          </el-form-item>
          <el-form-item label="归属部门">
            <el-select v-model="onboardForm.deptId" clearable style="width: 100%" placeholder="选择部门">
              <el-option v-for="item in departments" :key="item.id" :value="item.id" :label="item.deptName" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="备注">
          <el-input v-model="onboardForm.remark" maxlength="200" show-word-limit />
        </el-form-item>
      </template>

      <template v-else>
        <div class="start-flow__grid">
          <el-form-item label="离职员工" required>
            <el-select v-model="offboardForm.employeeId" filterable style="width: 100%" placeholder="选择在职员工">
              <el-option
                v-for="item in employees"
                :key="item.id"
                :value="item.id"
                :label="`${item.realName}（${item.stationName || '—'}）`"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="离职类型" required>
            <el-select v-model="offboardForm.type" style="width: 100%">
              <el-option v-for="(dict, key) in OFFBOARDING_TYPE" :key="key" :value="key" :label="dict.label" />
            </el-select>
          </el-form-item>
          <el-form-item label="最后工作日" required>
            <el-date-picker
              v-model="offboardForm.lastWorkDate"
              type="date"
              value-format="YYYY-MM-DD"
              :clearable="false"
              style="width: 100%"
            />
          </el-form-item>
        </div>
        <el-form-item label="离职原因" required>
          <el-input v-model="offboardForm.reason" type="textarea" :rows="3" maxlength="200" show-word-limit />
        </el-form-item>
      </template>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">再想想</el-button>
      <el-button type="primary" :loading="submitting" :disabled="!canSubmit" :title="errorText" @click="handleSubmit"
        >确认发起</el-button
      >
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.start-flow {
  &__grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 0 var(--sp-3);
  }
}
</style>
