/**
 * AI 设计 patch 的 id 规范化（前端 hydrate）。
 * 非数字 id 发雪花并同步边端点；传入 clientIdMap 时优先复用已有映射。
 * 同时删除 condition 分支上预写的 target。
 */
import type { FlowDesignPatch } from '../types/aiDesignTypes';
import { nextSnowflakeId } from '@/utils/flow/snowflakeId';

/** id 是否为纯数字字符串（可用的雪花形态） */
function isValidNumericId(id: string | undefined | null): boolean {
  return !!id && /^\d+$/.test(id);
}

/**
 * 规范化新增节点/边的 id，并处理分支 target。
 * 可选 clientIdMap：已映射短名不再重新发号。
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

  stripBranchTargets(next.addNodes);
  stripBranchTargets(next.updateNodes);

  return next;
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
