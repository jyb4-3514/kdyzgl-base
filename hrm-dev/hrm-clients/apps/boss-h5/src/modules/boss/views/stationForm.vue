<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showSuccessToast } from 'vant'
import ActionBar from '@kdyzgl/shared/ui/ActionBar.vue'
import PageNav from '@kdyzgl/shared/ui/PageNav.vue'
import PageState from '@kdyzgl/shared/ui/PageState.vue'
import { bossConfirm } from '../components/bossConfirm.js'
import { createStation, getStationList, updateStation, updateStationStatus } from '@/api/org.js'

/**
 * 新增 / 编辑驿站表单（共用一页，按路由 params.id 判定）· 设计 ④.3
 *
 * 新增：POST /stations（不传 status，服务端固定 1；表单不出现启用开关，避免「建了就是停用」的错觉），
 * 成功后直接进详情继续建账号；编辑：PUT /stations/{id}。停用走独立端点并二次确认（影响面明确）。
 */
const route = useRoute()
const router = useRouter()
const stationId = computed(() => (route.params.id ? Number(route.params.id) : null))
const isEdit = computed(() => stationId.value != null)
const ROUTE_BASE = '/boss/station'

const loading = ref(true)
const error = ref('')
const saved = ref(null)

const form = ref({ code: '', stationName: '', contactPerson: '', contactPhone: '', address: '', remark: '' })
const saving = ref(false)
const formError = ref('')

const codeError = ref('')
const nameError = ref('')
const phoneError = ref('')

async function load() {
  if (!isEdit.value) {
    loading.value = false
    return
  }
  loading.value = true
  error.value = ''
  try {
    const list = await getStationList()
    const hit = list.find((item) => item.id === stationId.value)
    if (!hit) {
      error.value = '驿站不存在，请刷新后重试'
      return
    }
    saved.value = hit
    form.value = {
      code: hit.code || '',
      stationName: hit.stationName || '',
      contactPerson: hit.contactPerson || '',
      contactPhone: hit.contactPhone || '',
      address: hit.address || '',
      remark: hit.remark || ''
    }
  } catch (e) {
    error.value = e.message || '驿站信息加载失败'
  } finally {
    loading.value = false
  }
}

function validateCode() {
  codeError.value = /^[A-Za-z0-9_-]{2,50}$/.test(form.value.code.trim())
    ? ''
    : '驿站编号须为 2–50 位字母/数字/下划线/中划线'
  return !codeError.value
}

function validateName() {
  const len = form.value.stationName.trim().length
  nameError.value = len >= 1 && len <= 50 ? '' : '驿站名称须为 1–50 字符'
  return !nameError.value
}

function validatePhone() {
  const value = form.value.contactPhone.trim()
  phoneError.value = !value || /^1[3-9]\d{9}$/.test(value) ? '' : '联系人电话格式不正确'
  return !phoneError.value
}

async function onSubmit() {
  if (saving.value) return
  const ok = validateCode() && validateName() && validatePhone()
  if (!ok) return
  if (form.value.contactPerson.trim().length > 50) {
    formError.value = '联系人不可超过 50 字符'
    return
  }
  if (form.value.address.length > 255) {
    formError.value = '地址不可超过 255 字符'
    return
  }
  if (form.value.remark.length > 255) {
    formError.value = '备注不可超过 255 字符'
    return
  }
  formError.value = ''
  saving.value = true
  const payload = {
    code: form.value.code.trim(),
    stationName: form.value.stationName.trim(),
    contactPerson: form.value.contactPerson.trim() || null,
    contactPhone: form.value.contactPhone.trim() || null,
    address: form.value.address.trim() || null,
    remark: form.value.remark.trim() || null
  }
  try {
    if (isEdit.value) {
      await updateStation(stationId.value, payload)
      showSuccessToast('驿站信息已保存')
      // 就地刷新详情：replace 让详情页重新挂载取数，避免返回后看到旧值
      router.replace(`${ROUTE_BASE}/${stationId.value}`)
    } else {
      const result = await createStation(payload)
      showSuccessToast('驿站已创建')
      router.replace(`${ROUTE_BASE}/${result.id}`)
    }
  } catch (e) {
    // 4002 编号已存在：落到编号字段下，指向具体要改的项（契约文案「该驿站编号已被使用，请更换」）
    if (e.code === 4002) codeError.value = e.message
    else formError.value = e.message || '保存失败，请稍后重试'
  } finally {
    saving.value = false
  }
}

