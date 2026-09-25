<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getEmployees } from '@/api/employee.js'
import { NOTIFICATION_TYPE } from '@kdyzgl/shared/constants/dict'
import { publishNotification } from '../../../api/notification.js'
import StateBlock from '../../../components/StateBlock.vue'

/**
 * 发布通知抽屉（需求4，B4.2）
 *
 * 三种发布范围的收件人一律是「在职且启用」员工（与 notification.js:83 口径一致），
 * 因此人数预览必须由同一份员工数据算出，不能「界面上写 56 人、实际发出 54 人」。
 * 二次确认阈值 20 人：小范围通知每次确认会变成高频骚扰，大范围群发才需要拦一道（B4.4）。
 */
const CONFIRM_THRESHOLD = 20

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  stations: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue', 'published'])

const formRef = ref(null)
const submitting = ref(false)
const loading = ref(false)
const loadError = ref(false)
const employees = ref([])
const employeeTotal = ref(0)
const stationCount = ref(null)

const form = reactive({ type: 4, title: '', content: '', scope: 'ALL', stationId: null, employeeIds: [] })

const RULES = {
  title: [
    { required: true, message: '请填写通知标题', trigger: 'blur' },
    { min: 1, max: 100, message: '标题长度须为 1-100 字符', trigger: 'blur' }
  ],
  content: [
    { required: true, message: '请填写通知正文', trigger: 'blur' },
    { min: 1, max: 500, message: '正文长度须为 1-500 字符', trigger: 'blur' }
  ]
}

const selectedEmployees = computed(() => employees.value.filter((item) => form.employeeIds.includes(item.id)))

/** 收件人名单（仅用于预览前 3 名；实际收件人以服务端返回的 count 为准） */
const recipientList = computed(() => {
  if (form.scope === 'ALL') return employees.value
  if (form.scope === 'STATION') return employees.value.filter((item) => item.stationId === form.stationId)
  return selectedEmployees.value
})

const recipientCount = computed(() => {
  if (form.scope === 'ALL') return employeeTotal.value
  if (form.scope === 'STATION') return stationCount.value == null ? 0 : stationCount.value
  return form.employeeIds.length
})

const recipientPreview = computed(() => {
  if (form.scope === 'STATION' && form.stationId == null) return '请先选择驿站'
  if (!recipientCount.value) return '当前范围内没有在职员工'
  const names = recipientList.value.slice(0, 3).map((item) => item.realName)
  const suffix = recipientCount.value > names.length ? ` 等共 ${recipientCount.value} 人` : ''
  return `接收人预览：${names.join('、')}${suffix}`
})

/** 禁用原因写进 title：让用户知道缺的是哪一项，而不是对着灰按钮猜（B4.3） */
const disabledReason = computed(() => {
  if (!form.title.trim()) return '请先填写通知标题'
  if (!form.content.trim()) return '请先填写通知正文'
  if (form.scope === 'STATION' && form.stationId == null) return '请选择要发布到的驿站'
  if (form.scope === 'EMPLOYEE' && !form.employeeIds.length) return '请至少选择一名在职员工'
  if (!recipientCount.value) return '当前范围内没有在职员工，无法发布'
  return ''
})

async function loadEmployees() {
  loading.value = true
  loadError.value = false
  try {
    // pageSize 取契约上限 100：Demo 在职员工约 60 人，一次取全即可支撑多选与预览
    const page = await getEmployees({ status: 1, pageNum: 1, pageSize: 100 })
    employees.value = page.list
    employeeTotal.value = page.total
  } catch (e) {
    employees.value = []
    employeeTotal.value = 0
    loadError.value = true
  } finally {
    loading.value = false
  }
}

/** 指定驿站的人数用单次 pageSize=1 请求取 total，避免本地按截断后的名单误算 */
async function loadStationCount(stationId) {
  stationCount.value = null
  if (stationId == null) return
  try {
    const page = await getEmployees({ stationId, status: 1, pageNum: 1, pageSize: 1 })
    stationCount.value = page.total
  } catch (e) {
    stationCount.value = 0
  }
}

function handleScopeChange() {
  if (form.scope === 'STATION') loadStationCount(form.stationId)
}

function handleStationChange() {
  loadStationCount(form.stationId)
}

