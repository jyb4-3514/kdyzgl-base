<script setup>
import { computed, onMounted, ref } from 'vue'
import { showConfirmDialog, showSuccessToast } from 'vant'
import ActionBar from '../../components/ActionBar.vue'
import PageNav from '../../components/PageNav.vue'
import PageState from '../../components/PageState.vue'
import { getLeaveSettings, saveLeaveSettings } from '../../api/index.js'

/**
 * P5 请假扣款设置（ADMIN · 全局单开关，D5）
 *
 * 这一页最不能让人猜：开关直接决定「请假扣不扣钱」，所以正反两种口径的公式都写在界面上，
 * 而不是只给一个开关名。语义以数据层为准（leaveStore.isLeaveDeductEnabled）：
 *   开启 = 请假按缺勤计（缺勤天数不减已批请假天数）→ 扣款
 *   关闭（默认）= 请假不计缺勤（缺勤天数减掉已批请假天数）→ 不扣款
 *
 * 切换先二次确认再落到草稿，最后由底部「保存」提交：算薪口径的变更不能靠一次误触生效。
 */
const saved = ref(false)
const draft = ref(false)
const loading = ref(true)
const error = ref('')
const saving = ref(false)

const dirty = computed(() => draft.value !== saved.value)

const barNote = computed(() => {
  if (loading.value || error.value) return ''
  return dirty.value ? '开关已改动，点「保存」后生效' : '当前口径已保存'
})

const currentText = computed(() => (draft.value ? '请假按缺勤计（扣款）' : '请假不计缺勤（不扣款）'))

async function load() {
  loading.value = true
  error.value = ''
  try {
    const settings = await getLeaveSettings()
    saved.value = settings.leaveDeductEnabled === true
    draft.value = saved.value
  } catch (e) {
    error.value = e.message || '扣款设置加载失败'
  } finally {
    loading.value = false
  }
}

/** 取消时不写 draft：开关视觉保持原值，不出现「已经切了但没保存」的假象 */
async function onToggle(next) {
  if (saving.value || next === draft.value) return
  try {
    await showConfirmDialog({
      title: next ? '开启请假扣款' : '关闭请假扣款',
      message: next
        ? '开启后：请假当天按缺勤计，会扣工资（缺勤天数 = 排班天数 − 出勤天数）。涉及算薪口径，确认切换？'
        : '关闭后（默认口径）：请假不计缺勤，不扣工资（缺勤天数 = 排班天数 − 出勤天数 − 已批请假天数）。确认切换？',
      confirmButtonText: '确认切换',
      cancelButtonText: '再想想'
    })
  } catch (e) {
    return // 用户取消
  }
  draft.value = next
}

async function onSave() {
  if (!dirty.value || saving.value) return
  saving.value = true
  try {
    const result = await saveLeaveSettings({ leaveDeductEnabled: draft.value })
    saved.value = result.leaveDeductEnabled === true
    draft.value = saved.value
    showSuccessToast('扣款口径已保存')
  } catch (e) {
    // 保存失败由 http 层统一提示；不改动 draft，用户可原样重试
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="leave-settings">
    <PageNav title="请假扣款设置" />
    <div class="page page--bar">
      <PageState :loading="loading" :error="error" :rows="3" @retry="load">
        <div class="section-title">全局开关</div>
        <div class="card">
          <div class="switch-row">
            <div class="switch-row__text">
              <p class="switch-row__label">请假按缺勤计（扣款）</p>
              <p class="switch-row__hint">当前口径：{{ currentText }}</p>
            </div>
            <van-switch
              :model-value="draft"
              :loading="saving"
              aria-label="请假按缺勤计（扣款）"
              @update:model-value="onToggle"
            />
          </div>
        </div>

        <div class="section-title">这个开关影响什么</div>
        <div class="card">
          <p class="rule rule--on">
            开启：缺勤天数 = 排班天数 − 出勤天数。已批准的请假按缺勤计，缺勤扣款与全勤奖都会受影响。
          </p>
          <p class="rule">
            关闭（默认）：缺勤天数 = 排班天数 − 出勤天数 − 已批请假天数。请假不扣钱，考勤仍记为「请假」。
          </p>
          <p class="rule rule--note">这是全局唯一口径：对所有驿站、所有假别统一生效，不能按驿站或按假别单独设置。</p>
          <p class="rule rule--note">涉及算薪口径，切换前请先确认当前账期的工资单状态，避免中途改口径。</p>
        </div>
      </PageState>
    </div>

    <ActionBar
      :actions="[{ key: 'save', label: '保存设置', plain: false, loading: saving, disabled: !dirty }]"
      :note="barNote"
      :submitting="saving"
      @select="onSave"
    />
  </div>
</template>

<style scoped>
.switch-row {
  display: flex;
  gap: var(--sp-3);
  align-items: center;
  justify-content: space-between;
  min-height: 44px;
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

.rule {
  margin: 0;
  font-size: var(--fs-body);
  line-height: var(--lh-body);
  color: var(--text-2);
}

.rule + .rule {
  margin-top: var(--sp-3);
}

.rule--on {
  color: var(--text-1);
}

/* 浅底块内一律用 --text-2：--text-3 在浅灰底上只有 4.23:1（§5.1 对比度禁区） */
.rule--note {
  padding: var(--sp-2) var(--sp-3);
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-2);
  background: var(--surface-subtle);
  border-radius: var(--r-sm);
}
</style>
