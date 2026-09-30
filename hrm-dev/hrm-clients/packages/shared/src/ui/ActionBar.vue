<script setup>
import { computed, ref } from 'vue'

/**
 * 详情页固定底部操作栏（C-M8）
 * 为什么必须固定：工单/包裹详情内容长（信息 8 行 + 描述 + 时间线），
 * 关键操作排在内容流末尾时一线员工得先滑到底才能操作（现状 P31，本轮最高优先级修复）。
 * 三种形态：single 单主操作 / dual 主 + 次 / multi 主 + 「更多」弹层（按钮不堆叠）。
 * 配套：使用本组件的页面根容器加 .page--bar，否则末元素会被固定栏盖住。
 *
 * 两种排布：默认固定悬浮（工单/包裹类内容长的详情页，操作随时可达）；
 * inline=true 时改随内容滚动（详情页把按钮做进页面内容里，滚到底即可见、不遮挡），
 * 由页面按业务选择，二者共用同一套动作/note/更多弹层逻辑，不写第二份。
 */
const props = defineProps({
  /** [{ key, label, type, plain, loading, disabled }]，第 1 个为主操作；loading 为按钮级，主/次操作同口径 */
  actions: { type: Array, default: () => [] },
  /** 主操作不可用时的原因说明 / 权限说明，渲染在按钮上方 */
  note: { type: String, default: '' },
  /** 全局提交中：所有按钮禁用，避免连点产生多次流转 */
  submitting: { type: Boolean, default: false },
  /** 内嵌形态：随内容滚动、不固定悬浮；页面据此改用 .page--loose，不再预留固定栏高度 */
  inline: { type: Boolean, default: false }
})

const emit = defineEmits(['select'])

const showMore = ref(false)

const variant = computed(() => (props.actions.length >= 3 ? 'multi' : props.actions.length === 2 ? 'dual' : 'single'))
const mainAction = computed(() => props.actions[0] || null)
const restActions = computed(() => props.actions.slice(1))
/** multi 只把第 2 个之后收进弹层，第 2 个仍是可见的次操作 */
const visibleActions = computed(() => (variant.value === 'multi' ? [props.actions[1]] : restActions.value))
const moreActions = computed(() => (variant.value === 'multi' ? props.actions.slice(2) : []))

function onSelect(action) {
  // 已在提交中的按钮不再接受点击：避免同一动作被连点产生多次流转
  if (!action || action.disabled || action.loading || props.submitting) return
  showMore.value = false
  emit('select', action.key)
}
</script>

<template>
  <div
    v-if="actions.length"
    class="actionbar"
    :class="{ 'actionbar--with-note': note, 'actionbar--inline': inline }"
    role="toolbar"
    aria-label="页面操作"
  >
    <p v-if="note" class="actionbar__note">{{ note }}</p>
    <div class="actionbar__row">
      <van-button
        v-if="mainAction"
        class="actionbar__btn actionbar__btn--main"
        type="primary"
        :plain="mainAction.plain"
        :loading="mainAction.loading"
        :disabled="mainAction.disabled || submitting"
        @click="onSelect(mainAction)"
      >
        {{ mainAction.label }}
      </van-button>

      <van-button
        v-for="action in visibleActions"
        :key="action.key"
        class="actionbar__btn"
        :type="action.type || 'primary'"
        :plain="action.plain !== false"
        :loading="action.loading"
        :disabled="action.disabled || submitting"
        @click="onSelect(action)"
      >
        {{ action.label }}
      </van-button>

      <van-button v-if="moreActions.length" class="actionbar__btn" type="primary" plain @click="showMore = true"
        >更多</van-button
      >
    </div>

    <van-popup v-model:show="showMore" round position="bottom" safe-area-inset-bottom>
      <div class="actionbar__sheet">
        <div class="actionbar__sheet-title">更多操作</div>
        <button
          v-for="action in moreActions"
          :key="action.key"
          type="button"
          class="actionbar__sheet-item"
          :class="{ 'actionbar__sheet-item--danger': action.type === 'danger' }"
          :disabled="action.disabled || submitting || action.loading"
          :aria-busy="action.loading || undefined"
          @click="onSelect(action)"
        >
          {{ action.loading ? '处理中…' : action.label }}
        </button>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.actionbar {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 20;
  background: var(--surface-card);
  border-top: 1px solid var(--border-line);

  /* 向上反投影，与内容区分层 */
  box-shadow: var(--e3-up);

  /* 底部安全区留白：壳内避让系统手势条 */
  padding: 0 var(--sp-3) var(--safe-bottom);
}

/* 内嵌形态：按钮做进页面内容流，随内容滚动，彻底消除固定栏对末元素的遮挡。
   左右内边距归零（.page 已留 12px），底部仍避让系统手势条，顶部留一段与内容的呼吸。
   双类名提高优先级，稳定压过 mobile.scss 给 .actionbar 的固定栏限宽/居中规则。 */
.actionbar.actionbar--inline {
  position: static;
  z-index: auto;
  padding: 0 0 var(--safe-bottom);
  margin-top: var(--sp-4);
  border-top: none;
  box-shadow: none;
}

.actionbar__row {
  display: flex;
  gap: var(--sp-3);
  align-items: center;
  min-height: var(--actionbar-h);
}

.actionbar__btn {
  flex: 1;
  min-height: 44px;
}

/* 主操作占更大权重：一眼看清「这一步该做什么」 */
.actionbar__btn--main {
  flex: 2;
}

.actionbar__note {
  margin: 0;
  padding-top: var(--sp-2);
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

.actionbar__sheet {
  padding: var(--sp-5) 0 var(--sp-6);
}

.actionbar__sheet-title {
  margin-bottom: var(--sp-3);
  font-size: var(--fs-h2);
  font-weight: var(--fw-semibold);
  text-align: center;
}

.actionbar__sheet-item {
  display: block;
  width: 100%;
  min-height: 48px;
  font-size: var(--fs-body-strong);
  color: var(--text-1);
  background: none;
  border: none;
  border-top: 1px solid var(--border-line);
}

.actionbar__sheet-item--danger {
  color: var(--color-danger);
}
</style>
