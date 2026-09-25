<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getEmployees } from '@/api/employee.js'
import { WORK_ORDER_PRIORITY, WORK_ORDER_SLA_HOURS, WORK_ORDER_TYPE } from '@kdyzgl/shared/constants/dict'
import { formatDateTime } from '@kdyzgl/shared/domain/time.js'
import { autoDispatch, getDispatchRules, updateDispatchRule } from '../../../api/workOrder.js'
import StateBlock from '../../../components/StateBlock.vue'
import StatusTag from '../../../components/StatusTag.vue'

/**
 * 企业微信自动派单（需求3，B3.3 模拟演示入口）
 *
 * 这是「预留接口 + 演示流程」而非真实对接：整条模拟链路（群消息 → 规则解析 → 建单）走的是
 * 契约 POST /work-orders/auto-dispatch，服务端只接受已解析的明文。
 *
 * TODO(扩展): 接入企业微信机器人回调时替换为真实签名校验与消息解密
 *   —— 校验 msg_signature / timestamp / nonce，用 EncodingAESKey 解密 Encrypt 字段后再解析消息体；
 *   本接口当前只接受已解析的明文（依据 shared/mock/routes/workOrder.js:328-330）
 *
 * 规则维护接口 PUT 仅 ADMIN（契约 roles:['ADMIN']），GET 未加 roles 属契约不一致（U6），
 * 前端按更严口径处理：整个「自动派单」入口只对 ADMIN 渲染，故本组件不做权限分支。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  stations: { type: Array, default: () => [] },
  defaultStationId: { type: [Number, String], default: null },
  defaultGroupName: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue', 'dispatched'])

/** 未命中任何规则时的兜底，与 workOrder.js 的 AUTO_DISPATCH_DEFAULT 逐字一致 */
const FALLBACK = { workOrderType: 4, priority: 1 }

/** 抽屉标题上的固定标识：一整片模拟区必须一眼可辨（B3.3 硬要求） */
const SIMULATE_TAG = { SIMULATE: { label: '模拟演示', type: 'warning' } }

const step = ref(1)
const submitting = ref(false)
const rulesLoading = ref(false)
const rulesError = ref(false)
const rules = ref([])
const employees = ref([])

const msgForm = reactive({ groupName: '', senderName: '企微助手', content: '', stationId: null })
const parsed = reactive({ workOrderType: FALLBACK.workOrderType, priority: FALLBACK.priority, defaultAssigneeId: null })

const editing = reactive({ id: null, keyword: '', workOrderType: null, priority: null, defaultAssigneeId: null })

const employeeName = (id) => {
  const hit = employees.value.find((item) => item.id === id)
  return hit ? hit.realName : '未指定'
}

/** 命中规则：取首个「启用且关键词命中」的规则，与服务端 autoDispatch 的查找口径一致 */
const matchedRule = computed(
  () => rules.value.find((rule) => rule.enabled && msgForm.content.includes(rule.keyword)) || null
)

const matchText = computed(() =>
  matchedRule.value
    ? `命中规则「${matchedRule.value.keyword}」→ ${WORK_ORDER_TYPE[matchedRule.value.workOrderType].label} / ${WORK_ORDER_PRIORITY[matchedRule.value.priority].label}优先级`
    : `未命中任何规则，将使用默认：${WORK_ORDER_TYPE[FALLBACK.workOrderType].label} / ${WORK_ORDER_PRIORITY[FALLBACK.priority].label}优先级`
)

const slaPreview = computed(() => {
  const hours = WORK_ORDER_SLA_HOURS[parsed.priority]
  return hours
    ? `${formatDateTime(new Date(Date.now() + hours * 3600000))}（${WORK_ORDER_PRIORITY[parsed.priority].label}优先级 ${hours} 小时）`
    : '—'
})

const enabledRuleCount = computed(() => rules.value.filter((rule) => rule.enabled).length)

async function loadRules() {
  rulesLoading.value = true
  rulesError.value = false
  try {
    rules.value = await getDispatchRules()
  } catch (e) {
    rules.value = []
    rulesError.value = true
  } finally {
    rulesLoading.value = false
  }
}

