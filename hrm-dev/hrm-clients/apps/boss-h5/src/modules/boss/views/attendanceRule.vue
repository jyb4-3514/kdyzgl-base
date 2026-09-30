<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showSuccessToast, showToast } from 'vant'
import ActionBar from '@kdyzgl/shared/ui/ActionBar.vue'
import PageNav from '@kdyzgl/shared/ui/PageNav.vue'
import PageState from '@kdyzgl/shared/ui/PageState.vue'
import { getAttendanceRule, getShiftsSilent, saveAttendanceRule, updateShift } from '@/api/attendance.js'
import { getStationList } from '@/api/org.js'
import { enabledShiftsSorted, matchPeriodsToShifts, minutesOfDay } from '@/utils/shiftPeriods.js'
import { MATCH_MODE } from '@kdyzgl/shared/constants/dict.js'
import {
  isWifiEditable,
  pickWifiEntry,
  rowErrors,
  validateBssid,
  validateSsid,
  wifiExtraLabel
} from '@/utils/wifiWhitelist.js'
import { useAuthStore } from '@/stores/auth.js'

/**
 * B8 打卡规则（ADMIN · 查看 + 快捷调整）
 * 为什么先选驿站再改规则：规则是按驿站维度存的（半径、围栏坐标、白名单各不相同），
 * 不选驿站的话「保存」会把 A 站的规则写歪到 B 站。
 *
 * 打卡时段与上下班时间的**唯一真源仍是「该驿站班次」**（后端只读出参 checkPeriods，`checkPeriodsReadonly=true`，
 * `PUT /rule` 传 checkPeriods 会 400）：本页据此**只读展示来源**，同时提供「就地改时间」入口——
 * 改的其实是**班次记录**（`PUT /shifts/{id}` 只动 startTime/endTime），成功后重拉规则让派生时段跟随刷新。
 * 这样既满足「在本页能改打卡时间」，又不引入第二套时间（不新增时段写入通道，不出现「班次 08:00-16:00
 * 与规则时段 08:00-12:00 并存」的时间分裂）。班次的全量增删停用仍在「考勤概览 → 班次管理」。
 *
 * 白名单（WiFi / BSSID）按「每站仅一条」单条化（设计 §12）：未配置 → 设置，已配置 → 修改 / 清除；
 * 判定只比对 SSID（区分大小写），BSSID 仅留痕；编辑只改本地表单，仍走页面统一的「保存规则」提交。
 * 提交恒 0 或 1 条（§12.12②）；加载遇历史多条只取首条并提示 T39（§12.12①）。
 * 非 ADMIN 属防御位（本端 BOSS_ROLES = [ADMIN]，登录者恒为 ADMIN）：只读、不渲染任何按钮。
 */
const VALIDATIONS = [
  { key: 'enableWifi', title: 'WiFi 校验', label: '当前连接的 WiFi 需在白名单内' },
  { key: 'enableLocation', title: '定位校验', label: '需处于电子围栏半径内' },
  { key: 'enableTimeWindow', title: '时间窗校验', label: '需在时段对应的打卡时间窗内' }
]

/**
 * 打卡频次与时段均为「由该驿站班次派生」的值（U-4/U-5）：
 * 频次 = 启用班次数 × 2（只读）；时段名称与起止取自班次——**只读来源 + 可就地改时间（底层写班次）**，
 * 不新增第二条时段写入通道（设计 ⑭.0 覆盖 U-3；PUT /rule 仍拒绝 checkPeriods）。
 */

/** 去掉 stationId 的规则体：脏检查只看规则内容，驿站切换不算「改动」 */
function bodyOf(data) {
  const body = { ...data }
  delete body.stationId
  return body
}

const loading = ref(true)
const error = ref('')
const saving = ref(false)
const stations = ref([])
const stationId = ref(null)
const rule = ref(null)
const form = ref(null)
/** 已保存态的快照：用于「有无改动」判断，避免用户对着没改的表单反复保存 */
const original = ref('')

const router = useRouter()
/** 权限分支取 auth.isAdmin 单点判据（不在页内自行判 role）；本端登录者恒为 ADMIN，只读分支属防御位 */
const auth = useAuthStore()
const wifiEditable = computed(() => isWifiEditable(auth.isAdmin))

/** 白名单编辑态：单条化后物理上恒为 0 或 1 条，editing 表示当前是否展开编辑表单 */
const editing = ref(false)
const draft = ref({ ssid: '', bssid: '' })
const draftErrors = ref({ ssid: '', bssid: '' })
/** 加载到历史多条白名单的标记：只渲染首条，卡顶提示 T39（设计 §12.12①） */
const legacyMultiple = ref(false)

/* ==================== 打卡时段：派生来源只读 + 就地改时间（底层写班次） ==================== */

/**
 * 该驿站班次全集（含停用；silent 拉取失败则空数组）：把服务端派生的 checkPeriods 映射回「可写的班次记录」。
 * 为什么必须映射：契约里 `CheckPeriod[]` 只有 `{ name, startTime, endTime }`、不含班次 id（api.md §4.6.1），
 * 而唯一写入口是班次（`PUT /shifts/{id}`，api.md §4.8.3），故按 stationId 取班次后再配对。
 * 配对口径（纯逻辑，含回归）见 utils/shiftPeriods.js。
 */
