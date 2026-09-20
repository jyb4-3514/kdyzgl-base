<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ATTENDANCE_CODE } from '@/shared/constants/errorCode'
import { addDays, formatDate } from '@/shared/domain/time.js'
import { getSchedules, saveSchedulesByStation } from '../../../api/attendance.js'
import StateBlock from '../../../components/StateBlock.vue'

/**
 * 排班批量工具（需求2，B2.2 四个能力）
 *
 * 一个组件承载四种模式，而不是四个弹窗：四者的「预览命中格数 + 危险项二次确认 + 应用」骨架完全一致，
 * 拆成四个组件会把同一套预览与确认逻辑复制四遍。
 *
 * 两种落地路径（按契约可行性区分）：
 * - spread（一键铺排）：契约 POST /schedules/batch-by-station 已就绪，直接写服务端，成功回列表重载；
 * - copy / batch / clear：无对应契约，走「本地 dirty + 页面手动保存」，复用既有 /schedules/batch（B2.5 已说明理由：
 *   复制结果几乎一定需要微调，直接落库会让用户改完再存第二次）。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  mode: { type: String, default: 'spread' }, // spread | copy | batch | clear
  stationId: { type: [Number, String], default: null },
  stationName: { type: String, default: '' },
  weekStart: { type: String, default: '' },
  // 当前周日期（周一 → 周日）与矩阵行，用于本地预览与 dirty 生成
  dates: { type: Array, default: () => [] },
  rows: { type: Array, default: () => [] },
  shifts: { type: Array, default: () => [] },
  // 行首 / 列头锚点带入的预置目标：{ direction, employeeId, workDate }
  seed: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'spread-done', 'apply'])

const MODE_TITLE = { spread: '一键铺排', copy: '复制上一周', batch: '整行 / 整列批量设置', clear: '清空排班' }

/** 星期选项按 JS getDay 取值（0=周日 … 6=周六），与契约 weekdays 参数同口径 */
const WEEKDAY_OPTIONS = [
  { value: 1, label: '周一' },
  { value: 2, label: '周二' },
  { value: 3, label: '周三' },
  { value: 4, label: '周四' },
  { value: 5, label: '周五' },
  { value: 6, label: '周六' },
  { value: 0, label: '周日' }
]

const saving = ref(false)
const copyLoading = ref(false)
const copyError = ref(false)
const prevMatrix = ref(null)

const spreadForm = reactive({ shiftId: null, range: [], weekdays: [], employeeIds: [], skipExisting: true })
const batchForm = reactive({ direction: 'row', employeeId: null, workDate: null, shiftId: null })
const clearForm = reactive({ scope: 'week', workDate: null })

/** 'YYYY-MM-DD' → 本地 0 点：new Date('YYYY-MM-DD') 按 UTC 解析，跨时区会算错一天 */
function localDate(text) {
  const [y, m, d] = String(text).split('-').map(Number)
  return new Date(y, m - 1, d)
}

const enabledShifts = computed(() => props.shifts.filter((item) => item.status === 1))

/** 当前周各单元格的班次：key = employeeId_workDate */
const currentMap = computed(() => {
  const map = new Map()
  props.rows.forEach((row) =>
    row.days.forEach((day) => map.set(`${row.employeeId}_${day.workDate}`, day.shiftId ?? null))
  )
  return map
})

const currentOf = (employeeId, workDate) => currentMap.value.get(`${employeeId}_${workDate}`) ?? null

/** 铺排日期区间：客户端按天展开，加 366 天上限防超长区间把浏览器卡住 */
const spreadDates = computed(() => {
  const [start, end] = spreadForm.range || []
  if (!start || !end || start > end) return []
  const list = []
  let cursor = localDate(start)
  const last = localDate(end)
  for (let i = 0; i < 366 && cursor <= last; i += 1) {
    list.push(formatDate(cursor))
    cursor = addDays(cursor, 1)
  }
  return list
})

const spreadTargetDates = computed(() => {
  if (!spreadForm.weekdays.length) return spreadDates.value
  return spreadDates.value.filter((date) => spreadForm.weekdays.includes(localDate(date).getDay()))
})

