<!--
  用户发送的消息气泡。
  默认插槽放消息正文（纯文本或 Mention 片段），可选显示「编辑并重发」按钮。
-->
<script setup lang="ts">
defineProps<{
  /** 是否显示编辑按钮（生成中或 system 消息应传 false） */
  showActions?: boolean;
}>();

const emit = defineEmits<{
  /** 用户点击编辑，由父组件把内容回填到 Composer */
  edit: [];
}>();
</script>

<template>
  <div class="ai-chat-msg__bubble ai-chat-msg__bubble--user">
    <slot />
    <div v-if="showActions" class="ai-chat-msg__actions">
      <button
          class="ai-chat-msg__action"
          type="button"
          title="编辑并重发"
          @click="emit('edit')"
      >
        编辑
      </button>
    </div>
  </div>
</template>