const shifts = ref([])
/** 启用班次升序：既用于配对，也用于判断「班次没取到 → 本页不能改时间」的降级说明 */
const enabledShifts = computed(() => enabledShiftsSorted(shifts.value))

/** 时段行 = 派生时段（只读来源）+ 配到的启用班次（写入口）；配不到 → shift 为 null，该行只读 */
const periodRows = computed(() => {
  const list = rule.value && Array.isArray(rule.value.checkPeriods) ? rule.value.checkPeriods : []
  return matchPeriodsToShifts(list, shifts.value).map((row) => ({
    ...row,
    // 行 key 含下标：同名班次的时段不会互相顶掉
    key: `${row.index}-${row.name}`
  }))
})
/** 派生频次（只读）：= 启用班次数 × 2 */
const frequencyText = computed(() => {
  const count = periodRows.value.length
  return count ? `${count * 2} 次（${count} 个班次）` : '—'
})
/** 派生作息：取服务端 workStartTime / workEndTime（班次派生） */
const workStartText = computed(() => (rule.value && rule.value.workStartTime ? rule.value.workStartTime : '-'))
const workEndText = computed(() => (rule.value && rule.value.workEndTime ? rule.value.workEndTime : '-'))

/* ---------- 时段就地编辑态（与 WiFi 白名单编辑互斥） ---------- */

/** 正在编辑的时段行 index，null = 无 */
const editingPeriod = ref(null)
const periodDraft = ref({ startTime: '', endTime: '' })
const periodSaving = ref(false)
/** 服务端口径失败文案（如 9114）：原样就地展示，不自行改写、不清空草稿 */
const periodError = ref('')

const showPeriodStartPicker = ref(false)
const showPeriodEndPicker = ref(false)
const periodStartValue = ref(['08', '00'])
const periodEndValue = ref(['16', '00'])

/** 可编辑：管理员 + 该行配到启用班次 + 当前无其它编辑在进行（WiFi 或另一时段） */
const canEditPeriod = (row) => wifiEditable.value && !!row.shift && editingPeriod.value === null && !editing.value

function startPeriodEdit(row) {
  if (!canEditPeriod(row)) return
  editingPeriod.value = row.index
  periodDraft.value = { startTime: row.startTime, endTime: row.endTime }
  periodError.value = ''
}

function cancelPeriodEdit() {
  editingPeriod.value = null
  periodDraft.value = { startTime: '', endTime: '' }
  periodError.value = ''
  showPeriodStartPicker.value = false
  showPeriodEndPicker.value = false
}

/**
 * 时间选择：van-time-picker，30 分钟粒度，结束时间可 24:00 —— 与 modules/boss/views/shiftForm.vue 完全同口径。
 * 为什么在此复制这几行纯函数：两处改的是同一个班次字段，粒度/取值必须一致；第 3 处出现时再上提为共享模块。
 */
function makeTimeFilter(allowEnd) {
  return (columnType, options, values) => {
    if (columnType === 'hour') return allowEnd ? options.concat({ text: '24', value: '24' }) : options
    if (columnType === 'minute') {
      if (allowEnd && values && values[0] === '24') return [{ text: '00', value: '00' }]
      return options.filter((option) => option.value === '00' || option.value === '30')
    }
    return options
  }
}
const periodStartFilter = makeTimeFilter(false)
const periodEndFilter = makeTimeFilter(true)
/** 拼接 'HH:mm'：24 点收班统一显示「24:00」（与 shiftForm.vue 同一写法，不写 00:00 以免与次日零点混淆） */
const joinTime = (values) => {
  const [h, m] = values
  return h === '24' ? '24:00' : `${h}:${m}`
}

function openPeriodStartPicker() {
  const [h, m] = String(periodDraft.value.startTime || '08:00').split(':')
  periodStartValue.value = [h || '08', m || '00']
  showPeriodStartPicker.value = true
}

function openPeriodEndPicker() {
  const [h, m] = String(periodDraft.value.endTime || '16:00').split(':')
  periodEndValue.value = [h || '16', m || '00']
  showPeriodEndPicker.value = true
}

function onPeriodStartConfirm({ selectedValues }) {
  periodDraft.value.startTime = joinTime(selectedValues)
  showPeriodStartPicker.value = false
}

function onPeriodEndConfirm({ selectedValues }) {
  periodDraft.value.endTime = joinTime(selectedValues)
  showPeriodEndPicker.value = false
}

/**
 * 保存时段时间：落点写**班次**（`PUT /shifts/{id}`，只改 startTime/endTime，其余字段原样带回），
 * 成功后重拉规则 → 派生时段跟随刷新（时间真源仍唯一，不出现第二套时段）。
 * 失败（如 9114 启用数超 2 / 一早一晚冲突）把服务端文案原样落在行内，草稿保留供用户改。
 */