const spreadEmployeeIds = computed(() =>
  spreadForm.employeeIds.length ? spreadForm.employeeIds : props.rows.map((row) => row.employeeId)
)

/** 预览只对「当前周矩阵内」的格子能算准；超出当前周的日期无从知晓既有排班，故显式提示 */
const spreadBeyondWeek = computed(() => {
  if (!props.dates.length) return false
  const first = props.dates[0]
  const last = props.dates[props.dates.length - 1]
  return spreadDates.value.some((date) => date < first || date > last)
})

const spreadPreview = computed(() => {
  let write = 0
  let skip = 0
  spreadEmployeeIds.value.forEach((employeeId) => {
    spreadTargetDates.value.forEach((workDate) => {
      const existing = currentOf(employeeId, workDate)
      if (existing != null && spreadForm.skipExisting) skip += 1
      else write += 1
    })
  })
  return { write, skip }
})

/** 上一周矩阵 → Map，用于复制预览 */
const prevMap = computed(() => {
  const map = new Map()
  if (!prevMatrix.value) return map
  prevMatrix.value.employees.forEach((item) => {
    const days = new Map()
    item.days.forEach((day) => days.set(day.workDate, day.shiftId ?? null))
    map.set(item.employeeId, days)
  })
  return map
})

/**
 * 复制预览：只比对「上一周名册里存在」的员工，否则会为名册外员工算出大量虚假的「清空」。
 * 新增 = 上周边有值且本周为空；覆盖 = 两周都有值且不同；清空 = 上周边为空但本周有值。
 */
const copyPreview = computed(() => {
  const result = { add: 0, overwrite: 0, clear: 0, changes: [] }
  prevMap.value.forEach((days, employeeId) => {
    days.forEach((prevShiftId, workDate) => {
      const current = currentOf(employeeId, workDate)
      if (prevShiftId === current) return
      if (prevShiftId != null && current == null) result.add += 1
      else if (prevShiftId != null && current != null) result.overwrite += 1
      else result.clear += 1
      result.changes.push({ employeeId, workDate, shiftId: prevShiftId })
    })
  })
  return result
})

/** 上一周是否整周无排班：决定「无可复制内容」空态与确认按钮可用性 */
const prevEmpty = computed(
  () => prevMap.value.size === 0 || copyPreview.value.add + copyPreview.value.overwrite + copyPreview.value.clear === 0
)

const batchChanges = computed(() => {
  const changes = []
  if (batchForm.direction === 'row') {
    const row = props.rows.find((item) => item.employeeId === batchForm.employeeId)
    if (!row) return changes
    row.days.forEach((day) => {
      if ((day.shiftId ?? null) !== batchForm.shiftId)
        changes.push({ employeeId: row.employeeId, workDate: day.workDate, shiftId: batchForm.shiftId })
    })
  } else {
    if (!batchForm.workDate) return changes
    props.rows.forEach((row) => {
      if (currentOf(row.employeeId, batchForm.workDate) !== batchForm.shiftId) {
        changes.push({ employeeId: row.employeeId, workDate: batchForm.workDate, shiftId: batchForm.shiftId })
      }
    })
  }
  return changes
})

const clearChanges = computed(() => {
  const changes = []
  props.rows.forEach((row) => {
    row.days.forEach((day) => {
      if (clearForm.scope === 'day' && day.workDate !== clearForm.workDate) return
      if ((day.shiftId ?? null) != null)
        changes.push({ employeeId: row.employeeId, workDate: day.workDate, shiftId: null })
    })
  })
  return changes
})

/** 预览文案与格数：aria-live 播报读的就是这段，必须包含具体数量而非「若干」 */
const previewText = computed(() => {
  if (props.mode === 'spread') {
    const { write, skip } = spreadPreview.value
    return `将写入 ${write} 个格子${skip ? `，跳过 ${skip} 个已有排班` : ''}`
  }
  if (props.mode === 'copy') {
    const { add, overwrite, clear } = copyPreview.value
    return `新增 ${add} 格 · 覆盖 ${overwrite} 格 · 清空 ${clear} 格`
  }
  if (props.mode === 'batch') return `将变更 ${batchChanges.value.length} 个格子`
  return `将清空 ${clearChanges.value.length} 个格子`
})

