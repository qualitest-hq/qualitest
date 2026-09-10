/**
 * AI 设计 patch 的 id 规范化。
 *
 * 服务端 submit 已按会话 map 把短名落成雪花；前端 hydrate 以服务端结果为准。
 * 本函数仅作无会话上下文时的兜底：非数字 id 发号，并同步边端点；若传入 clientIdMap 则优先复用。
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
 * @param clientIdMap 可选会话短名→雪花映射；有则禁止对已映射短名重新发号
 */
export function normalizeFlowDesignPatchIds(
  patch: FlowDesignPatch,
  clientIdMap?: Record<string, string> | Map<string, string>,
): FlowDesignPatch {
  const next = JSON.parse(JSON.stringify(patch)) as FlowDesignPatch;
  const idRemap = new Map<string, string>();
  const sessionMap = toMap(clientIdMap);

  for (const node of next.addNodes ?? []) {
    const oldId = node.id?.trim() ?? '';
    if (oldId && sessionMap.has(oldId)) {
      const mapped = sessionMap.get(oldId)!;
      idRemap.set(oldId, mapped);
      node.id = mapped;
      continue;
    }
    if (!isValidNumericId(oldId)) {
      const newId = nextSnowflakeId();
      if (oldId) {
        idRemap.set(oldId, newId);
        sessionMap.set(oldId, newId);
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
    } else if (edge.source && sessionMap.has(edge.source)) {
      edge.source = sessionMap.get(edge.source)!;
    }
    if (edge.target && idRemap.has(edge.target)) {
      edge.target = idRemap.get(edge.target)!;
    } else if (edge.target && sessionMap.has(edge.target)) {
      edge.target = sessionMap.get(edge.target)!;
    }
  }

  remapBranchTargets(next.addNodes, idRemap, sessionMap);
  remapBranchTargets(next.updateNodes, idRemap, sessionMap);

  rewireAddEdgeEndpointsToAddNodes(next);
  return next;
}

function toMap(clientIdMap?: Record<string, string> | Map<string, string>): Map<string, string> {
  if (!clientIdMap) return new Map();
  if (clientIdMap instanceof Map) return new Map(clientIdMap);
  return new Map(Object.entries(clientIdMap));
}

function remapBranchTargets(
  nodes: FlowDesignPatch['addNodes'] | FlowDesignPatch['updateNodes'],
  idRemap: Map<string, string>,
  sessionMap: Map<string, string>,
) {
  for (const node of nodes ?? []) {
    const branches = (node.data as { branches?: Array<{ target?: string }> } | undefined)?.branches;
    if (!Array.isArray(branches)) continue;
    for (const branch of branches) {
      const t = branch.target?.trim();
      if (!t) continue;
      if (idRemap.has(t)) branch.target = idRemap.get(t);
      else if (sessionMap.has(t)) branch.target = sessionMap.get(t);
    }
  }
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
    for (let i = 0; i < edges.length; i++) {
      edges[i].source = nodeIds[i];
      edges[i].target = nodeIds[i + 1];
    }
    return;
  }

  if (nodes.length === 3 && edges.length === 3) {
    edges[0].source = nodeIds[0];
    edges[0].target = nodeIds[1];
    edges[1].source = nodeIds[1];
    edges[1].target = nodeIds[2];
    edges[2].source = nodeIds[0];
    edges[2].target = nodeIds[2];
  }
}