async function savePeriod() {
  if (periodSaving.value) return
  const row = periodRows.value.find((item) => item.index === editingPeriod.value)
  if (!row || !row.shift) {
    periodError.value = '该时段未匹配到可写的班次，请到「班次管理」维护'
    return
  }
  if (!periodDraft.value.startTime || !periodDraft.value.endTime) {
    periodError.value = '请选择开始与结束时间'
    return
  }
  if (minutesOfDay(periodDraft.value.startTime) >= minutesOfDay(periodDraft.value.endTime)) {
    periodError.value = '结束时间须晚于开始时间'
    return
  }
  periodSaving.value = true
  periodError.value = ''
  try {
    await updateShift(row.shift.id, {
      // 班次编辑为整对象提交（api.md §4.8.3 入参同新增）：只改时间，其余字段原样带回不丢数据
      shiftName: row.shift.shiftName,
      startTime: periodDraft.value.startTime,
      endTime: periodDraft.value.endTime,
      color: row.shift.color,
      restMinutes: row.shift.restMinutes,
      status: row.shift.status
    })
    cancelPeriodEdit()
    showSuccessToast('打卡时间已更新')
    await loadRule()
  } catch (e) {
    periodError.value = e.message || '保存失败，请稍后重试'
  } finally {
    periodSaving.value = false
  }
}

const showStation = ref(false)

const currentStationName = computed(() => {
  const hit = stations.value.find((item) => item.id === stationId.value)
  return hit ? hit.stationName : '-'
})

/** 白名单真源落在 form 上（随表单一起脏检查与提交），rule 只保留服务端回读的更新时间等只读展示值 */
const wifiList = computed(() => (form.value ? form.value.wifiList : []))
/** 单条化后「已配置」等价于长度 > 0（长度恒为 0 或 1） */
const hasWifi = computed(() => wifiList.value.length > 0)

/** 卡标题 extra：可编辑 / 只读（T30，单条化后不再显示条数） */
const wifiExtra = computed(() => wifiExtraLabel(wifiEditable.value))

/**
 * 卡顶 warning 提示条（互斥取一条，优先级 T4 > T40 > T39，见设计 §12.3.2 / §12.12 / §12.13.1）：
 * 关闭校验 → 说明白名单不参与判定；开启但为空 → 强警示 T40（warning-o、不可关闭）；
 * 开启但加载到历史多条 → T39 提示将收敛为 1 条。均只提示不阻断保存（分步配置的正当流程要能走通）。
 */
const wifiNotice = computed(() => {
  if (!form.value) return null
  if (!form.value.enableWifi) {
    return { icon: 'info-o', text: 'WiFi 校验已关闭，白名单暂不参与打卡判定' }
  }
  if (!hasWifi.value) {
    return {
      icon: 'warning-o',
      text: 'WiFi 校验已开启但白名单为空：本站所有员工将无法通过 WiFi 校验打卡（错误码 9103）。请设置白名单，或关闭 WiFi 校验。'
    }
  }
  if (legacyMultiple.value) {
    return {
      icon: 'info-o',
      text: '检测到本站存在多条 WiFi 白名单（历史数据），当前仅显示第 1 条；保存后将收敛为 1 条。'
    }
  }
  return null
})

/** 就地编辑侧的阻断原因（ActionBar note）：只读分支说明权限；任一处编辑中提示先完成或取消（T19） */
const wifiBlockReason = computed(() => {
  if (!wifiEditable.value) return '当前身份只能查看打卡规则，保存需管理员权限'
  if (editingPeriod.value !== null) return '有 1 个打卡时段正在编辑，请先完成或取消'
  if (editing.value) return '有 1 条白名单正在编辑，请先完成或取消'
  return ''
})

/** 提交体：字段与 Mock 的可写白名单一一对应；时段 / 频次 / 上下班时间为班次派生的只读值，一律不发（U-4/U-5） */
const payload = computed(() => {
  if (!form.value) return null
  return {
    stationId: stationId.value,
    ruleName: form.value.ruleName.trim(),
    enableWifi: !!form.value.enableWifi,
    enableLocation: !!form.value.enableLocation,
    enableTimeWindow: !!form.value.enableTimeWindow,
    matchMode: form.value.matchMode,
    longitude: Number(form.value.longitude),
    latitude: Number(form.value.latitude),
    radius: Number(form.value.radius),
    // 显式提交白名单：不提交则会沿用服务端现值，页面上的设置/修改/清除保存后不生效。
    // 单条化硬约束：截断为 0 或 1 条，正常路径永不产生 length > 1 的请求体（设计 §12.12②）
    wifiList: form.value.wifiList.slice(0, 1).map((w) => ({
      ssid: String(w.ssid).trim(),
      bssid: w.bssid ? String(w.bssid).trim() : null
    })),
    allowEarlyMin: Number(form.value.allowEarlyMin),
    allowLateMin: Number(form.value.allowLateMin),
    lateThresholdMin: Number(form.value.lateThresholdMin),
    earlyLeaveThresholdMin: Number(form.value.earlyLeaveThresholdMin)
  }
})

/** 表单校验：错误文案直接作为 ActionBar 的 note，用户不用点保存才知道哪里不对 */
const formError = computed(() => {
  const data = form.value
  if (!data) return ''
  if (!data.ruleName || !data.ruleName.trim()) return '规则名称不能为空'
  if (data.ruleName.trim().length > 50) return '规则名称最长 50 个字符'
  if (!(Number(data.allowEarlyMin) >= 0)) return '允许提前打卡分钟数须不小于 0'
  if (!(Number(data.allowLateMin) >= 0)) return '允许延后打卡分钟数须不小于 0'
  const lng = Number(data.longitude)
  if (data.longitude === '' || !Number.isFinite(lng) || Math.abs(lng) > 180) return '经度须为 -180 ~ 180 的数字'
  const lat = Number(data.latitude)
  if (data.latitude === '' || !Number.isFinite(lat) || Math.abs(lat) > 90) return '纬度须为 -90 ~ 90 的数字'
  if (!(Number(data.radius) > 0)) return '围栏半径须大于 0'
  if (!(Number(data.lateThresholdMin) >= 0)) return '迟到阈值须不小于 0'
  if (!(Number(data.earlyLeaveThresholdMin) >= 0)) return '早退阈值须不小于 0'
  if (!data.enableWifi && !data.enableLocation && !data.enableTimeWindow)
    return '三项校验全部关闭时为免校验打卡，确认无误后再保存'
  return ''
})

