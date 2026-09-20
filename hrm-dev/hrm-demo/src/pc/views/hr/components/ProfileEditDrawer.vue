<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getHrProfile, updateHrProfile } from '../../../api/hr.js'
import { CONTRACT_TYPE, EDUCATION } from '@/shared/constants/dict.js'
import StateBlock from '../../../components/StateBlock.vue'
import StatusTag from '../../../components/StatusTag.vue'

/**
 * 人事档案编辑抽屉（需求8，B8.3 Tab1）
 *
 * 银行卡的处理是本组件的关键：服务端出参是脱敏值，界面上**只读展示脱敏串**，
 * 只有用户主动点「修改银行卡号」后才出现空白输入框——绝不把完整卡号回填到输入框里。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  employeeId: { type: Number, default: null }
})

const emit = defineEmits(['update:modelValue', 'saved'])

const PHONE_RE = /^1[3-9]\d{9}$/
const BANK_RE = /^\d{12,25}$/
const RELATIONS = ['配偶', '父母', '子女', '兄弟姐妹', '其他']
/** 离职标记：人事档案的 leaveDate 非空即已走完离职流程（契约口径，比 employee.status 更准） */
const LEAVE_DICT = { LEFT: { label: '已离职', type: 'info' } }

const loading = ref(false)
const error = ref(false)
const saving = ref(false)
const profile = ref(null)
const bankEditing = ref(false)

const form = ref({
  education: '',
  contractType: '',
  contractStart: '',
  contractEnd: '',
  probationMonths: 3,
  probationEnd: '',
  regularDate: '',
  socialSecurityBase: null,
  emergencyContactName: '',
  emergencyContactPhone: '',
  emergencyContactRelation: '',
  bankName: '',
  bankAccount: ''
})

async function load() {
  if (!props.employeeId) return
  loading.value = true
  error.value = false
  bankEditing.value = false
  try {
    const detail = await getHrProfile(props.employeeId)
    profile.value = detail
    form.value = {
      education: detail.education || '',
      contractType: detail.contractType || '',
      contractStart: detail.contractStart || '',
      contractEnd: detail.contractEnd || '',
      probationMonths:
        detail.probationMonths === null || detail.probationMonths === undefined ? 0 : detail.probationMonths,
      probationEnd: detail.probationEnd || '',
      regularDate: detail.regularDate || '',
      socialSecurityBase: detail.socialSecurityBase,
      emergencyContactName: detail.emergencyContactName || '',
      emergencyContactPhone: '',
      emergencyContactRelation: detail.emergencyContactRelation || '',
      bankName: detail.bankName || '',
      bankAccount: ''
    }
  } catch (e) {
    error.value = true
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.modelValue, props.employeeId],
  ([visible]) => {
    if (visible) load()
  },
  { immediate: true }
)

const resigned = computed(() => !!(profile.value && profile.value.leaveDate))
const readOnly = computed(() => resigned.value)

/** 前端校验与服务端同口径：先把能本地判掉的挡掉，避免提交后才收到 400 */
const formError = computed(() => {
  if (form.value.contractStart && form.value.contractEnd && form.value.contractEnd < form.value.contractStart)
    return '合同到期日不能早于生效日'
  const months = Number(form.value.probationMonths)
  if (!Number.isInteger(months) || months < 0 || months > 12) return '试用期月数须为 0-12 的整数'
  if (
    form.value.socialSecurityBase !== null &&
    form.value.socialSecurityBase !== '' &&
    !(Number(form.value.socialSecurityBase) >= 0)
  )
    return '社保基数须不小于 0'
  if (
    form.value.emergencyContactName &&
    (form.value.emergencyContactName.trim().length < 2 || form.value.emergencyContactName.trim().length > 20)
  )
    return '紧急联系人姓名长度须为 2-20'
  // 手机号只在用户填写时校验：脱敏值不回填，留空表示「不修改」
  if (form.value.emergencyContactPhone && !PHONE_RE.test(form.value.emergencyContactPhone))
    return '紧急联系人手机号格式不正确'
  if (form.value.bankName && (form.value.bankName.trim().length < 2 || form.value.bankName.trim().length > 50))
    return '开户行长度须为 2-50'
  if (bankEditing.value && form.value.bankAccount && !BANK_RE.test(form.value.bankAccount.replace(/\s/g, '')))
    return '银行卡号须为 12-25 位数字'
  return ''
})

const canSubmit = computed(() => !readOnly.value && !formError.value)

