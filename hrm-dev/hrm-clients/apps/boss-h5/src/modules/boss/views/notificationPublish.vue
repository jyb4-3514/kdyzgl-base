<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showFailToast, showSuccessToast } from 'vant'
import ActionBar from '@kdyzgl/shared/ui/ActionBar.vue'
import PageNav from '@kdyzgl/shared/ui/PageNav.vue'
import StationPicker from '@kdyzgl/shared/ui/StationPicker.vue'
import { publishNotification } from '@/api/notification.js'
import { getEmployees, getStationList } from '@/api/org.js'
import { NOTIFICATION_TYPE, PUBLISH_SCOPE } from '@kdyzgl/shared/constants/dict.js'
import { DEMO_CODE } from '@kdyzgl/shared/constants/errorCode.js'

/**
 * B12 发布通知（ADMIN）
 *
 * 为什么用整页而不是底部弹层（B4.6）：表单有两段（内容 + 范围）且带实时人数预览，
 * 弹层在 375px 下要么盖住键盘要么把预览挤出可视区，全屏页滚动更稳。
 *
 * 为什么范围只留「全员 / 指定驿站」：移动端多选员工（可搜索 + 多选标签）在窄屏上是负体验，
 * 该场景留给 PC 的 PublishDrawer，移动端不重复实现。
 *
 * 两步只有两步：第 1 步内容、第 2 步范围，底部固定栏给唯一主操作（发布），不产生第三次跳转。
 */
const router = useRouter()

const form = reactive({ type: 4, title: '', content: '', scope: 'ALL', stationId: null })
const publishing = ref(false)

const stations = ref([])
const stationsLoading = ref(true)
const stationsError = ref('')
const showStation = ref(false)

/** 接收人数量：null 表示尚未算出，0 是有效结论（该范围下没人可发） */
const count = ref(null)
const countLoading = ref(false)
const countError = ref('')

const typeOptions = computed(() =>
  Object.entries(NOTIFICATION_TYPE).map(([value, item]) => ({ value: Number(value), label: item.label }))
)
/** 移动端只支持两种范围：多选员工是 PC 的能力（B4.6），字典里仍保留 EMPLOYEE 以便与契约一致 */
const scopeOptions = computed(() => ['ALL', 'STATION'].map((value) => ({ value, label: PUBLISH_SCOPE[value].label })))

const currentStationName = computed(() => {
  const hit = stations.value.find((item) => item.id === form.stationId)
  return hit ? hit.stationName : '请选择驿站'
})

const countText = computed(() => {
  if (countLoading.value) return '计算中…'
  if (countError.value) return '人数计算失败'
  if (count.value == null) return '-'
  return `${count.value} 名在职员工`
})

/** 范围二级未选：这是「发布」不可用的主要原因，单独算出来用于禁用说明 */
const scopeIncomplete = computed(() => form.scope === 'STATION' && form.stationId == null)
const canSubmit = computed(
  () => !!form.title.trim() && !!form.content.trim() && !scopeIncomplete.value && count.value > 0 && !publishing.value
)

const barNote = computed(() => {
  if (!form.title.trim() || !form.content.trim()) return '标题与正文为必填，填完才能发布'
  if (scopeIncomplete.value) return '已选择「指定驿站」，请先选择要发送的驿站'
  if (count.value === 0) return '该范围下暂无可接收的在职员工，无法发布'
  if (count.value == null) return '接收人数尚未算出，请稍候'
  return ''
})

/**
 * 人数预览：直接问 /employees 的在职总数（statistics 口径与发布接口一致，都只算在职启用账号），
 * 不在前端拿员工列表自己数，避免分页上限把人数数少。
 */
async function loadCount() {
  if (scopeIncomplete.value) {
    count.value = null
    countError.value = ''
    return
  }
  countLoading.value = true
  countError.value = ''
  try {
    const page = await getEmployees({
      status: 1,
      stationId: form.scope === 'STATION' ? form.stationId : '',
      pageNum: 1,
      pageSize: 1
    })
    count.value = page.total
  } catch (e) {
    count.value = null
    countError.value = e.message || '人数计算失败'
  } finally {
    countLoading.value = false
  }
}

async function loadStations() {
  stationsLoading.value = true
  stationsError.value = ''
  try {
    stations.value = await getStationList()
  } catch (e) {
    stations.value = []
    stationsError.value = e.message || '驿站列表加载失败'
  } finally {
    stationsLoading.value = false
  }
}

function pickScope(value) {
  if (publishing.value || form.scope === value) return
  form.scope = value
  if (value === 'STATION' && form.stationId == null && stations.value.length) form.stationId = stations.value[0].id
}

function pickStation(id) {
  form.stationId = id
  loadCount()
}

/** 范围或驿站变了就重算人数：预览必须始终对应当前选择，否则用户会按旧数字做决定 */
watch(() => [form.scope, form.stationId], loadCount)

async function onPublish() {
  if (!canSubmit.value) return
  // 影响面大到「一次发全站」的量级才拦一道；小范围通知每次都弹确认会让高频操作变烦（B4.4）
  if (count.value > 20) {
    try {
      await showConfirmDialog({
        title: '发布通知',
        message: `本次将发布给 ${count.value} 名员工，发布后不可撤回。`,
        confirmButtonText: '确认发布',
        cancelButtonText: '再想想'
      })
    } catch (e) {
      return // 用户取消
    }
  }
  publishing.value = true
  try {
    const result = await publishNotification({
      type: form.type,
      title: form.title.trim(),
      content: form.content.trim(),
      scope: form.scope,
      stationId: form.scope === 'STATION' ? form.stationId : undefined
    })
    showSuccessToast(`已发布给 ${result.count} 名员工`)
    router.back()
  } catch (e) {
    // 9002 的默认文案只说「参数不合法」，这里补上「该做什么」才算有用
    showFailToast(
      e.code === DEMO_CODE.NOTIFICATION_PUBLISH_SCOPE_INVALID
        ? '发布范围参数不合法：请选择至少一名在职员工'
        : e.message || '发布失败'
    )
  } finally {
    publishing.value = false
  }
}