const dirty = computed(() => {
  if (!payload.value || !original.value) return false
  // 历史多条：即便用户未改动，保存也会把服务端收敛为 1 条，故视为有改动（对应 T39「保存后收敛为 1 条」）
  if (legacyMultiple.value) return true
  return JSON.stringify(bodyOf(payload.value)) !== original.value
})

/** ActionBar 主操作禁用：只读分支 / 无改动 / 表单错误 / 白名单未完成或未填（任一即禁用，note 说明原因） */
const saveDisabled = computed(
  () => !wifiEditable.value || !dirty.value || !!formError.value || !!wifiBlockReason.value
)

/** ActionBar note：可编辑分支先报表单错误再报白名单汇总；只读分支固定说明权限（不展示与字段无关的错误） */
const actionNote = computed(() =>
  wifiEditable.value ? formError.value || wifiBlockReason.value : wifiBlockReason.value
)

const actions = computed(() => [
  { key: 'save', label: '保存规则', plain: false, loading: saving.value, disabled: saveDisabled.value }
])

async function loadStations() {
  const list = await getStationList()
  stations.value = list
  if (stationId.value == null && list.length) stationId.value = list[0].id
}

async function loadRule() {
  if (stationId.value == null) return
  loading.value = true
  error.value = ''
  // 换驿站 / 重拉时丢弃未完成的编辑草稿，避免把 A 站的输入带到 B 站
  cancelEdit()
  cancelPeriodEdit()
  try {
    // 班次与规则并发取：时段由班次派生，需要班次记录才能提供「就地改时间」的写入口。
    // 班次拉取失败不阻断规则展示（降级为时段只读 + 行内说明），故用 silent 版并兜底空数组。
    const [data, shiftRows] = await Promise.all([
      getAttendanceRule({ stationId: stationId.value }),
      getShiftsSilent({ stationId: stationId.value }).catch(() => [])
    ])
    shifts.value = Array.isArray(shiftRows) ? shiftRows : []
    rule.value = data
    // 单条化加载口径（设计 §12.12①）：多条历史数据只取首条渲染，并置标记触发 T39 提示
    const wifi = pickWifiEntry(data.wifiList)
    legacyMultiple.value = wifi.hasLegacyMultiple
    form.value = {
      ruleName: data.ruleName,
      enableWifi: data.enableWifi,
      enableLocation: data.enableLocation,
      enableTimeWindow: data.enableTimeWindow,
      matchMode: data.matchMode,
      longitude: String(data.longitude),
      latitude: String(data.latitude),
      radius: data.radius,
      // 白名单落到表单：单条化后恒 0 或 1 条（历史多条已在上面截为一条）
      wifiList: wifi.entry ? [wifi.entry] : [],
      allowEarlyMin: data.allowEarlyMin,
      allowLateMin: data.allowLateMin,
      lateThresholdMin: data.lateThresholdMin,
      earlyLeaveThresholdMin: data.earlyLeaveThresholdMin
    }
    original.value = JSON.stringify(bodyOf(payload.value))
  } catch (e) {
    error.value = e.message || '加载失败'
    rule.value = null
    form.value = null
    shifts.value = []
    legacyMultiple.value = false
  } finally {
    loading.value = false
  }
}

async function load() {
  try {
    await loadStations()
  } catch (e) {
    loading.value = false
    error.value = e.message || '驿站列表加载失败'
    return
  }
  await loadRule()
}

function pickStation(item) {
  showStation.value = false
  if (item.id === stationId.value) return
  stationId.value = item.id
  loadRule()
}

function toggle(key) {
  form.value[key] = !form.value[key]
}

/* ==================== WiFi 白名单编辑（本地表单，随页面统一保存 · 每站仅一条） ==================== */

/** 收起编辑态：未配置态取消 → 回到未配置（不产生空记录）；已配置态取消 → 丢弃草稿回原值 */
function cancelEdit() {
  editing.value = false
  draft.value = { ssid: '', bssid: '' }
  draftErrors.value = { ssid: '', bssid: '' }
}

/** 进入编辑态：entry 为 null 表示未配置态「设置 WiFi」，否则为已配置态「修改」（两态共用同一表单） */
function startEdit(entry) {
  // 与「时段就地改时间」互斥：两处同时处于未保存态会让用户分不清哪份改动会被提交
  if (editingPeriod.value !== null) return
  editing.value = true
  draft.value = { ssid: entry ? entry.ssid : '', bssid: entry ? entry.bssid : '' }
  draftErrors.value = { ssid: '', bssid: '' }
}

/** 行内校验：错误就近展示在字段下方（error-message），点「完成」/ 失焦都会走这里 */
function validateDraft() {
  draftErrors.value = rowErrors(draft.value)
  return !draftErrors.value.ssid && !draftErrors.value.bssid
}

function onSsidBlur() {
  draftErrors.value = { ...draftErrors.value, ssid: validateSsid(draft.value.ssid) }
}

