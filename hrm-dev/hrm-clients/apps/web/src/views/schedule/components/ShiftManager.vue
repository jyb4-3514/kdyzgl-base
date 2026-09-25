<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { STATION_STATUS } from '@kdyzgl/shared/constants/dict'
import { createShift, deleteShift, getShifts, updateShift } from '../../../api/attendance.js'
import StateBlock from '../../../components/StateBlock.vue'
import StatusTag from '../../../components/StatusTag.vue'

/**
 * 班次管理（排班页右下半区）
 *
 * 为什么独立成组件：班次是排班的时间基准（打卡判定的迟到/早退都按班次时间算），
 * 有自己完整的一套增删改与校验，与「周排班表」只是引用关系，拆开后两边互不干扰。
 *
 * 校验口径对齐 shared/mock/routes/attendance.js 的 validateShift（名称 1-20、HH:mm、结束晚于开始、#RRGGBB）。
 */
const props = defineProps({
  stationId: { type: Number, default: null },
  stationName: { type: String, default: '' },
  // 班次写操作在服务端仅放行 ADMIN，非 ADMIN 渲染为只读列表
  canWrite: { type: Boolean, default: false }
})

const emit = defineEmits(['changed'])

/**
 * 班次配色只允许设计 Token 色板（demo-ui-redesign.md 2.2 / 2.3）：
 * 早班取品牌蓝 blue-700、中班取物流橙 orange-500、晚班取中性深灰 neutral-800。
 * 旧契约给的紫色 #722ED1 不在 Token 色板内，已废弃，这里不提供。
 * value 是接口要的 #RRGGBB 字面量，token 是渲染色块用的 CSS 变量，两者成对维护，模板里不写死色值。
 */
const SHIFT_COLORS = [
  { value: '#0958D9', token: '--c-blue-700', label: '早班蓝' },
  { value: '#FA8C16', token: '--c-orange-500', label: '中班橙' },
  { value: '#1F2937', token: '--c-neutral-800', label: '晚班深灰' }
]

const loading = ref(false)
const saving = ref(false)
const error = ref(false)
const list = ref([])

const dialog = reactive({ visible: false, mode: 'create', id: null })
const formRef = ref(null)
const form = reactive({
  shiftName: '',
  startTime: '08:00',
  endTime: '16:00',
  color: SHIFT_COLORS[0].value,
  restMinutes: 60,
  status: 1
})

const dialogTitle = computed(() => (dialog.mode === 'create' ? '新增班次' : '编辑班次'))

/**
 * 与 attendanceStore.minutesOfDay 同口径（'24:00' 按 1440 计）
 * 4 行纯函数不值得让 UI 反向依赖 mock 层，故就地实现并在校验里成对使用
 */
function minutesOfDay(text) {
  const [h, m] = String(text || '')
    .split(':')
    .map(Number)
  if (!Number.isFinite(h) || !Number.isFinite(m)) return NaN
  return h * 60 + m
}

const formRules = {
  shiftName: [
    { required: true, message: '请输入班次名称', trigger: 'blur' },
    { min: 1, max: 20, message: '班次名称长度须为 1-20', trigger: 'blur' }
  ],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [
    { required: true, message: '请选择结束时间', trigger: 'change' },
    {
      validator: (_rule, value, callback) => {
        if (!value) return callback()
        if (minutesOfDay(form.startTime) >= minutesOfDay(value)) return callback(new Error('结束时间须晚于开始时间'))
        return callback()
      },
      trigger: 'change'
    }
  ],
  restMinutes: [
    {
      validator: (_rule, value, callback) =>
        value !== null && value !== '' && Number(value) >= 0 ? callback() : callback(new Error('休息时长须不小于 0')),
      trigger: 'blur'
    }
  ]
}

async function fetchList() {
  if (props.stationId == null) return
  loading.value = true
  error.value = false
  try {
    list.value = await getShifts(props.stationId)
  } catch (e) {
    list.value = []
    error.value = true
  } finally {
    loading.value = false
  }
}

function openCreate() {
  dialog.mode = 'create'
  dialog.id = null
  Object.assign(form, {
    shiftName: '',
    startTime: '08:00',
    endTime: '16:00',
    color: SHIFT_COLORS[0].value,
    restMinutes: 60,
    status: 1
  })
  dialog.visible = true
  if (formRef.value) formRef.value.clearValidate()
}

function openEdit(row) {
  dialog.mode = 'edit'
  dialog.id = row.id
  Object.assign(form, {
    shiftName: row.shiftName,
    startTime: row.startTime,
    endTime: row.endTime,
    color: row.color,
    restMinutes: row.restMinutes,
    status: row.status
  })
  dialog.visible = true
  if (formRef.value) formRef.value.clearValidate()
}