const spreadDisabled = computed(
  () => !spreadForm.shiftId || !spreadDates.value.length || !spreadEmployeeIds.value.length
)
const batchDisabled = computed(() => {
  if (!batchForm.shiftId) return true
  return batchForm.direction === 'row' ? batchForm.employeeId == null : !batchForm.workDate
})
const clearDisabled = computed(
  () => clearChanges.value.length === 0 || (clearForm.scope === 'day' && !clearForm.workDate)
)

function resetForms() {
  spreadForm.shiftId = enabledShifts.value[0] ? enabledShifts.value[0].id : null
  spreadForm.range = props.dates.length === 7 ? [props.dates[0], props.dates[6]] : []
  spreadForm.weekdays = []
  spreadForm.employeeIds = []
  spreadForm.skipExisting = true
  batchForm.direction = (props.seed && props.seed.direction) || 'row'
  batchForm.employeeId =
    props.seed && props.seed.employeeId != null
      ? props.seed.employeeId
      : props.rows[0]
        ? props.rows[0].employeeId
        : null
  batchForm.workDate = (props.seed && props.seed.workDate) || props.dates[0] || null
  batchForm.shiftId = null
  clearForm.scope = 'week'
  clearForm.workDate = props.dates[0] || null
  prevMatrix.value = null
  copyError.value = false
}

/** 复制上一周：需要单独拉上一周矩阵（GET /schedules 二次调用，无需新契约） */
async function loadPrevWeek() {
  copyLoading.value = true
  copyError.value = false
  try {
    prevMatrix.value = await getSchedules({
      stationId: props.stationId,
      weekStart: formatDate(addDays(localDate(props.weekStart), -7))
    })
  } catch (e) {
    prevMatrix.value = null
    copyError.value = true
  } finally {
    copyLoading.value = false
  }
}

/** 一键铺排：走服务端契约，成功后由页面重载矩阵 */
async function submitSpread() {
  if (!spreadForm.skipExisting) {
    try {
      await ElMessageBox.confirm(
        `「跳过已有排班」已关闭，本次铺排会覆盖命中范围内已有的排班，且不可撤回。${previewText.value}`,
        '覆盖已有排班',
        { confirmButtonText: '确认铺排', cancelButtonText: '再想想', type: 'warning' }
      )
    } catch (e) {
      return
    }
  }
  saving.value = true
  try {
    const result = await saveSchedulesByStation(
      {
        stationId: props.stationId,
        shiftId: spreadForm.shiftId,
        startDate: spreadForm.range[0],
        endDate: spreadForm.range[1],
        employeeIds: spreadForm.employeeIds.length ? spreadForm.employeeIds : undefined,
        weekdays: spreadForm.weekdays.length ? spreadForm.weekdays : undefined,
        skipExisting: spreadForm.skipExisting
      },
      { silent: true }
    )
    ElMessage.success(`已铺排 ${result.created} 处${result.skipped ? `，跳过 ${result.skipped} 处已有排班` : ''}`)
    close()
    emit('spread-done')
  } catch (e) {
    if (e && e.code === ATTENDANCE_CODE.SHIFT_UNAVAILABLE) {
      ElMessage.warning('所选班次已被删除或停用（9106），请刷新后重新选择')
    } else {
      ElMessage.error((e && e.message) || '一键铺排失败，请重试')
    }
  } finally {
    saving.value = false
  }
}

/** 复制上一周：先预览三类计数，确认后落到本地 dirty（不直接提交） */
async function submitCopy() {
  const { add, overwrite, clear, changes } = copyPreview.value
  try {
    await ElMessageBox.confirm(
      `将新增 ${add} 格、覆盖 ${overwrite} 格、清空 ${clear} 格。复制只写入本地待保存队列，覆盖当前周已有排班，请确认后仍可逐格微调再统一保存。`,
      '复制上一周',
      { confirmButtonText: '确认复制', cancelButtonText: '再想想', type: 'warning' }
    )
  } catch (e) {
    return
  }
  emit('apply', changes)
  close()
}

