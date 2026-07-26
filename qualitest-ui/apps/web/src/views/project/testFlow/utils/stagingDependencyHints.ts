/**
 * Staging 单元确认前的本地依赖提示。
 *
 * 与后端 FlowDesignPatchConfirmService 规则对齐：
 * confirm addEdge 时，patch 内 addNode 端点须已 confirm。
 */
import type { FlowDesignPatch } from '../types/aiDesignTypes';

function patchAddNodeIds(patch: FlowDesignPatch): Set<string> {
  return new Set((patch.addNodes ?? []).map((node) => node.id));
}

function nodeDisplayName(patch: FlowDesignPatch, nodeId: string): string {
  const node = patch.addNodes?.find((item) => item.id === nodeId);
  const name = (node?.data as Record<string, unknown> | undefined)?.name;
  return name != null && String(name).trim() ? String(name) : `节点 ${nodeId}`;
}

function formatAddNodeDependencyHint(nodeId: string, patch: FlowDesignPatch): string {
  return `请先确认「${nodeDisplayName(patch, nodeId)}」`;
}

/** 返回人类可读依赖提示；不修改单元状态 */
export function getStagingDependencyHints(
  unitId: string,
  patch: FlowDesignPatch,
  confirmedUnitIds: ReadonlySet<string>,
): string[] {
  if (!unitId.startsWith('addEdge:')) return [];

  const edgeId = unitId.slice('addEdge:'.length);
  const edge = (patch.addEdges ?? []).find((e) => e.id === edgeId);
  if (!edge) return [];

  const addNodeIds = patchAddNodeIds(patch);
  const hints: string[] = [];

  if (addNodeIds.has(edge.source) && !confirmedUnitIds.has(`addNode:${edge.source}`)) {
    hints.push(formatAddNodeDependencyHint(edge.source, patch));
  }
  if (addNodeIds.has(edge.target) && !confirmedUnitIds.has(`addNode:${edge.target}`)) {
    hints.push(formatAddNodeDependencyHint(edge.target, patch));
  }
  return hints;
}

/** 按钮 tooltip：合并多条依赖为一句 */
export function getStagingConfirmBlockTitle(
  unitId: string,
  patch: FlowDesignPatch,
  confirmedUnitIds: ReadonlySet<string>,
): string {
  const hints = getStagingDependencyHints(unitId, patch, confirmedUnitIds);
  if (!hints.length) return '';
  if (hints.length === 1) return hints[0];
  return `${hints.join('；')}后再确认此连线`;
}

/** 某单元 confirm 前是否应阻断（存在未满足的 addNode 依赖） */
export function isStagingConfirmBlockedByDependencies(
  unitId: string,
  patch: FlowDesignPatch,
  confirmedUnitIds: ReadonlySet<string>,
): boolean {
  return getStagingDependencyHints(unitId, patch, confirmedUnitIds).length > 0;
}

/** 本地依赖检查结果（UI 与 confirm 编排共用） */
export function resolveStagingConfirmDependency(
  unitId: string,
  patch: FlowDesignPatch,
  confirmedUnitIds: ReadonlySet<string>,
) {
  const hints = getStagingDependencyHints(unitId, patch, confirmedUnitIds);
  return {
    hints,
    blocked: hints.length > 0,
    blockTitle: getStagingConfirmBlockTitle(unitId, patch, confirmedUnitIds),
  };
}
