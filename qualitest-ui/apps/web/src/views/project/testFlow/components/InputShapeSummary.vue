<template>
  <div class="shape-summary">
    <div v-for="sec in sections" :key="sec.key" class="shape-summary__sec">
      <div class="shape-summary__title">{{ sec.title }}</div>
      <ul v-if="sec.items.length" class="shape-summary__list">
        <li v-for="(item, i) in sec.items" :key="i">{{ item }}</li>
      </ul>
      <p v-else class="shape-summary__empty">（无）</p>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 入参形状（inputShape）只读摘要组件。
 * 按 Body / Query / Headers 三块列出字段名、类型、是否必填的短文案；无字段时显示「（无）」。
 */
import { computed } from 'vue';

import { inputShapeSummarySections } from '../utils/entryDoc';

const props = defineProps<{
  /** 入口上的入参形状对象；非法或空则三块皆空 */
  inputShape?: unknown;
}>();

/** 分区标题 + 行文案，驱动模板列表 */
const sections = computed(() => inputShapeSummarySections(props.inputShape));
</script>

<style scoped lang="scss">
.shape-summary {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.shape-summary__title {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-regular, #606266);
  margin-bottom: 4px;
}

.shape-summary__list {
  margin: 0;
  padding-left: 18px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-primary, #303133);
}

.shape-summary__empty {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
}
</style>
