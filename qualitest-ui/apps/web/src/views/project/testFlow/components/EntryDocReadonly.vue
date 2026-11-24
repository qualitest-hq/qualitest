<template>
  <MarkdownContent v-if="trimmed" :source="trimmed" />
  <p v-else class="entry-doc-readonly__empty">{{ emptyText }}</p>
</template>

<script setup lang="ts">
/**
 * 入口说明只读块。
 * 有非空文本时按 Markdown 渲染；否则显示空态提示文案。
 * 列表抽屉、属性预览、跑前入口跟读共用本组件。
 */
import { computed } from 'vue';

import MarkdownContent from '@/components/MarkdownContent/index.vue';

const props = withDefaults(
  defineProps<{
    /** 说明原文；空串或仅空白视为未写 */
    source?: string;
    /** 未写说明时的提示；默认「未写说明」 */
    emptyText?: string;
  }>(),
  { source: '', emptyText: '未写说明' },
);

/** 去掉首尾空白后的正文；为空则走空态 */
const trimmed = computed(() => String(props.source ?? '').trim());
</script>

<style scoped lang="scss">
.entry-doc-readonly__empty {
  margin: 0;
  color: var(--el-text-color-secondary, #909399);
  font-size: 12px;
  line-height: 1.5;
}
</style>