function onBssidBlur() {
  draftErrors.value = { ...draftErrors.value, bssid: validateBssid(draft.value.bssid) }
}

/** 完成：校验通过才收起为展示态并写入表单（恒 0 或 1 条；不立即请求，仍走页面统一保存） */
function commitEdit() {
  if (!validateDraft()) return
  form.value.wifiList = [{ ssid: draft.value.ssid.trim(), bssid: draft.value.bssid.trim() }]
  cancelEdit()
}

/** 清除：二次确认（T35–T37，设计 §12.5①）后置空唯一一条；确认后仅改本地表单，保存按钮随之可用 */
async function askClear() {
  try {
    await showConfirmDialog({
      title: '清除 WiFi 白名单',
      message:
        '清除后本站将不再配置 WiFi 白名单；若「WiFi 校验」已开启，将无人能通过 WiFi 校验。确定清除？',
      confirmButtonText: '清除',
      cancelButtonText: '取消',
      confirmButtonColor: 'var(--color-danger)'
    })
  } catch (e) {
    return // 取消清除：什么都不做
  }
  form.value.wifiList = []
}

/**
 * 「读取当前 WiFi」（预留态/降级分支）：安卓壳未实现 HrmBridge.getWifiInfo，桥接恒返回 mock:true，
 * 因此**不发起任何读取、不预填任何值**，只按设计 §3.3 明确告知需手动输入 —— 不得伪装成已读取。
 * 落位（设计 §12.5③）：编辑中态紧贴 BSSID 字段下方；展示态/未配置态不出现该按钮。
 * TODO(扩展): 壳侧实现 HrmBridge.getWifiInfo 后改为：
 *   const info = getWifiInfo()
 *   if (canAutoFillWifi(info)) { 回填 SSID/BSSID 并标注来源「来自当前连接（安卓壳）」 } else { 走本降级分支 }
 * 判据必须是 getWifiInfo().mock === false（壳内也可能返回 mock），不得用「是否在壳内」判断；
 * 前置条件：壳侧实现 + 真机验证 mock===false + 安全面复核（设备信息读取属敏感能力）。
 */
function onReadWifi() {
  showToast('当前版本需手动输入 WiFi 名称：安卓壳暂不提供自动读取')
}

