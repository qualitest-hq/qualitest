<template>
  <span class="ai-staging-edge-flush" aria-hidden="true" />
</template>

<script setup lang="ts">
/**
 * Staging 连线灌入组件。
 *
 * 挂载在 Vue Flow 内部，负责将 store.pendingEdges 写入画布。
 *
 * 背景：节点与边若同时写入 store，Vue Flow 可能在内部尚未注册节点时处理 setEdges，
 * 导致找不到 source/target 而静默丢弃全部边。因此边先暂存 pendingEdges，
 * 待 findNode 能解析两端节点后再 setEdges。
 *
 * 触发灌入的时机：
 * - stagingEdgeFlushToken 变化（Staging 同步写入新 pending 边）
 * - pendingEdges 数量变化
 * - 画布节点 id 列表变化（新节点注册完成）
 * - Vue Flow 节点初始化完成
 * - 组件挂载
 */
import { useVueFlow } from '@vue-flow/core';
import { nextTick, onMounted, watch } from 'vue';

import { useFlowCanvasStore } from '../stores/flowCanvasStore';

const store = useFlowCanvasStore();
const { findNode, setEdges, onNodesInitialized } = useVueFlow();

/** 检查 pending 边两端节点是否已在 Vue Flow 内注册，就绪则灌入并清空 pending。 */
async function tryFlushPendingEdges() {
  const pending = store.pendingEdges;
  if (!pending?.length) return;

  const ready = pending.every((e) => findNode(e.source) && findNode(e.target));
  if (!ready) return;

  setEdges(pending.map((e) => ({ ...e })));
  await nextTick();

  const persisted = pending.every((e) => store.edges.some((x) => x.id === e.id));
  if (persisted) {
    store.pendingEdges = null;
  }
}

watch(
  () => [
    store.stagingEdgeFlushToken,
    store.pendingEdges?.length ?? 0,
    store.nodes.map((n) => n.id).join(','),
  ] as const,
  () => {
    void nextTick(() => tryFlushPendingEdges());
  },
);

onNodesInitialized(async () => {
  await nextTick();
  await tryFlushPendingEdges();
});

onMounted(() => {
  void tryFlushPendingEdges();
});
</script>