async function submit() {
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  saving.value = true
  const payload = { ...form }
  try {
    if (dialog.mode === 'create') await createShift({ ...payload, stationId: props.stationId })
    else await updateShift(dialog.id, payload)
    ElMessage.success(dialog.mode === 'create' ? '班次已新增' : '班次已更新')
    dialog.visible = false
    await fetchList()
    // 通知父级重拉排班矩阵：班次时间/配色/状态变了，表里的下拉与图例都要跟着变
    emit('changed')
  } catch (e) {
    /* 400 字段校验由拦截器按服务端文案提示（如「结束时间须晚于开始时间」），不覆盖为通用文案 */
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除班次「${row.shiftName}」？已被排班引用的班次不允许删除。`, '删除班次', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  try {
    await deleteShift(row.id)
    ElMessage.success('班次已删除')
    await fetchList()
    emit('changed')
  } catch (e) {
    /* 400「该班次已被排班引用，不能删除」/ 404 由拦截器原样提示 */
  }
}

watch(
  () => props.stationId,
  () => fetchList(),
  { immediate: true }
)
</script>

<template>
  <el-card shadow="never" class="shift-card">
    <template #header>
      <div class="shift-card__head">
        <div>
          <span class="shift-card__title">班次管理</span>
          <span v-if="stationName" class="shift-card__station">{{ stationName }}</span>
          <span class="shift-card__count">共 {{ list.length }} 个班次</span>
        </div>
        <el-button v-if="canWrite" type="primary" :icon="Plus" @click="openCreate">新增班次</el-button>
      </div>
    </template>

    <StateBlock v-if="error" variant="error" title="班次列表加载失败" @action="fetchList" />

    <StateBlock
      v-else-if="!loading && !list.length"
      variant="empty"
      title="该驿站暂无班次"
      description="新增班次后即可用于排班"
    />

    <template v-else>
      <el-table v-loading="loading" :data="list" border>
        <el-table-column label="班次名称" min-width="130">
          <template #default="{ row }">
            <span class="shift-name">
              <!-- 色块取接口返回的配色（受控于服务端校验过的 #RRGGBB），图例与单元格共用同一份值 -->
              <i class="shift-name__dot" :style="{ backgroundColor: row.color }" aria-hidden="true" />
              {{ row.shiftName }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="时间段" width="150">
          <template #default="{ row }">{{ row.startTime }} - {{ row.endTime }}</template>
        </el-table-column>
        <el-table-column label="休息时长" width="100" align="right">
          <template #default="{ row }">{{ row.restMinutes }} 分钟</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <StatusTag :dict="STATION_STATUS" :value="row.status" :variant="row.status === 1 ? 'soft' : 'outline'" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" align="center">
          <template #default="{ row }">
            <template v-if="canWrite">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
            </template>
            <span v-else class="shift-card__readonly">只读</span>
          </template>
        </el-table-column>
      </el-table>

      <p v-if="!canWrite" class="shift-card__tip">只读视角：班次新增/编辑/删除仅超级管理员可执行</p>
    </template>

    <el-dialog v-model="dialog.visible" :title="dialogTitle" width="480px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="88px">
        <el-form-item label="班次名称" prop="shiftName">
          <el-input v-model.trim="form.shiftName" maxlength="20" show-word-limit placeholder="如：早班" />
        </el-form-item>
        <el-form-item label="开始时间" prop="startTime">
          <el-time-select
            v-model="form.startTime"
            start="00:00"
            end="23:30"
            step="00:30"
            placeholder="开始时间"
            class="field-time"
          />
        </el-form-item>
        <el-form-item label="结束时间" prop="endTime">
          <el-time-select
            v-model="form.endTime"
            start="00:00"
            end="24:00"
            step="00:30"
            include-end-time
            placeholder="结束时间"
            class="field-time"
          />
        </el-form-item>
        <el-form-item label="班次配色">
          <el-radio-group v-model="form.color">
            <el-radio-button v-for="item in SHIFT_COLORS" :key="item.value" :value="item.value">
              <span class="color-option">
                <i class="color-option__dot" :style="{ backgroundColor: `var(${item.token})` }" aria-hidden="true" />
                {{ item.label }}
              </span>
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="休息时长">
          <el-input-number v-model="form.restMinutes" :min="0" :max="480" :step="15" />
          <span class="dialog-hint">分钟</span>
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
          <span class="dialog-hint">停用后该班次不能再被排班引用</span>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">确定</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<style scoped lang="scss">
.shift-card {
  margin-bottom: 0;

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--sp-4);
  }

  &__title {
    font-size: var(--fs-h3);
    font-weight: var(--fw-semibold);
    color: var(--text-1);
  }

  &__station,
  &__count {
    margin-left: var(--sp-2);
    font-size: var(--fs-caption);
    color: var(--text-3);
  }

  &__readonly {
    font-size: var(--fs-caption);
    color: var(--text-3);
  }

  &__tip {
    margin: var(--sp-3) 0 0;
    font-size: var(--fs-caption);
    color: var(--text-3);
  }
}

.shift-name {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-2);

  &__dot {
    width: 10px;
    height: 10px;
    border-radius: var(--r-xs);
    flex-shrink: 0;
  }
}

.color-option {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-1);

  &__dot {
    width: 10px;
    height: 10px;
    border-radius: var(--r-xs);
  }
}

.dialog-hint {
  margin-left: var(--sp-2);
  font-size: var(--fs-caption);
  color: var(--text-3);
}

.field-time {
  width: 140px;
}
</style>