async function onSave() {
  if (saving.value || formError.value || wifiBlockReason.value || !dirty.value) return
  // 保存前二次确认（T41–T43，设计 §12.13.1）：开启校验却提交空名单会让全员 WiFi 校验失败，
  // 真正的危险点是「提交落地那一刻」，故在提交前再确认一次；「仍要保存」放行、「返回设置」回表单，均不阻断。
  if (payload.value.enableWifi && payload.value.wifiList.length === 0) {
    try {
      await showConfirmDialog({
        title: '确认保存？白名单为空',
        message:
          'WiFi 校验已开启但白名单为空，保存后本站所有员工将无法通过 WiFi 校验打卡（错误码 9103）。确定仍要保存？',
        confirmButtonText: '仍要保存',
        cancelButtonText: '返回设置',
        confirmButtonColor: 'var(--color-warning)'
      })
    } catch (e) {
      return // 返回设置：留在表单，不提交
    }
  }
  saving.value = true
  try {
    const vo = await saveAttendanceRule(payload.value)
    rule.value = vo
    original.value = JSON.stringify(bodyOf(payload.value))
    legacyMultiple.value = false // 保存后已收敛为 1 条，T39 提示随之解除
    cancelEdit()
    showSuccessToast('打卡规则已保存')
  } catch (e) {
    // 错误提示由 http 层统一弹出（9107 的时段原因在 message 里），页内不重复
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="rule-page">
    <PageNav title="打卡规则" />
    <div class="page page--bar">
      <PageState :loading="loading" :error="error" :rows="8" @retry="load">
        <div v-if="form">
          <button type="button" class="station-pick card" @click="showStation = true">
            <span class="muted">当前驿站</span>
            <span class="station-pick__value"
              >{{ currentStationName }}<van-icon name="arrow" aria-hidden="true"
            /></span>
          </button>

          <div class="section-title">基础信息</div>
          <van-cell-group inset>
            <van-field v-model="form.ruleName" label="规则名称" placeholder="请输入规则名称" maxlength="50" />
          </van-cell-group>

          <div class="section-title">打卡频次<span class="section-title__extra">由班次派生 · 只读</span></div>
          <div class="card derived">
            <div class="derived__row">
              <span>每日打卡次数</span>
              <span class="derived__value tabular-nums">{{ frequencyText }}</span>
            </div>
            <p class="tip">打卡频次 = 启用班次数 × 2（每个班次上下班各一次），由驿站班次自动派生，不可在此修改。</p>
          </div>

          <div class="section-title">
            <span>打卡时段</span>
            <span class="section-title__extra tabular-nums">{{ periodRows.length }} 个时段 · 可改时间</span>
          </div>
          <van-cell-group inset>
            <!-- 空态：指向本端班次管理（U-3 旧「网页端维护」文案作废，设计 ⑭.1），提供 ≥44px 直达按钮；
                 本页不凭空造班次，无启用班次即无时段可改 -->
            <van-cell v-if="!periodRows.length" title="该驿站尚未配置启用班次">
              <template #label>
                <p class="tip">去「班次管理」新增班次后，本站打卡时间自动生效</p>
                <van-button
                  plain
                  type="primary"
                  size="small"
                  class="go-shifts"
                  @click="router.push('/boss/shifts')"
                >
                  去班次管理
                </van-button>
              </template>
            </van-cell>

            <template v-for="row in periodRows" :key="row.key">
              <!-- 展示态：时段名称与起止取自「该驿站启用班次」（只读来源）；保存前可改起止 -->
              <van-cell
                v-if="editingPeriod !== row.index"
                :title="`时段 ${row.index + 1} · ${row.name}`"
                :value="`${row.startTime} - ${row.endTime}`"
              >
                <template v-if="canEditPeriod(row)" #right-icon>
                  <button type="button" class="period-act" @click="startPeriodEdit(row)">修改时间</button>
                </template>
              </van-cell>

              <!-- 编辑态：就地展开（同一 inset 组内仍是 van-cell 序列），交互与「班次管理」表单同口径 -->
              <template v-else>
                <van-cell
                  :title="`时段 ${row.index + 1} · ${row.name}`"
                  label="此处修改的即本站班次时间，保存后打卡时段随之生效"
                />
                <van-cell
                  title="开始时间"
                  is-link
                  :value="periodDraft.startTime"
                  :aria-label="`开始时间，当前 ${periodDraft.startTime}`"
                  @click="openPeriodStartPicker"
                />
                <van-cell
                  title="结束时间"
                  is-link
                  :value="periodDraft.endTime"
                  :aria-label="`结束时间，当前 ${periodDraft.endTime}`"
                  @click="openPeriodEndPicker"
                />
                <!-- 服务端失败文案（如 9114）原样落在这里，role=alert 可被读屏即时播报 -->
                <van-cell v-if="periodError" :border="false">
                  <template #title>
                    <p class="period-error" role="alert">{{ periodError }}</p>
                  </template>
                </van-cell>
                <van-cell :border="false">
                  <div class="period-actions">
                    <van-button plain size="small" :disabled="periodSaving" @click="cancelPeriodEdit">取消</van-button>
                    <van-button type="primary" size="small" :loading="periodSaving" @click="savePeriod">
                      保存时间
                    </van-button>
                  </div>
                </van-cell>
              </template>
            </template>
          </van-cell-group>
          <!-- 班次拉取失败：规则照常展示，但要说清「为何这里不能改时间」并给出口，不让按钮凭空消失 -->
          <p v-if="periodRows.length && !enabledShifts.length" class="tip">
            班次信息未取到，暂不能在此修改打卡时间；可到「考勤概览 → 班次管理」维护，改完本站打卡时间即时跟随。
          </p>
          <div class="card derived">
            <div class="derived__row">
              <span>作息时间（自动派生）</span>
              <span class="derived__value tabular-nums">{{ workStartText }} - {{ workEndText }}</span>
            </div>
            <p class="tip">
              打卡时段与上下班时间由该驿站班次决定（唯一时间真源）：本页改的即班次时间，保存后打卡时段随之生效；
              班次的增删停用仍在「考勤概览 → 班次管理」。
            </p>
          </div>

          <div class="section-title">打卡时间窗</div>
          <van-cell-group inset>
            <van-cell title="提前可打卡（分钟）" center>
              <template #right-icon>
                <van-stepper
                  v-model="form.allowEarlyMin"
                  :min="0"
                  :max="240"
                  :step="5"
                  button-size="32px"
                  input-width="56px"
                />
              </template>
            </van-cell>
            <van-cell title="延后可打卡（分钟）" center>
              <template #right-icon>
                <van-stepper
                  v-model="form.allowLateMin"
                  :min="0"
                  :max="240"
                  :step="5"
                  button-size="32px"
                  input-width="56px"
                />
              </template>
            </van-cell>
          </van-cell-group>
          <p class="tip">
            每个时段的打卡窗口 = 上班时间提前 {{ form.allowEarlyMin }} 分钟开放，下班时间延后
            {{ form.allowLateMin }} 分钟关闭； 各项校验全开时超出窗口会被拒绝（9102）。
          </p>

          <div class="section-title">迟到 / 早退阈值</div>
          <van-cell-group inset>
            <van-cell title="迟到阈值（分钟）" center>
              <template #right-icon>
                <van-stepper
                  v-model="form.lateThresholdMin"
                  :min="0"
                  :max="240"
                  :step="5"
                  button-size="32px"
                  input-width="56px"
                />
              </template>
            </van-cell>
            <van-cell title="早退阈值（分钟）" center>
              <template #right-icon>
                <van-stepper
                  v-model="form.earlyLeaveThresholdMin"
                  :min="0"
                  :max="240"
                  :step="5"
                  button-size="32px"
                  input-width="56px"
                />
              </template>
            </van-cell>
          </van-cell-group>

          <div class="section-title">校验项<span class="section-title__extra">关闭即不参与判定</span></div>
          <van-cell-group inset>
            <van-cell v-for="item in VALIDATIONS" :key="item.key" :title="item.title" :label="item.label" center>
              <template #right-icon>
                <van-switch
                  :model-value="form[item.key]"
                  size="24px"
                  :aria-label="`${item.title}开关`"
                  @click="toggle(item.key)"
                  @keydown.enter.prevent="toggle(item.key)"
                  @keydown.space.prevent="toggle(item.key)"
                />
              </template>
            </van-cell>
          </van-cell-group>

          <div class="chip-row" role="radiogroup" aria-label="校验项组合关系">
            <button
              v-for="mode in Object.keys(MATCH_MODE)"
              :key="mode"
              type="button"
              class="chip"
              :class="{ 'chip--active': form.matchMode === mode }"
              role="radio"
              :aria-checked="form.matchMode === mode"
              @click="form.matchMode = mode"
            >
              {{ MATCH_MODE[mode].label }}
            </button>
            <span class="chip-row__hint">多项校验之间的组合关系</span>
          </div>

          <div class="section-title">
            WiFi 白名单<span class="section-title__extra">{{ wifiExtra }}</span>
          </div>
          <div class="card">
            <!-- warning 提示条（互斥取一条，优先级：校验关闭 T4 > 开启但为空 T40 强警示 > 多条历史 T39） -->
            <van-notice-bar
              v-if="wifiNotice"
              class="notice"
              :left-icon="wifiNotice.icon"
              wrapable
              :text="wifiNotice.text"
              color="var(--color-warning)"
              background="var(--color-warning-surface)"
            />

            <!-- 恒常口径说明：判定只比对 SSID，BSSID 仅留痕（不随权限分支隐藏） -->
            <p class="tip">校验只比对 SSID（区分大小写）；BSSID 仅作留痕，填或不填都不影响判定。</p>

            <!-- 只读分支（防御位）：保留原说明，不渲染任何写控件 -->
            <p v-if="!wifiEditable" class="rule-text wifi-readonly">
              白名单需现场抓取 SSID 后维护；改白名单请走 PC 端。
            </p>

            <!-- A. 未配置态：唯一动作「设置 WiFi」（未配置进入取消 → 回到本态，不产生空记录） -->
            <!-- 时段编辑中禁用：与「时段就地改时间」互斥，避免两处同时处于未保存态 -->
            <template v-if="!hasWifi && !editing">
              <p class="wifi-empty">{{ wifiEditable ? '尚未设置 WiFi 白名单' : '未配置' }}</p>
              <van-button
                v-if="wifiEditable"
                block
                plain
                type="primary"
                class="wifi-set"
                :disabled="editingPeriod !== null"
                @click="startEdit(null)"
              >
                设置 WiFi
              </van-button>
            </template>

            <!-- B. 已配置态（唯一一条）：动作「修改 / 清除」；只读分支不渲染按钮、不留空位 -->
            <van-cell
              v-else-if="hasWifi && !editing"
              :title="wifiList[0].ssid"
              :label="wifiList[0].bssid ? `BSSID ${wifiList[0].bssid}` : 'BSSID 未填'"
            >
              <template v-if="wifiEditable" #right-icon>
                <button
                  type="button"
                  class="wifi-act"
                  :disabled="editingPeriod !== null"
                  @click="startEdit(wifiList[0])"
                >
                  修改
                </button>
                <button type="button" class="wifi-act wifi-act--danger" :disabled="editingPeriod !== null" @click="askClear">
                  清除
                </button>
              </template>
            </van-cell>

            <!-- C. 编辑中态（未配置 / 已配置共用同一表单；「完成」校验通过才收起，「取消」丢弃改动） -->
            <template v-else-if="editing">
              <van-field
                v-model="draft.ssid"
                label="SSID"
                required
                maxlength="32"
                placeholder="如 ST001-Express"
                :error-message="draftErrors.ssid"
                @blur="onSsidBlur"
              />
              <van-field
                v-model="draft.bssid"
                label="BSSID"
                maxlength="17"
                placeholder="如 AC:84:C6:00:00:03（可留空）"
                :error-message="draftErrors.bssid"
                @blur="onBssidBlur"
              />
              <!-- 降级态：紧贴唯一输入行（设计 §12.5③）；能力未开放，可点但明确拒绝，不发起读取、不预填 -->
              <van-button v-if="wifiEditable" plain type="primary" size="small" class="wifi-read" @click="onReadWifi">
                读取当前 WiFi（暂不支持）
              </van-button>
              <p class="tip wifi-read-tip">当前版本请手动输入 SSID 与 BSSID。</p>
              <div class="wifi-edit-actions">
                <van-button plain size="small" class="wifi-edit-btn" @click="cancelEdit">取消</van-button>
                <van-button type="primary" size="small" class="wifi-edit-btn" @click="commitEdit">完成</van-button>
              </div>
            </template>
          </div>

          <div class="section-title">电子围栏</div>
          <van-cell-group inset>
            <van-field v-model="form.longitude" label="中心经度" type="number" placeholder="如 117.201000" />
            <van-field v-model="form.latitude" label="中心纬度" type="number" placeholder="如 31.821000" />
            <van-cell title="围栏半径（米）" center>
              <template #right-icon>
                <van-stepper
                  v-model="form.radius"
                  :min="50"
                  :max="5000"
                  :step="50"
                  button-size="32px"
                  input-width="56px"
                />
              </template>
            </van-cell>
          </van-cell-group>

          <p class="tip">规则更新时间：{{ rule.updateTime }}</p>
        </div>
      </PageState>
    </div>

    <ActionBar :actions="actions" :note="actionNote" :submitting="saving" @select="onSave" />

    <van-popup v-model:show="showStation" round position="bottom" safe-area-inset-bottom>
      <div class="sheet">
        <div class="sheet__title">选择驿站</div>
        <button
          v-for="item in stations"
          :key="item.id"
          type="button"
          class="sheet__item"
          :aria-pressed="item.id === stationId"
          @click="pickStation(item)"
        >
          <span>{{ item.stationName }}</span>
          <van-icon v-if="item.id === stationId" name="passed" aria-hidden="true" />
        </button>
      </div>
    </van-popup>

    <!-- 时段时间选择（与「班次管理」表单同口径）：30 分钟粒度，结束时间可 24:00 -->
    <van-popup v-model:show="showPeriodStartPicker" round position="bottom" safe-area-inset-bottom>
      <van-time-picker
        v-model="periodStartValue"
        title="选择开始时间"
        :columns-type="['hour', 'minute']"
        :filter="periodStartFilter"
        @confirm="onPeriodStartConfirm"
        @cancel="showPeriodStartPicker = false"
      />
    </van-popup>

    <van-popup v-model:show="showPeriodEndPicker" round position="bottom" safe-area-inset-bottom>
      <van-time-picker
        v-model="periodEndValue"
        title="选择结束时间"
        :columns-type="['hour', 'minute']"
        :filter="periodEndFilter"
        @confirm="onPeriodEndConfirm"
        @cancel="showPeriodEndPicker = false"
      />
    </van-popup>
  </div>
</template>

<style scoped>
.station-pick {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-height: 52px;
  margin-top: var(--sp-3);
  font-size: var(--fs-body);
  color: var(--text-1);
}

.station-pick__value {
  display: inline-flex;
  gap: var(--sp-1);
  align-items: center;
  color: var(--color-primary);
}

.chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-3);
  align-items: center;
  margin-top: var(--sp-3);
}

