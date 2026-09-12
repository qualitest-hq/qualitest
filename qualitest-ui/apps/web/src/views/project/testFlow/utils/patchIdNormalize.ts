/**
 * AI 设计 patch 的 id 规范化（前端 hydrate 兜底）。
 * 非数字 id 发雪花并同步边端点；传入 clientIdMap 时优先复用已有映射。
 * 同时删除 condition 分支上预写的 target，并对无效边端点做启发式改写（改写必返回警告文案）。
 */
import type { FlowDesignPatch } from '../types/aiDesignTypes';
import { nextSnowflakeId } from '@/utils/flow/snowflakeId';

/** id 是否为纯数字字符串（可用的雪花形态） */
function isValidNumericId(id: string | undefined | null): boolean {
  return !!id && /^\d+$/.test(id);
}

/** 规范化结果：新 patch + 边端点改写说明 */
export interface NormalizeFlowDesignPatchIdsResult {
  patch: FlowDesignPatch;
  /** 启发式改写边端点时的说明；无改写时为空数组 */
  rewireWarnings: string[];
}

/**
 * 规范化新增节点/边的 id，并处理分支 target 与边端点改写；只返回 patch。
 * 可选 clientIdMap：已映射短名不再重新发号。
 */
export function normalizeFlowDesignPatchIds(
  patch: FlowDesignPatch,
  clientIdMap?: Record<string, string> | Map<string, string>,
): FlowDesignPatch {
  return normalizeFlowDesignPatchIdsWithWarnings(patch, clientIdMap).patch;
}

/**
 * 规范化新增节点/边的 id，删除条件分支预写 target，并对无效边端点做启发式改写。
 * 改写说明放在 rewireWarnings，供 toast 展示。
 */
export function normalizeFlowDesignPatchIdsWithWarnings(
  patch: FlowDesignPatch,
  clientIdMap?: Record<string, string> | Map<string, string>,
): NormalizeFlowDesignPatchIdsResult {
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

  // 条件出口只认连线：删掉预写的 branches.target
  stripBranchTargets(next.addNodes);
  stripBranchTargets(next.updateNodes);

  const rewireWarnings = rewireAddEdgeEndpointsToAddNodes(next);
  return { patch: next, rewireWarnings };
}

function toMap(clientIdMap?: Record<string, string> | Map<string, string>): Map<string, string> {
  if (!clientIdMap) return new Map();
  if (clientIdMap instanceof Map) return new Map(clientIdMap);
  return new Map(Object.entries(clientIdMap));
}

/** 删除节点 data.branches 各项的 target 字段 */
function stripBranchTargets(
  nodes: FlowDesignPatch['addNodes'] | FlowDesignPatch['updateNodes'],
) {
  for (const node of nodes ?? []) {
    const branches = (node.data as { branches?: Array<{ target?: string }> } | undefined)?.branches;
    if (!Array.isArray(branches)) continue;
    for (const branch of branches) {
      if (branch && 'target' in branch) {
        delete branch.target;
      }
    }
  }
}

/**
 * 当 addEdges 的 source/target 仍指不到本批 addNodes 时改写端点。
 * 边数 = 节点数 - 1：按节点顺序连成链；3 节点 3 边：连成 A→B、B→C、A→C。
 * 直接改入参 patch；有改写时返回说明文案，禁止静默改拓扑。
 */
export function rewireAddEdgeEndpointsToAddNodes(patch: FlowDesignPatch): string[] {
  const warnings: string[] = [];
  const nodes = patch.addNodes ?? [];
  const edges = patch.addEdges ?? [];
  if (nodes.length < 2 || !edges.length) {
    return warnings;
  }

  const nodeIds = nodes.map((n) => n.id);
  const validSet = new Set(nodeIds);

  const needsRewire = edges.some((e) => !validSet.has(e.source) || !validSet.has(e.target));
  if (!needsRewire) {
    return warnings;
  }

  if (edges.length === nodes.length - 1) {
    for (let i = 0; i < edges.length; i++) {
      const before = `${edges[i].source}→${edges[i].target}`;
      edges[i].source = nodeIds[i];
      edges[i].target = nodeIds[i + 1];
      warnings.push(
        `addEdge:${edges[i].id} 端点无效，已按链式拓扑改写：${before} → ${edges[i].source}→${edges[i].target}`,
      );
    }
    return warnings;
  }

  if (nodes.length === 3 && edges.length === 3) {
    const plan: Array<[string, string]> = [
      [nodeIds[0], nodeIds[1]],
      [nodeIds[1], nodeIds[2]],
      [nodeIds[0], nodeIds[2]],
    ];
    for (let i = 0; i < edges.length; i++) {
      const before = `${edges[i].source}→${edges[i].target}`;
      edges[i].source = plan[i][0];
      edges[i].target = plan[i][1];
      warnings.push(
        `addEdge:${edges[i].id} 端点无效，已按三角拓扑改写：${before} → ${edges[i].source}→${edges[i].target}`,
      );
    }
  }
  return warnings;
}