/** 停用/启用：独立端点 + 停用二次确认（存量员工归属与登录不受影响） */
async function onToggleStatus(next) {
  if (!saved.value || saving.value) return
  const nextStatus = next ? 1 : 0
  if (nextStatus === 0) {
    const ok = await bossConfirm({
      action: '停用驿站',
      target: saved.value.stationName,
      impact: '新增与编辑员工时不可再选择该驿站，存量员工归属与登录不受影响',
      confirmText: '确认停用'
    })
    if (!ok) return
  }
  try {
    await updateStationStatus(saved.value.id, nextStatus)
    saved.value = { ...saved.value, status: nextStatus }
    showSuccessToast(nextStatus === 1 ? '驿站已启用' : '驿站已停用')
  } catch (e) {
    formError.value = e.message || '状态更新失败'
  }
}

onMounted(load)
</script>

<template>
  <div class="station-form">
    <PageNav :title="isEdit ? '编辑驿站' : '新增驿站'" />
    <div class="page page--bar">
      <PageState :loading="loading" :error="error" :rows="4" @retry="load">
        <div class="section-title">驿站信息</div>
        <div class="card">
          <van-field
            v-model="form.code"
            label="驿站编号"
            placeholder="2–50 位字母/数字/下划线/中划线"
            maxlength="50"
            aria-label="驿站编号"
            @blur="validateCode"
          />
          <p v-if="codeError" class="field-error" role="alert">{{ codeError }}</p>

          <van-field
            v-model="form.stationName"
            label="驿站名称"
            placeholder="1–50 字符"
            maxlength="50"
            aria-label="驿站名称"
            @blur="validateName"
          />
          <p v-if="nameError" class="field-error" role="alert">{{ nameError }}</p>

          <van-field v-model="form.contactPerson" label="联系人" placeholder="可空，≤50 字符" maxlength="50" />
          <van-field
            v-model="form.contactPhone"
            type="tel"
            inputmode="numeric"
            label="联系电话"
            placeholder="可空，11 位手机号"
            maxlength="11"
            aria-label="联系人电话"
            @blur="validatePhone"
          />
          <p v-if="phoneError" class="field-error" role="alert">{{ phoneError }}</p>

          <van-field
            v-model="form.address"
            type="textarea"
            rows="2"
            maxlength="255"
            show-word-limit
            label="地址"
            placeholder="可空，≤255 字符"
          />
          <van-field
            v-model="form.remark"
            type="textarea"
            rows="2"
            maxlength="255"
            show-word-limit
            label="备注"
            placeholder="可空，≤255 字符"
          />

          <!-- 状态开关仅编辑态出现（新增固定启用，服务端定 status=1） -->
          <div v-if="isEdit" class="switch-row">
            <div class="switch-row__text">
              <p class="switch-row__label">启用驿站</p>
              <p class="switch-row__hint">停用后新增/编辑员工不可再归属本驿站</p>
            </div>
            <van-switch
              :model-value="saved && saved.status === 1"
              aria-label="启用驿站"
              @update:model-value="onToggleStatus"
            />
          </div>
        </div>

        <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>
      </PageState>
    </div>

    <ActionBar
      :actions="[{ key: 'save', label: isEdit ? '保存' : '创建驿站', plain: false, loading: saving }]"
      :submitting="saving"
      @select="onSubmit"
    />
  </div>
</template>

<style scoped>
.field-error {
  margin: 0;
  padding: 0 var(--sp-4) var(--sp-3);
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--color-danger);
}

.form-error {
  margin: var(--sp-3) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--color-danger);
}

.switch-row {
  display: flex;
  gap: var(--sp-3);
  align-items: center;
  justify-content: space-between;
  min-height: var(--touch-min);
  padding-top: var(--sp-2);
  border-top: 1px solid var(--border-line);
}

.switch-row__text {
  flex: 1;
  min-width: 0;
}

.switch-row__label {
  margin: 0;
  font-size: var(--fs-body-strong);
  font-weight: var(--fw-medium);
  color: var(--text-1);
}

.switch-row__hint {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}
</style>