async function handlePublish() {
  const valid = await (formRef.value ? formRef.value.validate().catch(() => false) : true)
  if (!valid) return
  if (disabledReason.value) {
    ElMessage.warning(disabledReason.value)
    return
  }
  // 大范围群发不可撤回，发布前二次确认（B4.4）
  if (recipientCount.value > CONFIRM_THRESHOLD) {
    try {
      await ElMessageBox.confirm(
        `本次将发布给 ${recipientCount.value} 名员工，发布后不可撤回。确认发布？`,
        '发布通知',
        {
          confirmButtonText: '确认发布',
          cancelButtonText: '再想想',
          type: 'warning'
        }
      )
    } catch (e) {
      return
    }
  }

  submitting.value = true
  try {
    const result = await publishNotification(
      {
        type: form.type,
        title: form.title.trim(),
        content: form.content.trim(),
        scope: form.scope,
        stationId: form.scope === 'STATION' ? form.stationId : undefined,
        employeeIds: form.scope === 'EMPLOYEE' ? form.employeeIds : undefined
      },
      { silent: true }
    )
    ElMessage.success(`已发布给 ${result.count} 名员工`)
    emit('published', result)
    close()
  } catch (e) {
    ElMessage.error((e && e.message) || '通知发布失败，请重试')
  } finally {
    submitting.value = false
  }
}

function reset() {
  form.type = 4
  form.title = ''
  form.content = ''
  form.scope = 'ALL'
  form.stationId = null
  form.employeeIds = []
  stationCount.value = null
}

function close() {
  emit('update:modelValue', false)
}

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return
    reset()
    loadEmployees()
  }
)
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    title="发布通知"
    size="min(560px, 92vw)"
    destroy-on-close
    @update:model-value="close"
  >
    <div v-loading="loading" class="drawer-body">
      <StateBlock
        v-if="loadError"
        variant="error"
        title="员工名册加载失败"
        description="发布范围需要名册才能计算收件人"
        @action="loadEmployees"
      />

      <el-form v-else ref="formRef" :model="form" :rules="RULES" label-width="90px" :disabled="submitting">
        <el-form-item label="通知类型">
          <el-radio-group v-model="form.type">
            <el-radio v-for="(item, key) in NOTIFICATION_TYPE" :key="key" :value="Number(key)">{{
              item.label
            }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model.trim="form.title" maxlength="100" show-word-limit placeholder="一句话说明通知事项" />
        </el-form-item>
        <el-form-item label="正文" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="5"
            maxlength="500"
            show-word-limit
            placeholder="通知内容（≤500 字）"
          />
        </el-form-item>
        <el-form-item label="发布范围">
          <el-radio-group v-model="form.scope" @change="handleScopeChange">
            <el-radio value="ALL">全员</el-radio>
            <el-radio value="STATION">指定驿站</el-radio>
            <el-radio value="EMPLOYEE">指定员工</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.scope === 'STATION'" label="选择驿站">
          <el-select v-model="form.stationId" class="form-field" placeholder="请选择驿站" @change="handleStationChange">
            <el-option v-for="item in stations" :key="item.id" :label="item.stationName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-else-if="form.scope === 'EMPLOYEE'" label="选择员工">
          <el-select
            v-model="form.employeeIds"
            class="form-field"
            multiple
            filterable
            collapse-tags
            placeholder="请选择在职员工"
          >
            <el-option
              v-for="item in employees"
              :key="item.id"
              :label="`${item.realName}（${item.stationName || '总部'}）`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <!-- 收件人预览：人数随范围实时变化，避免「以为发了 12 人、实际发了全站」（B4.2 ③/④） -->
      <div class="recipient" aria-live="polite">
        <span class="recipient__count">将发送给 {{ recipientCount }} 名在职员工</span>
        <span class="recipient__names">{{ recipientPreview }}</span>
      </div>
    </div>

    <template #footer>
      <div class="drawer-footer">
        <el-button @click="close">取消</el-button>
        <el-button
          type="primary"
          :loading="submitting"
          :disabled="!!disabledReason"
          :title="disabledReason || '发布后不可撤回'"
          @click="handlePublish"
        >
          发布
        </el-button>
      </div>
    </template>
  </el-drawer>
</template>

<style scoped lang="scss">
.drawer-body {
  min-height: 200px;
}

.form-field {
  width: 100%;
}

.recipient {
  display: flex;
  flex-direction: column;
  gap: var(--sp-1);
  margin-top: var(--sp-4);
  padding: var(--sp-3);
  border: 1px solid var(--state-primary-border);
  border-radius: var(--r-sm);
  background-color: var(--state-primary-bg);

  &__count {
    font-size: var(--fs-body-strong);
    font-weight: var(--fw-medium);
    color: var(--text-1);
    font-variant-numeric: tabular-nums;
  }

  &__names {
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    color: var(--text-3);
  }
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--sp-2);
}
</style>
