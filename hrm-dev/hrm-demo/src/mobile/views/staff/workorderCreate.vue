<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast, showToast } from 'vant'
import ActionBar from '../../components/ActionBar.vue'
import PageNav from '../../components/PageNav.vue'
import { createWorkOrder } from '../../api/index.js'
import { WORK_ORDER_PRIORITY, WORK_ORDER_SLA_HOURS, WORK_ORDER_TYPE } from '@/shared/constants/dict.js'
import { useAuthStore } from '../../stores/auth.js'

/**
 * S6 新建工单
 * 类型/优先级改卡片式单选：原来 4 个 van-radio 横排，375pt 下「包裹异常/设备故障/客户投诉/其他」
 * 会挤成两行且每格极窄，现场极易误触（S6）。
 * 校验改字段级：原来只有一个全局 errorMsg，用户不知道错在哪个字段。
 */
const router = useRouter()
const auth = useAuthStore()

const form = reactive({
  type: 1,
  priority: 1,
  title: '',
  content: '',
  waybillNo: ''
})
const formRef = ref(null)
const submitting = ref(false)

const typeOptions = computed(() =>
  Object.entries(WORK_ORDER_TYPE).map(([value, item]) => ({ value: Number(value), label: item.label }))
)
const priorityOptions = computed(() =>
  Object.entries(WORK_ORDER_PRIORITY).map(([value, item]) => ({ value: Number(value), label: item.label }))
)

/** SLA 口径直接取字典同一份常量，避免文案与后端计算漂移 */
const slaHint = computed(() =>
  Object.keys(WORK_ORDER_SLA_HOURS)
    .map((key) => `${WORK_ORDER_PRIORITY[key].label} ${WORK_ORDER_SLA_HOURS[key]}h`)
    .join(' / ')
)

async function onSubmit() {
  if (submitting.value) return
  try {
    await formRef.value.validate()
  } catch (e) {
    return // 字段级错误已由 van-field 渲染
  }
  submitting.value = true
  try {
    const data = await createWorkOrder({
      type: form.type,
      priority: form.priority,
      title: form.title.trim(),
      content: form.content.trim(),
      waybillNo: form.waybillNo.trim() || undefined
    })
    showSuccessToast('工单已创建')
    router.replace({ path: '/staff/workorder', query: { highlight: data.id } })
  } catch (e) {
    showToast(e.message || '提交失败')
  } finally {
    submitting.value = false
  }
}

/** 图片上传占位：三期未定义附件接口，先保留入口不动数据 */
function onPickImage() {
  // TODO(扩展): 三期补充附件上传接口后，改为读取本地图片并上传，成功后回填 handle_log 的图片列表
  showToast('附件上传接口待三期定义，演示暂不接入')
}
</script>

<template>
  <div class="create-page">
    <PageNav title="新建工单" />
    <div class="page page--bar">
      <!-- 一致性提示：上报人 / 归属驿站由登录态决定，只读展示避免用户以为可改 -->
      <div class="card">
        <van-cell-group>
          <van-cell title="上报人" :value="auth.user.realName" />
          <van-cell title="归属驿站" :value="auth.user.stationName || '-'" />
        </van-cell-group>
      </div>

      <div class="section-title">工单类型</div>
      <div class="card">
        <div class="choice-grid">
          <button
            v-for="item in typeOptions"
            :key="item.value"
            type="button"
            class="choice"
            :class="{ 'choice--active': form.type === item.value }"
            :aria-pressed="form.type === item.value"
            @click="form.type = item.value"
          >
            {{ item.label }}
            <van-icon v-if="form.type === item.value" name="success" class="choice__check" aria-hidden="true" />
          </button>
        </div>
      </div>

      <div class="section-title">
        优先级<span class="section-title__extra">SLA：{{ slaHint }}</span>
      </div>
      <div class="card">
        <div class="choice-grid choice-grid--3">
          <button
            v-for="item in priorityOptions"
            :key="item.value"
            type="button"
            class="choice"
            :class="{ 'choice--active': form.priority === item.value }"
            :aria-pressed="form.priority === item.value"
            @click="form.priority = item.value"
          >
            {{ item.label }}
            <van-icon v-if="form.priority === item.value" name="success" class="choice__check" aria-hidden="true" />
          </button>
        </div>
      </div>

      <div class="section-title">工单内容</div>
      <div class="card form-card">
        <van-form ref="formRef">
          <van-field
            v-model="form.title"
            name="title"
            label="标题"
            placeholder="一句话说明问题"
            maxlength="100"
            show-word-limit
            :rules="[{ required: true, message: '请填写工单标题' }]"
          />
          <van-field
            v-model="form.content"
            name="content"
            label="描述"
            type="textarea"
            rows="4"
            maxlength="500"
            show-word-limit
            placeholder="补充现场情况、客户诉求等"
          />
          <!-- 与 PC 端同一字段名（「关联运单号」）：两端文案对齐，避免用户以为填的不是同一个东西 -->
          <van-field
            v-model="form.waybillNo"
            name="waybillNo"
            label="关联运单号"
            placeholder="选填，关联包裹便于追溯"
          />
        </van-form>
        <button type="button" class="upload" @click="onPickImage">
          <van-icon name="photograph" aria-hidden="true" />
          <span>上传现场照片（占位）</span>
        </button>
      </div>

      <p class="tip">
        提交后状态为「待处理」，SLA 截止时间按优先级自动计算；上报人显示为当前登录身份的用户名（Demo 数据）
      </p>
    </div>

    <ActionBar
      :actions="[{ key: 'submit', label: '提交工单', plain: false, loading: submitting }]"
      :submitting="submitting"
      @select="onSubmit"
    />
  </div>
</template>

<style scoped>
.card {
  margin-top: var(--sp-3);
}

/* 字段自带 16px 左右内边距 + 卡片 16px 会成 32px，统一收敛到卡片内边距 */
.form-card {
  --van-cell-horizontal-padding: 0px;
}

.choice-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--sp-3);
}

.choice-grid--3 {
  grid-template-columns: repeat(3, 1fr);
}

.choice {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 48px;
  font-size: var(--fs-body);
  color: var(--text-2);
  background: var(--surface-card);

  /* 可交互控件边界需 ≥3:1（SC 1.4.11），Vant 默认描边约 1.4:1 */
  border: 1px solid var(--border-control);
  border-radius: var(--r-sm);
}

.choice--active {
  color: var(--color-primary);
  background: var(--color-primary-surface);
  border-color: var(--color-primary-icon);
}

/* 勾选图标：选中态不只靠颜色（SC 1.4.1） */
.choice__check {
  position: absolute;
  top: var(--sp-1);
  right: var(--sp-1);
  font-size: 12px;
  color: var(--color-primary);
}

.upload {
  display: flex;
  gap: var(--sp-1);
  align-items: center;
  justify-content: center;
  min-height: 72px;
  margin-top: var(--sp-3);
  font-size: var(--fs-caption);
  color: var(--text-3);
  background: var(--surface-subtle);
  border: 1px dashed var(--border-control);
  border-radius: var(--r-sm);
}
</style>