async function submitBatch() {
  if (batchForm.direction === 'row' && !batchForm.shiftId) return
  emit('apply', batchChanges.value)
  close()
}

/** 清空是纯覆盖类操作，且不可撤回，固定二次确认（B0.3） */
async function submitClear() {
  const scopeText = clearForm.scope === 'week' ? '本周全部排班' : `${clearForm.workDate} 的全员排班`
  try {
    await ElMessageBox.confirm(
      `将清空${scopeText}，共 ${clearChanges.value.length} 个格子，清空后需保存才会生效。`,
      '清空排班',
      {
        confirmButtonText: '确认清空',
        cancelButtonText: '再想想',
        type: 'warning'
      }
    )
  } catch (e) {
    return
  }
  emit('apply', clearChanges.value)
  close()
}

function handleConfirm() {
  if (props.mode === 'spread') return submitSpread()
  if (props.mode === 'copy') return submitCopy()
  if (props.mode === 'batch') return submitBatch()
  return submitClear()
}

function close() {
  emit('update:modelValue', false)
}

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return
    resetForms()
    if (props.mode === 'copy') loadPrevWeek()
  }
)
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="MODE_TITLE[mode]"
    width="640px"
    :close-on-click-modal="false"
    @update:model-value="close"
  >
    <!-- ========== 一键铺排 ========== -->
    <el-form v-if="mode === 'spread'" label-width="110px" :disabled="saving">
      <el-form-item label="驿站">
        <span class="form-static">{{ stationName || '—' }}</span>
      </el-form-item>
      <el-form-item label="班次" required>
        <el-select v-model="spreadForm.shiftId" class="form-field" placeholder="请选择要铺排的班次">
          <el-option
            v-for="shift in enabledShifts"
            :key="shift.id"
            :label="`${shift.shiftName} ${shift.startTime}-${shift.endTime}`"
            :value="shift.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="日期区间" required>
        <el-date-picker
          v-model="spreadForm.range"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          unlink-panels
          class="form-field"
        />
      </el-form-item>
      <el-form-item label="星期">
        <el-select v-model="spreadForm.weekdays" multiple collapse-tags placeholder="不选即全周" class="form-field">
          <el-option v-for="item in WEEKDAY_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <p class="form-hint">不选任何星期时按「整周每天」铺排；跨周区间以提交结果为准。</p>
      </el-form-item>
      <el-form-item label="人员">
        <el-select
          v-model="spreadForm.employeeIds"
          multiple
          filterable
          collapse-tags
          placeholder="默认全员"
          class="form-field"
        >
          <el-option v-for="row in rows" :key="row.employeeId" :label="row.employeeName" :value="row.employeeId" />
        </el-select>
        <p class="form-hint">不选即对该驿站全部在岗员工铺排（共 {{ rows.length }} 人）。</p>
      </el-form-item>
      <el-form-item label="跳过已有排班">
        <el-switch v-model="spreadForm.skipExisting" />
        <span class="form-inline-hint">{{
          spreadForm.skipExisting ? '已有排班的格子保持不动' : '命中范围内已有排班将被覆盖'
        }}</span>
      </el-form-item>
    </el-form>

    <!-- ========== 复制上一周 ========== -->
    <div v-else-if="mode === 'copy'">
      <StateBlock v-if="copyError" variant="error" title="上一周排班加载失败" @action="loadPrevWeek" />
      <StateBlock
        v-else-if="!copyLoading && prevEmpty"
        variant="empty"
        title="上一周无排班记录，无可复制内容"
        description="可先用「一键铺排」铺出上周排班，或直接在本周矩阵逐格编辑"
      />
      <p v-else class="copy-note">
        复制会以「上一周」的班次覆盖当前周（{{ weekStart }}
        所在周）的对应格子，结果先落在本地待保存队列，可继续微调后统一保存。
      </p>
    </div>

    <!-- ========== 整行 / 整列批量设置 ========== -->
    <el-form v-else-if="mode === 'batch'" label-width="110px">
      <el-form-item label="批量方式">
        <el-radio-group v-model="batchForm.direction">
          <el-radio value="row">整行（某员工整周）</el-radio>
          <el-radio value="column">整列（某天全员）</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="batchForm.direction === 'row'" label="员工" required>
        <el-select v-model="batchForm.employeeId" filterable class="form-field" placeholder="请选择员工">
          <el-option v-for="row in rows" :key="row.employeeId" :label="row.employeeName" :value="row.employeeId" />
        </el-select>
      </el-form-item>
      <el-form-item v-else label="日期" required>
        <el-select v-model="batchForm.workDate" class="form-field" placeholder="请选择日期">
          <el-option v-for="date in dates" :key="date" :label="date" :value="date" />
        </el-select>
      </el-form-item>
      <el-form-item label="班次" required>
        <el-select v-model="batchForm.shiftId" class="form-field" placeholder="请选择班次">
          <el-option
            v-for="shift in enabledShifts"
            :key="shift.id"
            :label="`${shift.shiftName} ${shift.startTime}-${shift.endTime}`"
            :value="shift.id"
          />
        </el-select>
      </el-form-item>
      <p class="form-hint">批量设置只写入本地待保存队列，点页面「保存排班」后才提交。</p>
    </el-form>

    <!-- ========== 清空 ========== -->
    <el-form v-else label-width="110px">
      <el-form-item label="清空范围">
        <el-radio-group v-model="clearForm.scope">
          <el-radio value="week">整周</el-radio>
          <el-radio value="day">某一天</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="clearForm.scope === 'day'" label="日期" required>
        <el-select v-model="clearForm.workDate" class="form-field" placeholder="请选择日期">
          <el-option v-for="date in dates" :key="date" :label="date" :value="date" />
        </el-select>
      </el-form-item>
      <p class="form-hint">清空同样写入本地待保存队列，点「保存排班」后才提交；撤销修改可一键回退。</p>
    </el-form>

    <!-- 改动预览：aria-live 播报格数变化，避免视障用户只能看到"提交成功"却不知改了多少格 -->
    <div class="preview" aria-live="polite">
      <span class="preview__label">改动预览</span>
      <span class="preview__value">{{ previewText }}</span>
      <span v-if="mode === 'spread' && spreadBeyondWeek" class="preview__note"
        >所选区间超出当前周，跨周部分以服务端结果为准</span
      >
    </div>

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button
        type="primary"
        :loading="saving"
        :disabled="
          (mode === 'spread' && spreadDisabled) ||
          (mode === 'copy' && (prevEmpty || copyLoading || copyError)) ||
          (mode === 'batch' && batchDisabled) ||
          (mode === 'clear' && clearDisabled)
        "
        @click="handleConfirm"
      >
        {{
          mode === 'spread' ? '确认铺排' : mode === 'copy' ? '确认复制' : mode === 'batch' ? '应用到矩阵' : '确认清空'
        }}
      </el-button>
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

.form-hint {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

.form-inline-hint {
  margin-left: var(--sp-2);
  font-size: var(--fs-caption);
  color: var(--text-3);
}

.copy-note {
  margin: 0;
  font-size: var(--fs-body);
  line-height: 1.7;
  color: var(--text-2);
}

.preview {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--sp-2);
  margin-top: var(--sp-4);
  padding: var(--sp-3);
  border: 1px solid var(--state-primary-border);
  border-radius: var(--r-sm);
  background-color: var(--state-primary-bg);

  &__label {
    font-size: var(--fs-caption);
    color: var(--text-3);
  }

  &__value {
    font-size: var(--fs-body-strong);
    font-weight: var(--fw-medium);
    color: var(--text-1);
    font-variant-numeric: tabular-nums;
  }

  &__note {
    font-size: var(--fs-caption);
    color: var(--text-3);
  }
}
</style>
