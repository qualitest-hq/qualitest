/**
 * 画布节点增删改：拖拽落点、节点库双击、属性区 patch。
 * 负责生成节点 id/data、刷新 summary、判定流程开始节点。
 */
import type { Node } from '@vue-flow/core';

import { findStartNodeIds } from '@/utils/flow/graphValidate';

import { NODE_MIN_H, NODE_W } from '../constants/flowConfig';
import { NODE_TYPES } from '../constants/nodeTypes';
import { useFlowHistory } from './useFlowHistory';
import { syncStagingDraftFromNode } from './stagingDraftSync';
import { useFlowCanvasStore } from '../stores/flowCanvasStore';
import { createNodeData, generateNodeId, updateSummary } from '../utils/nodeDataUtils';

/** 将浏览器 client 坐标转换为 vue-flow 画布逻辑坐标（考虑 viewport 平移与缩放） */
function clientToFlowPosition(clientX: number, clientY: number, store: ReturnType<typeof useFlowCanvasStore>) {
  const el = document.querySelector('.flow-canvas-vue-flow');
  const rect = el?.getBoundingClientRect();
  if (!rect) return { x: 0, y: 0 };
  const { x: vx, y: vy, zoom } = store.viewport;
  return {
    x: (clientX - rect.left - vx) / zoom,
    y: (clientY - rect.top - vy) / zoom,
  };
}

export function useFlowNodes() {
  const store = useFlowCanvasStore();
  const { pushHistory } = useFlowHistory();

  /** 在指定坐标创建节点，选中并打开右栏属性 */
  function addNode(type: string, position: { x: number; y: number }, dataOverride: Record<string, unknown> = {}) {
    const cfg = NODE_TYPES[type];
    if (!cfg) return null;
    const id = generateNodeId(type);
    const data = createNodeData(type, dataOverride);
    const node: Node = {
      id,
      type,
      position: { x: position.x, y: position.y },
      data,
    };
    store.nodes.push(node);
    store.markDirty();
    store.selectItem('node', id);
    store.ui.rightOpen = true;
    pushHistory();
    return id;
  }

  /** 在画布可视区域中心创建节点（节点库双击入口） */
  function addNodeAtCenter(type: string) {
    const el = document.querySelector('.flow-canvas-vue-flow');
    const rect = el?.getBoundingClientRect();
    const cx = rect ? rect.left + rect.width / 2 : window.innerWidth / 2;
    const cy = rect ? rect.top + rect.height / 2 : window.innerHeight / 2;
    const pos = clientToFlowPosition(cx, cy, store);
    return addNode(type, { x: pos.x - NODE_W / 2, y: pos.y - NODE_MIN_H / 2 });
  }

  /** 节点库拖放到画布：读取 dataTransfer.nodeType 并落点创建 */
  function handleDrop(event: DragEvent) {
    event.preventDefault();
    const type = event.dataTransfer?.getData('nodeType');
    if (!type) return;
    const pos = clientToFlowPosition(event.clientX, event.clientY, store);
    addNode(type, { x: pos.x - NODE_W / 2, y: pos.y - NODE_MIN_H / 2 });
  }

  function handleDragOver(event: DragEvent) {
    event.preventDefault();
    if (event.dataTransfer) event.dataTransfer.dropEffect = 'copy';
  }

  /** 合并节点 data 字段；patch 里值为 null 的键表示删除该字段 */
  function patchNodeData(nodeId: string, patch: Record<string, unknown>) {
    const node = store.nodes.find((n) => n.id === nodeId);
    if (!node) return;
    Object.assign(node.data, patch);
    for (const [key, value] of Object.entries(patch)) {
      if (value === null) {
        delete node.data[key];
      }
    }
    updateSummary(String(node.type), node.data as Record<string, unknown>);
    store.markDirty();
    syncStagingDraftFromNode(nodeId);
  }

  /**
   * 整对象替换节点 data。
   * 保存薄节点时用：才能真正去掉旧的 requestConfig / apiPath。
   */
  function replaceNodeData(nodeId: string, data: Record<string, unknown>) {
    const node = store.nodes.find((n) => n.id === nodeId);
    if (!node) return;
    node.data = data;
    updateSummary(String(node.type), node.data as Record<string, unknown>);
    store.markDirty();
    syncStagingDraftFromNode(nodeId);
  }

  function getStartNodeIds() {
    const graphNodes = store.nodes.map((n) => ({ id: n.id }));
    const graphEdges = store.edges.map((e) => ({ source: e.source, target: e.target }));
    return findStartNodeIds({ nodes: graphNodes, edges: graphEdges });
  }

  /** 当前图无入边节点视为候选开始节点；用于卡片「开始」角标 */
  function isStartNode(nodeId: string) {
    return getStartNodeIds().includes(nodeId);
  }

  return {
    addNode,
    addNodeAtCenter,
    handleDrop,
    handleDragOver,
    patchNodeData,
    replaceNodeData,
    getStartNodeIds,
    isStartNode,
  };
}