onMounted(async () => {
  await loadStations()
  await loadCount()
})
</script>

<template>
  <div class="publish-page">
    <PageNav title="发布通知" />
    <div class="page page--bar">
      <div class="section-title">第 1 步 · 填写内容</div>

      <div class="card">
        <p class="field-label">通知类型</p>
        <div class="choice-grid">
          <button
            v-for="item in typeOptions"
            :key="item.value"
            type="button"
            class="choice"
            :class="{ 'choice--active': form.type === item.value }"
            :aria-pressed="form.type === item.value"
            :disabled="publishing"
            @click="form.type = item.value"
          >
            {{ item.label }}
            <van-icon v-if="form.type === item.value" name="success" class="choice__check" aria-hidden="true" />
          </button>
        </div>
      </div>

      <div class="card form-card">
        <van-form>
          <van-field
            v-model="form.title"
            name="title"
            label="标题"
            placeholder="一句话说明这条通知事项"
            maxlength="100"
            show-word-limit
            :disabled="publishing"
          />
          <van-field
            v-model="form.content"
            name="content"
            label="正文"
            type="textarea"
            rows="5"
            maxlength="500"
            show-word-limit
            placeholder="写清时间、影响范围与需要员工做的事"
            :disabled="publishing"
          />
        </van-form>
      </div>

      <div class="section-title">第 2 步 · 选择范围</div>

      <div class="card">
        <div class="choice-grid">
          <button
            v-for="item in scopeOptions"
            :key="item.value"
            type="button"
            class="choice"
            :class="{ 'choice--active': form.scope === item.value }"
            :aria-pressed="form.scope === item.value"
            :disabled="publishing || (item.value === 'STATION' && !stationsLoading && !stations.length)"
            :title="
              item.value === 'STATION' && !stationsLoading && !stations.length ? '驿站列表为空，无法按驿站发布' : ''
            "
            @click="pickScope(item.value)"
          >
            {{ item.label }}
            <van-icon v-if="form.scope === item.value" name="success" class="choice__check" aria-hidden="true" />
          </button>
        </div>

        <div v-if="form.scope === 'STATION'" class="station-row">
          <div v-if="stationsLoading" class="skeleton-block station-skeleton" />

          <div v-else-if="stationsError" class="station-error" role="alert">
            <p class="field-hint">{{ stationsError }}</p>
            <van-button size="small" plain type="primary" :disabled="publishing" @click="loadStations"
              >重新加载</van-button
            >
          </div>

          <p v-else-if="!stations.length" class="field-hint">暂无可选驿站，请先在 PC 端维护驿站</p>

          <van-button v-else block plain type="primary" :disabled="publishing" @click="showStation = true">
            {{ currentStationName }}
          </van-button>
        </div>
      </div>

      <!-- 接收人预览：范围一变就重算，发布是否二次确认也以此数为准 -->
      <div class="card preview">
        <p class="field-label">接收人预览</p>
        <p class="preview__value tabular-nums">{{ countText }}</p>
        <p v-if="countError" class="field-hint">{{ countError }}，请稍后重试</p>
        <p v-else class="field-hint">
          仅统计在职且启用的员工；{{ form.scope === 'ALL' ? '全员范围不展开名单，只给计数' : '按所选驿站在职员工统计' }}
        </p>
        <p class="field-hint">公告类通知不带业务跳转，员工在「通知」页读取</p>
      </div>
    </div>

    <ActionBar
      :actions="[{ key: 'publish', label: '发布通知', plain: false, loading: publishing, disabled: !canSubmit }]"
      :note="barNote"
      :submitting="publishing"
      @select="onPublish"
    />

    <StationPicker
      v-model:show="showStation"
      :stations="stations"
      :model-value="form.stationId"
      :allow-all="false"
      :loading="stationsLoading"
      :error="stationsError"
      title="选择发布范围驿站"
      empty-text="暂无可选驿站，请先在 PC 端维护驿站"
      @retry="loadStations"
      @select="pickStation"
    />
  </div>
</template>

<style scoped>
.publish-page {
  padding-bottom: var(--sp-2);
}

.form-card {
  --van-cell-horizontal-padding: 0px;
}

.field-label {
  margin: 0;
  font-size: var(--fs-body);
  font-weight: var(--fw-medium);
  color: var(--text-1);
}

.field-hint {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

.choice-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--sp-3);
  margin-top: var(--sp-2);
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

.choice:disabled {
  color: var(--text-disabled);
  border-color: var(--border-line);
}

/* 勾选图标：选中态不只靠颜色（SC 1.4.1） */
.choice__check {
  position: absolute;
  top: var(--sp-1);
  right: var(--sp-1);
  font-size: 12px;
  color: var(--color-primary);
}

.station-row {
  margin-top: var(--sp-3);
}

.station-skeleton {
  height: 44px;
}

.station-error {
  display: flex;
  gap: var(--sp-3);
  align-items: center;
  justify-content: space-between;
}

.preview {
  margin-top: var(--sp-3);
}

.preview__value {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-num-md);
  font-weight: var(--fw-semibold);
  line-height: var(--lh-num-md);
  color: var(--text-1);
}
</style>