/* chip 主触控目标 ≥44（7.4），样式复用全局 .chip / .chip--active */
.chip-row__hint {
  flex: 1;
  min-width: 120px;
  font-size: var(--fs-caption);
  color: var(--text-3);
}

.derived {
  margin-top: var(--sp-3);
}

.derived__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 44px;
  font-size: var(--fs-body);
  color: var(--text-2);
}

.derived__value {
  font-size: var(--fs-num-sm);
  font-weight: var(--fw-semibold);
  color: var(--text-1);
}

.rule-text {
  margin: 0;
  font-size: var(--fs-body);
  line-height: var(--lh-body);
  color: var(--text-1);
  word-break: break-all;
}

/* 只读分支的说明与恒常 tip 之间留一档间距 */
.wifi-readonly {
  margin-top: var(--sp-3);
}

/* 「去班次管理」直达按钮：空态唯一动作，触控区 ≥44（设计 ⑭.1） */
.go-shifts {
  min-height: var(--touch-min);
  margin-top: var(--sp-2);
}

/* 行内「修改 / 清除」：原生按钮，触控区 ≥44×44（设计 §9.2） */
.wifi-act {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: var(--touch-min);
  min-height: var(--touch-min);
  font-size: var(--fs-body);
  color: var(--color-primary);
  background: none;
  border: none;
}

