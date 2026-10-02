<script setup lang="ts">
/**
 * AI assistant 回复气泡。
 *
 * 展示内容优先级：
 * 1. 流式思考过程（details 折叠，生成中默认展开）
 * 2. 已保存的思考过程（默认折叠）
 * 3. 完整 Markdown 正文：过程旁白每句一段在前，总结在后
 * 4. 生成中的实时 Markdown 正文 + 闪烁光标（调用工具前的说明文字也实时显示在这里）
 * 5. 仅「生成中…」占位
 *
 * 底部可显示模型标签、操作按钮（#actions 插槽）、业务扩展区（#extra 插槽，如 Diff 面板）。
 *
 * 流式思考区：限高滚动，默认贴底跟随最新内容；用户上滑离开底部后暂停，滚回阈值内恢复。
 */
import { computed, nextTick, ref, watch } from 'vue';

import MarkdownContent from '@/components/MarkdownContent/index.vue';
import { isNearStickBottom } from '@/composables/ai/useAiChatMessageListScroll';

/** assistant 消息渲染所需字段 */
export interface AiChatAssistantMessageView {
  id: string;
  content?: string;
  thinkingContent?: string;
  /** 过程旁白：调用工具前模型输出的说明文字，多句以换行分隔；结束后显示在总结前面 */
  processNarration?: string;
  vendorName?: string;
  modelName?: string;
}

const props = defineProps<{
  message: AiChatAssistantMessageView;
  /** 当前正在流式输出的占位消息 id */
  streamingMessageId?: string | null;
  /** 流式正文片段（SSE 增量） */
  streamText?: string;
  /** 流式思考过程片段 */
  streamThinking?: string;
  /** 是否显示 #actions 插槽（编辑/重新生成等） */
  showActions?: boolean;
}>();

/** 本条是否为正在流式输出的占位消息 */
const isStreaming = computed(() => props.message.id === props.streamingMessageId);

/** 结束后的正文：每句旁白单独成段，再接总结；生成中只取消息自身正文（通常为空），实时内容由 streamText 显示 */
const displayContent = computed(() => {
  const content = props.message.content?.trim() ?? '';
  if (isStreaming.value) return content;
  const narration = props.message.processNarration?.trim();
  if (!narration) return content;
  const paragraphs = narration.split(/\n+/).map((line) => line.trim()).filter(Boolean);
  if (content) paragraphs.push(content);
  return paragraphs.join('\n\n');
});

/** 流式思考滚动容器 */
const streamThinkingBodyRef = ref<HTMLElement | null>(null);
/** true：思考内容增高时自动滚到底；用户上滑离开后为 false */
const stickThinkingBottom = ref(true);

/** 用户滚动思考区：接近底部时继续自动跟随，离开底部则暂停 */
function onStreamThinkingScroll() {
  const el = streamThinkingBodyRef.value;
  if (!el) return;
  stickThinkingBottom.value = isNearStickBottom(el);
}

/** 思考内容增加时：新一轮开始先恢复自动跟随；处于跟随状态就滚到最新内容 */
watch(
  () => (isStreaming.value ? props.streamThinking ?? '' : ''),
  async (text, prev) => {
    if (!text) return;
    if (!prev) stickThinkingBottom.value = true;
    await nextTick();
    const el = streamThinkingBodyRef.value;
    if (el && stickThinkingBottom.value) el.scrollTop = el.scrollHeight;
  },
);
</script>

<template>
  <div class="ai-chat-msg__bubble ai-chat-msg__bubble--assistant">
    <!-- 流式思考：生成中且已有思考内容；限高滚动区，默认自动跟随到底部 -->
    <details
        v-if="isStreaming && streamThinking"
        class="ai-chat-msg__thinking"
        open
    >
      <summary>思考中…</summary>
      <div
          ref="streamThinkingBodyRef"
          class="ai-chat-msg__thinking-body"
          @scroll="onStreamThinkingScroll"
      >
        <MarkdownContent
            class="ai-chat-msg__thinking-markdown"
            :source="streamThinking"
        />
      </div>
    </details>
    <!-- 已保存的思考过程，默认折叠 -->
    <details v-else-if="message.thinkingContent" class="ai-chat-msg__thinking">
      <summary>思考过程</summary>
      <MarkdownContent
          class="ai-chat-msg__thinking-body ai-chat-msg__thinking-markdown"
          :source="message.thinkingContent"
      />
    </details>

    <!-- 完整回复（生成结束或历史消息）：过程旁白 + 总结 -->
    <MarkdownContent
        v-if="displayContent"
        class="ai-chat-msg__markdown"
        :source="displayContent"
    />
    <!-- 生成中：实时正文（含调用工具前的说明文字）+ 光标 -->
    <div
        v-else-if="isStreaming && streamText"
        class="ai-chat-msg__streaming-wrap"
    >
      <MarkdownContent
          class="ai-chat-msg__markdown ai-chat-msg__streaming"
          :source="streamText"
      />
      <span class="ai-chat-msg__cursor">▍</span>
    </div>
    <!-- 流式刚开始、尚无文字 -->
    <p v-else-if="isStreaming" class="ai-chat-msg__text ai-chat-msg__streaming">
      生成中…<span class="ai-chat-msg__cursor">▍</span>
    </p>

    <span v-if="message.vendorName && message.modelName" class="ai-chat-msg__model-tag">
      {{ message.vendorName }} · {{ message.modelName }}
    </span>

    <div v-if="showActions" class="ai-chat-msg__actions">
      <slot name="actions" />
    </div>

    <!-- 业务扩展：测试流 Diff、脚本 Diff 等 -->
    <slot name="extra" />
  </div>
</template>

<style scoped lang="scss">
.ai-chat-msg__bubble {
  max-width: 96%;
  border-radius: 12px;
  padding: 10px 14px;
  font-size: 13px;
  line-height: 1.5;
}

.ai-chat-msg__bubble--assistant {
  background: var(--pd-bg-sunken);
  border: 1px solid var(--pd-divider);
}

.ai-chat-msg__thinking {
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--pd-text-muted);

  summary {
    cursor: pointer;
    user-select: none;
  }
}

.ai-chat-msg__thinking-body {
  margin-top: 6px;
  max-height: 200px;
  overflow-y: auto;
}

.ai-chat-msg__markdown {
  :deep(p) {
    margin: 0 0 0.5em;

    &:last-child {
      margin-bottom: 0;
    }
  }
}

.ai-chat-msg__streaming-wrap {
  position: relative;
}

.ai-chat-msg__streaming {
  display: inline;
}

.ai-chat-msg__cursor {
  animation: ai-chat-msg-blink 1s step-end infinite;
  color: var(--pd-primary);
}

@keyframes ai-chat-msg-blink {
  50% {
    opacity: 0;
  }
}

.ai-chat-msg__model-tag {
  display: inline-block;
  margin-top: 8px;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  color: var(--pd-text-muted);
  background: color-mix(in srgb, var(--pd-text) 6%, transparent);
}
</style>
