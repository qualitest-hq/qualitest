<!--
  单条聊天消息的外层容器。
  负责消息行布局（左/右/居中）以及 Lazy Patch 所需的 data 属性：
  - data-message-id：消息唯一 id
  - data-patch-pending：patch 详情尚未加载，进入视口时需触发懒加载
-->
<script setup lang="ts">
defineProps<{
  /** 消息 id 与角色，用于 CSS 对齐和 DOM 标记 */
  message: { id: string; role: string };
  /** 为 true 时在 DOM 上写入 data-patch-pending="1" */
  patchPending?: boolean;
}>();
</script>

<template>
  <div
      :class="['ai-chat-msg', `ai-chat-msg--${message.role}`]"
      :data-message-id="message.id"
      :data-patch-pending="patchPending ? '1' : undefined"
  >
    <slot />
  </div>
</template>