.wifi-act--danger {
  color: var(--color-danger);
}

/* 禁用态（时段编辑中）：降一档到 disabled 色，明确「此刻不可点」而非按钮失灵 */
.wifi-act:disabled {
  color: var(--text-disabled);
}

/* 时段行内「修改时间」：与 .wifi-act 同规格（原生按钮，触控区 ≥44×44） */
.period-act {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: var(--touch-min);
  min-height: var(--touch-min);
  padding: 0 var(--sp-2);
  font-size: var(--fs-body);
  color: var(--color-primary);
  background: none;
  border: none;
}

/* 服务端失败文案（9114 / 400）：原样落地，交给读屏 role=alert 即时播报 */
.period-error {
  margin: 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--color-danger);
}

/* 编辑态行内动作：取消 / 保存各占一半，触控区 ≥44（与白名单编辑同口径） */
.period-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--sp-3);
  width: 100%;
}

.period-actions :deep(.van-button) {
  min-height: var(--touch-min);
}

.wifi-edit-actions {
  display: flex;
  gap: var(--sp-3);
  margin-top: var(--sp-3);
}

/* 「取消 / 完成」各占一半，触控区 ≥44（设计 §8.3：完成按钮紧邻字段，不落在页面底部） */
.wifi-edit-btn {
  flex: 1;
  min-height: var(--touch-min);
}

/* 空态：可编辑「尚未设置 WiFi 白名单」/ 只读「未配置」，都不含「失败/错误/网络」字样 */
.wifi-empty {
  margin: var(--sp-5) 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
  text-align: center;
}

/* 「设置 WiFi」沿用原「+ 添加白名单」取值（设计 §12.8） */
.wifi-set {
  margin-top: var(--sp-4);
  min-height: var(--touch-min);
}

.wifi-read {
  margin-top: var(--sp-3);
  min-height: var(--touch-min);
}

.wifi-read-tip {
  margin-top: var(--sp-2);
}

.sheet {
  padding: var(--sp-5) 0 var(--sp-6);
}

.sheet__title {
  margin-bottom: var(--sp-3);
  font-size: var(--fs-h2);
  font-weight: var(--fw-semibold);
  text-align: center;
}

.sheet__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-height: 48px;
  padding: 0 var(--sp-4);
  font-size: var(--fs-body-strong);
  color: var(--text-1);
  background: none;
  border: none;
  border-top: 1px solid var(--border-line);
}
</style>
