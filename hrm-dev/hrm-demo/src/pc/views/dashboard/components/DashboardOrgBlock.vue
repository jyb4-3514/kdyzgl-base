<script setup>
import { ref } from 'vue'
import { ArrowDown } from '@element-plus/icons-vue'

/**
 * 组织规模区块（一期口径，默认折叠，降为末块）
 * 折叠态只被本区块消费，故留在组件内；父级不关心展开与否，避免把纯展示状态外提。
 */
defineProps({
  cards: { type: Array, default: () => [] },
  summary: { type: Object, default: null },
  // 折叠条的汇总文案由容器拼（口径同页头副信息），组件不重复推导
  summaryText: { type: String, default: '—' },
  error: { type: Boolean, default: false }
})

const orgOpen = ref(false)
</script>

<template>
  <el-card shadow="never" class="block-card org-card">
    <button type="button" class="org-toggle" :aria-expanded="orgOpen" @click="orgOpen = !orgOpen">
      <span>组织规模（一期口径）</span>
      <span class="org-toggle__sum">{{ summaryText }}</span>
      <el-icon :size="14" class="org-toggle__arrow" :class="{ 'is-open': orgOpen }"><ArrowDown /></el-icon>
    </button>
    <div v-show="orgOpen" class="org-body">
      <p v-if="error" class="org-error">组织规模加载失败，请刷新重试</p>
      <dl v-else class="org-list">
        <div v-for="item in cards" :key="item.key" class="org-list__item">
          <dt>{{ item.label }}</dt>
          <dd>{{ summary ? summary[item.key] : '—' }}</dd>
        </div>
      </dl>
    </div>
  </el-card>
</template>

<style scoped lang="scss">
.block-card {
  height: 100%;
  margin-bottom: var(--sp-4);
}

.org-card {
  margin-bottom: 0;
}

.org-toggle {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  width: 100%;
  padding: 0;
  border: none;
  background-color: transparent;
  font-family: inherit;
  font-size: var(--fs-h3);
  font-weight: var(--fw-semibold);
  color: var(--text-1);
  cursor: pointer;

  &__sum {
    margin-left: auto;
    font-size: var(--fs-caption);
    font-weight: var(--fw-regular);
    color: var(--text-3);
    font-variant-numeric: tabular-nums;
  }

  &__arrow {
    color: var(--text-3);
    transition: transform var(--dur-base) var(--ease-std);

    &.is-open {
      transform: rotate(180deg);
    }
  }
}

.org-body {
  margin-top: var(--sp-4);
}

.org-error {
  margin: 0;
  font-size: var(--fs-caption);
  color: var(--color-danger);
}

.org-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-6);
  margin: 0;

  &__item {
    dt {
      font-size: var(--fs-caption);
      color: var(--text-3);
    }

    dd {
      margin: var(--sp-1) 0 0;
      font-size: var(--fs-num-md);
      font-weight: var(--fw-semibold);
      color: var(--text-1);
      font-variant-numeric: tabular-nums;
    }
  }
}
</style>
