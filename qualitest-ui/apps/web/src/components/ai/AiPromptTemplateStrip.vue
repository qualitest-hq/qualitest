<template>
  <!-- 输入框上方的横向快捷提示词胶囊条；点击将模板内容交给父组件填入输入区 -->
  <div
      v-if="loading || sortedItems.length"
      :class="['ai-prompt-template-strip', { 'is-disabled': disabled }]"
  >
    <div v-if="loading" class="ai-prompt-template-strip__hint">加载模板…</div>
    <div
        v-else
        ref="scrollRef"
        class="ai-prompt-template-strip__scroll"
        role="list"
        aria-label="快捷模板"
        @wheel="onWheel"
    >
      <el-tooltip
          v-for="item in sortedItems"
          :key="item.aiPromptTemplateId"
          :content="tooltipContent(item)"
          :disabled="!item.templateDescription"
          placement="top"
          :show-after="280"
      >
        <button
            :disabled="disabled"
            class="ai-prompt-template-strip__chip"
            type="button"
            @click="onSelect(item)"
        >
          <span class="ai-prompt-template-strip__chip-title">{{ item.templateTitle }}</span>
          <span
              v-if="item.templateScope === 'project'"
              class="ai-prompt-template-strip__chip-tag"
          >项目</span>
        </button>
      </el-tooltip>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * AI 助手输入区快捷提示词胶囊条。
 * 按 sortNum 排序展示模板标题；横向滚动隐藏滚动条，鼠标滚轮可左右滑动。
 */
import { computed, ref } from 'vue';

import type { AiPromptTemplateItem } from '@/api/project/testFlowAi';

const props = defineProps<{
  items: AiPromptTemplateItem[];
  loading?: boolean;
  disabled?: boolean;
}>();

const emit = defineEmits<{
  select: [item: AiPromptTemplateItem];
}>();

const scrollRef = ref<HTMLElement | null>(null);

/** 按 sortNum 升序排列模板 */
const sortedItems = computed(() =>
  [...props.items].sort((a, b) => (a.sortNum ?? 0) - (b.sortNum ?? 0)),
);

function tooltipContent(item: AiPromptTemplateItem): string {
  return item.templateDescription?.trim() || item.templateTitle;
}

/** 内容超出可视宽度时，将纵向滚轮转为横向滚动 */
function onWheel(event: WheelEvent) {
  const el = scrollRef.value;
  if (!el || el.scrollWidth <= el.clientWidth) return;
  event.preventDefault();
  el.scrollLeft += event.deltaY;
}

function onSelect(item: AiPromptTemplateItem) {
  if (props.disabled) return;
  emit('select', item);
}
</script>

<style lang="scss" scoped>
@use '@/views/project/testFlow/styles/flowCanvasTokens.scss' as flow;

.ai-prompt-template-strip {
  @include flow.flow-pd-core-vars;
  flex-shrink: 0;
  padding: 0 0 8px;
  border-bottom: 1px solid var(--pd-divider);
  margin-bottom: 10px;

  &.is-disabled {
    opacity: 0.65;
  }
}

.ai-prompt-template-strip__hint {
  font-size: 12px;
  color: var(--pd-text-muted);
  line-height: 1.4;
}

.ai-prompt-template-strip__scroll {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: 6px;
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-width: none;

  &::-webkit-scrollbar {
    display: none;
  }
}

.ai-prompt-template-strip__chip {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 168px;
  height: 28px;
  padding: 0 10px;
  border: 1px solid var(--pd-border-subtle);
  border-radius: 999px;
  background: var(--pd-surface-elevated);
  color: var(--pd-text);
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    background 0.15s ease,
    color 0.15s ease,
    box-shadow 0.15s ease;

  &:hover:not(:disabled) {
    border-color: color-mix(in srgb, var(--pd-primary) 45%, var(--pd-border-subtle));
    background: var(--pd-primary-soft);
    color: var(--pd-primary);
  }

  &:disabled {
    cursor: not-allowed;
  }
}

.ai-prompt-template-strip__chip-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-prompt-template-strip__chip-tag {
  flex-shrink: 0;
  font-size: 10px;
  font-weight: 600;
  color: var(--pd-primary);
  opacity: 0.9;
}
</style>
