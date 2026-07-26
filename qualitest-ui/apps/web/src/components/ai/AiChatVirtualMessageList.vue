<script setup lang="ts" generic="T extends { id: string }">
/**
 * AI 会话消息虚拟滚动容器。
 * 消息数低于阈值时用普通列表；达到阈值后启用 @tanstack/vue-virtual。
 */
import { computed, ref, watch } from 'vue';
import { useVirtualizer } from '@tanstack/vue-virtual';

import {
  AI_CHAT_LOAD_OLDER_TOP_PX,
  AI_CHAT_VIRTUAL_SCROLL_THRESHOLD,
} from '@/utils/ai/aiChatMessagePage';

const props = withDefaults(
  defineProps<{
    messages: T[];
    hasMoreOlder?: boolean;
    loadingOlder?: boolean;
    virtualThreshold?: number;
  }>(),
  {
    hasMoreOlder: false,
    loadingOlder: false,
    virtualThreshold: AI_CHAT_VIRTUAL_SCROLL_THRESHOLD,
  },
);

const emit = defineEmits<{
  loadOlder: [];
  scroll: [];
}>();

const parentRef = ref<HTMLElement | null>(null);

const useVirtual = computed(() => props.messages.length >= props.virtualThreshold);

const virtualizerOptions = computed(() => ({
  count: props.messages.length,
  getScrollElement: () => parentRef.value,
  estimateSize: () => 140,
  overscan: 6,
}));

const virtualizer = useVirtualizer(virtualizerOptions);
const virtualItems = computed(() => virtualizer.value.getVirtualItems());

let loadOlderLocked = false;

function onScroll() {
  emit('scroll');
  if (loadOlderLocked || props.loadingOlder || !props.hasMoreOlder) return;
  const el = parentRef.value;
  if (!el || el.scrollTop > AI_CHAT_LOAD_OLDER_TOP_PX) return;
  loadOlderLocked = true;
  emit('loadOlder');
}

watch(
  () => props.loadingOlder,
  (loading) => {
    if (!loading) {
      loadOlderLocked = false;
    }
  },
);

defineExpose({
  getScrollElement: () => parentRef.value,
});
</script>

<template>
  <div ref="parentRef" class="ai-chat-virtual-list" @scroll="onScroll">
    <div
        v-if="hasMoreOlder || loadingOlder"
        class="ai-chat-virtual-list__older"
    >
      <span v-if="loadingOlder">正在加载更早消息…</span>
      <span v-else>向上滚动加载更早消息</span>
    </div>

    <template v-if="!useVirtual">
      <slot
          v-for="(msg, index) in messages"
          :key="msg.id"
          :index="index"
          :message="msg"
      />
    </template>

    <div
        v-else
        :style="{ height: `${virtualizer.getTotalSize()}px`, position: 'relative', width: '100%' }"
    >
      <div
          v-for="item in virtualItems"
          :key="String(item.key)"
          :data-index="item.index"
          :ref="(el) => virtualizer.measureElement(el as Element)"
          :style="{
            position: 'absolute',
            top: 0,
            left: 0,
            width: '100%',
            transform: `translateY(${item.start}px)`,
          }"
      >
        <slot :index="item.index" :message="messages[item.index]" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.ai-chat-virtual-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
}

.ai-chat-virtual-list__older {
  padding: 8px 0 12px;
  text-align: center;
  font-size: 12px;
  color: var(--pd-text-muted, #888);
}
</style>