/** 规则里只存 defaultAssigneeId，展示姓名需要员工表；顺带复用给编辑下拉 */
async function loadEmployees() {
  try {
    const page = await getEmployees({ status: 1, pageNum: 1, pageSize: 100 })
    employees.value = page.list
  } catch (e) {
    employees.value = []
  }
}

/** 解析预览：本地先按规则算出候选，再允许人工覆盖（解析结果可改是 B3.3 明确要求的） */
function handleParse() {
  if (!msgForm.content.trim()) {
    ElMessage.warning('请先粘贴群消息内容')
    return
  }
  parsed.workOrderType = matchedRule.value ? matchedRule.value.workOrderType : FALLBACK.workOrderType
  parsed.priority = matchedRule.value ? matchedRule.value.priority : FALLBACK.priority
  parsed.defaultAssigneeId = matchedRule.value ? matchedRule.value.defaultAssigneeId : null
  step.value = 2
}

async function handleDispatch() {
  if (!msgForm.content.trim()) {
    ElMessage.warning('群消息内容不能为空')
    step.value = 1
    return
  }
  const assigneeName = parsed.defaultAssigneeId == null ? '未指定' : employeeName(parsed.defaultAssigneeId)
  try {
    await ElMessageBox.confirm(
      `将按「${WORK_ORDER_TYPE[parsed.workOrderType].label} / ${WORK_ORDER_PRIORITY[parsed.priority].label}优先级」生成工单，处理人：${assigneeName}。生成后不可撤销，只能关闭工单。`,
      '确认派单',
      { confirmButtonText: '确认派单', cancelButtonText: '返回修改', type: 'warning' }
    )
  } catch (e) {
    return
  }
  submitting.value = true
  try {
    const order = await autoDispatch(
      {
        groupName: msgForm.groupName.trim() || undefined,
        senderName: msgForm.senderName.trim() || undefined,
        content: msgForm.content.trim(),
        stationId: msgForm.stationId,
        // 以当前时间作为消息发送时间：与服务端「按消息时间起算 SLA」的口径一致
        sendTime: formatDateTime(new Date())
      },
      { silent: true }
    )
    ElMessage.success(`已按规则生成工单 ${order.orderNo}，处理人 ${order.assigneeName || '未指定'}`)
    emit('dispatched', order)
    close()
  } catch (e) {
    ElMessage.error((e && e.message) || '自动派单失败，请重试')
  } finally {
    submitting.value = false
  }
}

function startEdit(rule) {
  editing.id = rule.id
  editing.keyword = rule.keyword
  editing.workOrderType = rule.workOrderType
  editing.priority = rule.priority
  editing.defaultAssigneeId = rule.defaultAssigneeId
}

function cancelEdit() {
  editing.id = null
}

async function saveEdit() {
  if (!editing.keyword || !editing.keyword.trim()) {
    ElMessage.warning('关键词不能为空')
    return
  }
  try {
    const updated = await updateDispatchRule(
      editing.id,
      {
        keyword: editing.keyword.trim(),
        workOrderType: editing.workOrderType,
        priority: editing.priority,
        defaultAssigneeId: editing.defaultAssigneeId
      },
      { silent: true }
    )
    const index = rules.value.findIndex((rule) => rule.id === updated.id)
    if (index >= 0) rules.value[index] = updated
    ElMessage.success('派单规则已保存')
    editing.id = null
  } catch (e) {
    ElMessage.error((e && e.message) || '派单规则保存失败，请重试')
  }
}

/** 启用开关直接落库：它没有中间态，收进编辑表单反而多一次点击 */
async function toggleRule(rule, value) {
  try {
    const updated = await updateDispatchRule(rule.id, { enabled: value ? 1 : 0 }, { silent: true })
    const index = rules.value.findIndex((item) => item.id === updated.id)
    if (index >= 0) rules.value[index] = updated
  } catch (e) {
    ElMessage.error((e && e.message) || '规则状态保存失败，请重试')
  }
}

