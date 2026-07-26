<script setup lang="ts">
/**
 * AI assistant 回复气泡。
 *
 * 展示内容优先级：
 * 1. 流式思考过程（details 折叠，生成中默认展开）
 * 2. 已落库的思考过程
 * 3. 完整 Markdown 正文
 * 4. 流式 Markdown 正文 + 闪烁光标
 * 5. 仅「生成中…」占位
 *
 * 底部可显示模型标签、操作按钮（#actions 插槽）、业务扩展区（#extra 插槽，如 Diff 面板）。
 */
import MarkdownContent from '@/components/MarkdownContent/index.vue';

/** assistant 消息渲染所需字段 */
export interface AiChatAssistantMessageView {
  id: string;
  content?: string;
  thinkingContent?: string;
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
</script>

<template>
  <div class="ai-chat-msg__bubble ai-chat-msg__bubble--assistant">
    <!-- 流式思考：生成中且已有思考内容 -->
    <details
        v-if="message.id === streamingMessageId && streamThinking"
        class="ai-chat-msg__thinking"
        open
    >
      <summary>思考中…</summary>
      <MarkdownContent
          class="ai-chat-msg__thinking-body ai-chat-msg__thinking-markdown"
          :source="streamThinking"
      />
    </details>
    <!-- 已保存的思考过程，默认折叠 -->
    <details v-else-if="message.thinkingContent" class="ai-chat-msg__thinking">
      <summary>思考过程</summary>
      <MarkdownContent
          class="ai-chat-msg__thinking-body ai-chat-msg__thinking-markdown"
          :source="message.thinkingContent"
      />
    </details>

    <!-- 完整回复（非流式或已落库） -->
    <MarkdownContent
        v-if="message.content"
        class="ai-chat-msg__markdown"
        :source="message.content"
    />
    <!-- 流式正文 + 光标 -->
    <div
        v-else-if="message.id === streamingMessageId && streamText"
        class="ai-chat-msg__streaming-wrap"
    >
      <MarkdownContent
          class="ai-chat-msg__markdown ai-chat-msg__streaming"
          :source="streamText"
      />
      <span class="ai-chat-msg__cursor">▍</span>
    </div>
    <!-- 流式刚开始、尚无文字 -->
    <p v-else-if="message.id === streamingMessageId" class="ai-chat-msg__text ai-chat-msg__streaming">
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