async function handleSubmit() {
  if (!canSubmit.value) {
    ElMessage.warning(formError.value)
    return
  }
  saving.value = true
  try {
    // 只提交用户真正动过的敏感字段：脱敏串（含 * 号）绝不回传，否则会把卡号写成掩码
    const payload = {
      education: form.value.education || null,
      contractType: form.value.contractType || null,
      contractStart: form.value.contractStart || null,
      contractEnd: form.value.contractEnd || null,
      probationMonths: Number(form.value.probationMonths),
      probationEnd: form.value.probationEnd || null,
      regularDate: form.value.regularDate || null,
      socialSecurityBase: form.value.socialSecurityBase === '' ? null : form.value.socialSecurityBase,
      emergencyContactName: form.value.emergencyContactName || null,
      emergencyContactRelation: form.value.emergencyContactRelation || null,
      bankName: form.value.bankName || null
    }
    if (form.value.emergencyContactPhone) payload.emergencyContactPhone = form.value.emergencyContactPhone
    if (bankEditing.value && form.value.bankAccount) payload.bankAccount = form.value.bankAccount.replace(/\s/g, '')

    await updateHrProfile(props.employeeId, payload)
    ElMessage.success('人事档案已保存')
    emit('saved')
    emit('update:modelValue', false)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    title="人事档案"
    size="min(var(--drawer-w), 92vw)"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <StateBlock v-if="error" variant="error" title="人事档案加载失败" @action="load" />

    <div v-else v-loading="loading" class="hr-profile">
      <template v-if="profile">
        <el-alert
          v-if="resigned"
          class="hr-profile__alert"
          type="warning"
          show-icon
          :closable="false"
          title="员工已离职，档案为只读"
          description="已离职员工的档案与薪资均不可再调整。"
        />

        <div class="hr-profile__head">
          <span class="hr-profile__name">{{ profile.employeeName }}</span>
          <StatusTag v-if="profile.leaveDate" :dict="LEAVE_DICT" value="LEFT" variant="outline" />
          <span class="hr-profile__meta"
            >{{ profile.stationName || '—' }} · {{ profile.deptName || '—' }} · 入职
            {{ profile.entryDate || '—' }}</span
          >
        </div>

        <el-form label-position="top" :disabled="readOnly">
          <h4 class="hr-profile__title">合同与试用期</h4>
          <div class="hr-profile__grid">
            <el-form-item label="学历">
              <el-select v-model="form.education" clearable style="width: 100%">
                <el-option v-for="(item, key) in EDUCATION" :key="key" :value="key" :label="item.label" />
              </el-select>
            </el-form-item>
            <el-form-item label="合同类型">
              <el-select v-model="form.contractType" clearable style="width: 100%">
                <el-option v-for="(item, key) in CONTRACT_TYPE" :key="key" :value="key" :label="item.label" />
              </el-select>
            </el-form-item>
            <el-form-item label="合同生效日">
              <el-date-picker
                v-model="form.contractStart"
                type="date"
                value-format="YYYY-MM-DD"
                clearable
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item label="合同到期日">
              <el-date-picker
                v-model="form.contractEnd"
                type="date"
                value-format="YYYY-MM-DD"
                clearable
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item label="试用期（月）">
              <el-input v-model="form.probationMonths" type="number" :min="0" />
            </el-form-item>
            <el-form-item label="转正日期">
              <el-date-picker
                v-model="form.regularDate"
                type="date"
                value-format="YYYY-MM-DD"
                clearable
                style="width: 100%"
              />
            </el-form-item>
          </div>

          <h4 class="hr-profile__title">社保与联系信息</h4>
          <div class="hr-profile__grid">
            <el-form-item label="社保基数（元）">
              <el-input v-model="form.socialSecurityBase" type="number" :min="0" placeholder="未设置" />
            </el-form-item>
            <el-form-item label="紧急联系人">
              <el-input v-model="form.emergencyContactName" maxlength="20" />
            </el-form-item>
            <el-form-item label="联系人关系">
              <el-select v-model="form.emergencyContactRelation" clearable style="width: 100%">
                <el-option v-for="item in RELATIONS" :key="item" :value="item" :label="item" />
              </el-select>
            </el-form-item>
            <el-form-item label="联系人手机号">
              <!-- 脱敏值不回填输入框，改用 placeholder 提示当前值；留空即不修改 -->
              <el-input
                v-model="form.emergencyContactPhone"
                maxlength="11"
                :placeholder="profile.emergencyContactPhone || '未设置'"
              />
            </el-form-item>
          </div>

          <h4 class="hr-profile__title">银行信息</h4>
          <div class="hr-profile__grid">
            <el-form-item label="开户行">
              <el-input v-model="form.bankName" maxlength="50" />
            </el-form-item>
            <el-form-item label="银行卡号">
              <div v-if="bankEditing" class="hr-profile__bank">
                <el-input v-model="form.bankAccount" maxlength="25" placeholder="输入新的银行卡号（12-25 位数字）" />
                <el-button link @click="bankEditing = false">取消修改</el-button>
              </div>
              <div v-else class="hr-profile__bank">
                <span class="hr-profile__masked">{{ profile.bankAccount || '未设置' }}</span>
                <el-button link type="primary" :disabled="readOnly" @click="bankEditing = true">修改银行卡号</el-button>
              </div>
            </el-form-item>
          </div>
          <p class="hr-profile__hint">银行卡号与联系人手机号以脱敏值展示，保存时只提交本次新填写的值。</p>
        </el-form>

        <p class="hr-profile__hint">档案最后更新：{{ profile.updateTime }}</p>
      </template>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
      <el-button
        v-if="!readOnly"
        type="primary"
        :loading="saving"
        :disabled="!canSubmit"
        :title="formError || '保存人事档案'"
        @click="handleSubmit"
      >
        保存档案
      </el-button>
    </template>
  </el-drawer>
</template>

<style scoped lang="scss">
.hr-profile {
  &__alert {
    margin-bottom: var(--sp-4);
  }

  &__head {
    display: flex;
    align-items: baseline;
    gap: var(--sp-2);
    margin-bottom: var(--sp-4);
  }

  &__name {
    font-size: var(--fs-h2);
    font-weight: var(--fw-semibold);
    color: var(--text-1);
  }

  &__meta {
    font-size: var(--fs-caption);
    color: var(--text-3);
  }

  &__title {
    margin: var(--sp-4) 0 var(--sp-3);
    font-size: var(--fs-body-strong);
    font-weight: var(--fw-semibold);
    color: var(--text-1);
  }

  &__grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 0 var(--sp-3);

    @media (max-width: 992px) {
      grid-template-columns: 1fr;
    }
  }

  &__bank {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
    width: 100%;
  }

  &__masked {
    flex: 1;
    font-variant-numeric: tabular-nums;
    color: var(--text-2);
  }

  &__hint {
    margin: var(--sp-1) 0 0;
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    color: var(--text-3);
  }
}
</style>