function reset() {
  step.value = 1
  msgForm.groupName = props.defaultGroupName || ''
  msgForm.senderName = '企微助手'
  msgForm.content = ''
  msgForm.stationId = props.defaultStationId
  parsed.workOrderType = FALLBACK.workOrderType
  parsed.priority = FALLBACK.priority
  parsed.defaultAssigneeId = null
  editing.id = null
}

function close() {
  emit('update:modelValue', false)
}

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return
    reset()
    loadRules()
    if (!employees.value.length) loadEmployees()
  }
)
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    size="min(var(--drawer-w-lg), 92vw)"
    destroy-on-close
    @update:model-value="close"
  >
    <template #header>
      <div class="drawer-title">
        <span>企业微信自动派单</span>
        <StatusTag :dict="SIMULATE_TAG" value="SIMULATE" variant="outline" />
      </div>
    </template>

    <el-steps :active="step" simple class="dispatch-steps">
      <el-step title="粘贴群消息" />
      <el-step title="确认派单" />
    </el-steps>

    <!-- ========== Step 1 模拟群消息：整片走 simulate Token，与真实表单的白底明确拉开 ========== -->
    <section v-if="step === 1" class="simulate">
      <p class="simulate__title">模拟群消息（演示数据，不会真的连接企业微信）</p>
      <el-form label-width="96px" :disabled="submitting">
        <el-form-item label="来源群名称">
          <el-input v-model.trim="msgForm.groupName" placeholder="如：城东驿站-异常件处理群" />
        </el-form-item>
        <el-form-item label="发送人">
          <el-input v-model.trim="msgForm.senderName" placeholder="如：企微助手" />
        </el-form-item>
        <el-form-item label="消息内容" required>
          <el-input
            v-model="msgForm.content"
            type="textarea"
            :rows="6"
            placeholder="粘贴一条企业微信采集群里真实出现过的消息文本，系统将按规则解析（不会真的连接企业微信）"
          />
        </el-form-item>
        <el-form-item label="归属驿站" required>
          <el-select v-model="msgForm.stationId" class="form-field" placeholder="请选择归属驿站">
            <el-option v-for="item in stations" :key="item.id" :label="item.stationName" :value="item.id" />
          </el-select>
        </el-form-item>
      </el-form>
    </section>

    <!-- ========== Step 2 解析结果预览（只读卡 + 可覆盖） ========== -->
    <section v-else class="parse-result">
      <el-descriptions :column="1" size="small" border>
        <el-descriptions-item label="命中规则">{{ matchText }}</el-descriptions-item>
        <el-descriptions-item label="解析类型">
          <el-select v-model="parsed.workOrderType" class="form-field">
            <el-option v-for="(item, key) in WORK_ORDER_TYPE" :key="key" :label="item.label" :value="Number(key)" />
          </el-select>
        </el-descriptions-item>
        <el-descriptions-item label="解析优先级">
          <el-select v-model="parsed.priority" class="form-field">
            <el-option v-for="(item, key) in WORK_ORDER_PRIORITY" :key="key" :label="item.label" :value="Number(key)" />
          </el-select>
        </el-descriptions-item>
        <el-descriptions-item label="默认处理人">
          {{ parsed.defaultAssigneeId == null ? '未指定' : employeeName(parsed.defaultAssigneeId) }}
        </el-descriptions-item>
        <el-descriptions-item label="SLA 预览">{{ slaPreview }}</el-descriptions-item>
      </el-descriptions>
      <p class="parse-result__raw">原始消息：{{ msgForm.content }}</p>
    </section>

    <!-- ========== 派单规则（同抽屉的第三个区块，折叠） ========== -->
    <el-collapse class="rules">
      <el-collapse-item :title="`派单规则（${rules.length} 条，启用 ${enabledRuleCount} 条）`" name="rules">
        <StateBlock v-if="rulesError" variant="error" title="派单规则加载失败" @action="loadRules" />
        <StateBlock
          v-else-if="!rulesLoading && !rules.length"
          variant="empty"
          title="暂无派单规则"
          description="未命中规则时将使用默认类型与优先级（其他 / 中）"
        />
        <el-table v-else v-loading="rulesLoading" :data="rules" size="small" border>
          <el-table-column label="关键词" min-width="120">
            <template #default="{ row }">
              <el-input v-if="editing.id === row.id" v-model.trim="editing.keyword" maxlength="20" />
              <span v-else>{{ row.keyword }}</span>
            </template>
          </el-table-column>
          <el-table-column label="工单类型" min-width="130">
            <template #default="{ row }">
              <el-select v-if="editing.id === row.id" v-model="editing.workOrderType" class="form-field">
                <el-option v-for="(item, key) in WORK_ORDER_TYPE" :key="key" :label="item.label" :value="Number(key)" />
              </el-select>
              <span v-else>{{ WORK_ORDER_TYPE[row.workOrderType].label }}</span>
            </template>
          </el-table-column>
          <el-table-column label="优先级" min-width="110">
            <template #default="{ row }">
              <el-select v-if="editing.id === row.id" v-model="editing.priority" class="form-field">
                <el-option
                  v-for="(item, key) in WORK_ORDER_PRIORITY"
                  :key="key"
                  :label="item.label"
                  :value="Number(key)"
                />
              </el-select>
              <span v-else>{{ WORK_ORDER_PRIORITY[row.priority].label }}</span>
            </template>
          </el-table-column>
          <el-table-column label="默认处理人" min-width="140">
            <template #default="{ row }">
              <el-select
                v-if="editing.id === row.id"
                v-model="editing.defaultAssigneeId"
                class="form-field"
                clearable
                filterable
                placeholder="未指定"
              >
                <el-option v-for="item in employees" :key="item.id" :label="item.realName" :value="item.id" />
              </el-select>
              <span v-else>{{ employeeName(row.defaultAssigneeId) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="启用" width="70" align="center">
            <template #default="{ row }">
              <el-switch
                :model-value="row.enabled"
                :aria-label="`启用规则 ${row.keyword}`"
                @change="(value) => toggleRule(row, value)"
              />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110" align="center">
            <template #default="{ row }">
              <template v-if="editing.id === row.id">
                <el-button link type="primary" @click="saveEdit">保存</el-button>
                <el-button link @click="cancelEdit">取消</el-button>
              </template>
              <el-button v-else link type="primary" :aria-label="`编辑规则 ${row.keyword}`" @click="startEdit(row)"
                >编辑</el-button
              >
            </template>
          </el-table-column>
        </el-table>
      </el-collapse-item>
    </el-collapse>

    <template #footer>
      <div class="drawer-footer">
        <el-button v-if="step === 2" @click="step = 1">返回修改</el-button>
        <el-button @click="close">取消</el-button>
        <el-button v-if="step === 1" type="primary" @click="handleParse">解析预览</el-button>
        <el-button v-else type="primary" :loading="submitting" @click="handleDispatch">确认派单</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<style scoped lang="scss">
.drawer-title {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.dispatch-steps {
  margin-bottom: var(--sp-4);
}

.form-field {
  width: 100%;
}

/* 模拟演示区：整块走 --state-simulate-*（C3-1），与"警告/错误"语义解耦 */
.simulate {
  padding: var(--sp-4);
  border: 1px dashed var(--state-simulate-border);
  border-radius: var(--r-md);
  background-color: var(--state-simulate-bg);

  &__title {
    margin: 0 0 var(--sp-4);
    color: var(--state-simulate-fg);
    font-size: var(--fs-body-strong);
    font-weight: var(--fw-medium);
  }
}

.parse-result {
  &__raw {
    margin: var(--sp-3) 0 0;
    padding: var(--sp-2) var(--sp-3);
    border-radius: var(--r-sm);
    background-color: var(--surface-sub);
    color: var(--text-2);
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    word-break: break-all;
  }
}

.rules {
  margin-top: var(--sp-4);
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--sp-2);
}
</style>
