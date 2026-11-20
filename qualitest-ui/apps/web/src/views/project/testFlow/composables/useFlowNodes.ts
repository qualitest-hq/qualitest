/**
 * 画布节点增删改：按屏幕坐标落点创建、双击在可视区中心创建、属性区局部 patch。
 * 屏幕坐标换算与节点库拖放共用同一套画布根命中与 viewport 换算。
 */
import type { Node } from '@vue-flow/core';

import { findStartNodeIds } from '@/utils/flow/graphValidate';

import { clientToFlowPosition, getFlowCanvasRootEl } from '../canvas/paletteDrop/dropTarget';
import { NODE_MIN_H, NODE_W } from '../constants/flowConfig';
import { NODE_TYPES } from '../constants/nodeTypes';
import { useFlowHistory } from './useFlowHistory';
import { syncStagingDraftFromNode } from './stagingDraftSync';
import { useFlowCanvasStore } from '../stores/flowCanvasStore';
import { createNodeData, generateNodeId, updateSummary } from '../utils/nodeDataUtils';

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
    const el = getFlowCanvasRootEl();
    const rect = el?.getBoundingClientRect();
    const cx = rect ? rect.left + rect.width / 2 : window.innerWidth / 2;
    const cy = rect ? rect.top + rect.height / 2 : window.innerHeight / 2;
    const pos = clientToFlowPosition(cx, cy, store);
    return addNode(type, { x: pos.x - NODE_W / 2, y: pos.y - NODE_MIN_H / 2 });
  }

  /** 按屏幕坐标落点创建（节点库指针拖放） */
  function addNodeAtClient(type: string, clientX: number, clientY: number) {
    const pos = clientToFlowPosition(clientX, clientY, store);
    return addNode(type, { x: pos.x - NODE_W / 2, y: pos.y - NODE_MIN_H / 2 });
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
    addNodeAtClient,
    patchNodeData,
    replaceNodeData,
    getStartNodeIds,
    isStartNode,
  };
}
