/**
 * AI 设计 patch 的 id 规范化。
 *
 * 处理 AI 返回的临时节点/边 id（非纯数字），替换为雪花 id，
 * 并在节点 id 变更时同步修正连线的 source/target，避免画布找不到端点。
 */
import type { FlowDesignPatch } from '../types/aiDesignTypes';
import { nextSnowflakeId } from '@/utils/flow/snowflakeId';

/** 判断 id 是否为可用的纯数字字符串。 */
function isValidNumericId(id: string | undefined | null): boolean {
  return !!id && /^\d+$/.test(id);
}

/**
 * 规范化 patch 中所有新增节点与连线的 id。
 *
 * 流程：
 * 1. 遍历 addNodes，非数字 id 替换为雪花 id，并记录旧 id → 新 id 映射
 * 2. 遍历 addEdges，非数字边 id 替换为雪花 id；source/target 若在映射中则一并替换
 * 3. 若仍有连线端点无法对应任何新增节点，尝试按常见拓扑重连
 *
 * 返回深拷贝后的 patch，不修改入参。
 */
export function normalizeFlowDesignPatchIds(patch: FlowDesignPatch): FlowDesignPatch {
  const next = JSON.parse(JSON.stringify(patch)) as FlowDesignPatch;
  const idRemap = new Map<string, string>();

  for (const node of next.addNodes ?? []) {
    const oldId = node.id;
    if (!isValidNumericId(oldId)) {
      const newId = nextSnowflakeId();
      if (oldId?.trim()) {
        idRemap.set(oldId.trim(), newId);
      }
      node.id = newId;
    }
  }

  for (const edge of next.addEdges ?? []) {
    if (!isValidNumericId(edge.id)) {
      edge.id = nextSnowflakeId();
    }
    if (edge.source && idRemap.has(edge.source)) {
      edge.source = idRemap.get(edge.source)!;
    }
    if (edge.target && idRemap.has(edge.target)) {
      edge.target = idRemap.get(edge.target)!;
    }
  }

  rewireAddEdgeEndpointsToAddNodes(next);
  return next;
}

/**
 * 修正 addEdges 中仍无法指向 addNodes 的 source/target。
 *
 * 仅在存在无效端点时执行：
 * - 边数 = 节点数 - 1：按节点顺序串成链（n0→n1、n1→n2 …）
 * - 3 节点且 3 边：按 A→B、B→C、A→C 赋值
 *
 * 直接修改入参 patch。
 */
export function rewireAddEdgeEndpointsToAddNodes(patch: FlowDesignPatch): void {
  const nodes = patch.addNodes ?? [];
  const edges = patch.addEdges ?? [];
  if (nodes.length < 2 || !edges.length) {
    return;
  }

  const nodeIds = nodes.map((n) => n.id);
  const validSet = new Set(nodeIds);

  const needsRewire = edges.some((e) => !validSet.has(e.source) || !validSet.has(e.target));
  if (!needsRewire) {
    return;
  }

  if (edges.length === nodes.length - 1) {
    for (let i = 0; i < edges.length; i += 1) {
      edges[i].source = nodeIds[i];
      edges[i].target = nodeIds[i + 1];
    }
    return;
  }

  if (nodes.length === 3 && edges.length === 3) {
    const [a, b, c] = nodeIds;
    edges[0].source = a;
    edges[0].target = b;
    edges[1].source = b;
    edges[1].target = c;
    edges[2].source = a;
    edges[2].target = c;
  }
}
