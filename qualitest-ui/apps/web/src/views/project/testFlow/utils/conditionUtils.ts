/**
 * condition 节点分支数据工具。
 * 负责 branches 规范化、卡片摘要、出边与分支锚点映射、结束出口的读写。
 */
import type { CompareRule } from '@/utils/flow/compareRule';
import {
  hasBranchTarget,
  isTerminalBranch,
} from '@/utils/flow/conditionBranch';
import { condOpLabel, evalCompareRule } from '@/utils/flow/compareRule';
import type { FlowRunContext } from '@/utils/flow/types';

import { defaultConditionBranches } from '../constants/nodeTypes';
import { nextSnowflakeId } from '@/utils/flow/snowflakeId';

/** ELIF 分支数量上限 */
export const MAX_ELIF_COUNT = 5;

/** 单条 condition 分支 */
export interface ConditionBranch {
  id: string;
  kind: 'if' | 'elif' | 'else';
  /** 下游节点 id；缺省或空表示命中后结束本流 */
  target?: string;
  conditions?: CompareRule[];
}

/** 分支是否有有效下游 target */
export const hasConditionBranchTarget = hasBranchTarget;

/** 是否为结束分支（无有效 target） */
export { isTerminalBranch };

/**
 * 规范化 condition 节点 data.branches。
 * - 已有内容的 branches 原样保留（不再因缺 kind:else 盲目补结束 else）
 * - 空/非法则写入默认 IF + ELSE
 */
export function normalizeConditionBranches(data: Record<string, unknown> | null | undefined): void {
  if (!data || typeof data !== 'object') return;

  const branches = data.branches;
  if (Array.isArray(branches) && branches.length) {
    return;
  }

  data.branches = defaultConditionBranches();
}

/** 返回规范化后的 branches 数组 */
export function getConditionBranches(data: Record<string, unknown> | null | undefined): ConditionBranch[] {
  if (!data || typeof data !== 'object') return [];
  normalizeConditionBranches(data);
  return (data.branches as ConditionBranch[]) ?? [];
}

/** 分支 kind 对应的画布/边标签文案 */
export function branchKindLabel(branch: Pick<ConditionBranch, 'kind'>): string {
  if (branch.kind === 'if') return 'IF';
  if (branch.kind === 'elif') return 'ELIF';
  return 'ELSE';
}

/** 单条比较规则的可读摘要 */
export function formatCondRule(rule: CompareRule | Record<string, unknown> | null | undefined): string {
  if (!rule || !String(rule.left || '').trim()) return '未配置条件';
  const left = String(rule.left);
  if (rule.operator === 'exists') return `${left} ${condOpLabel('exists')}`;
  const right = rule.right != null && String(rule.right) !== '' ? ` ${rule.right}` : '';
  return `${left} ${condOpLabel(String(rule.operator))}${right}`;
}

/** 单条分支在节点卡片上的摘要文案 */
export function formatBranchSummary(branch: ConditionBranch): string {
  if (branch.kind === 'else') {
    return hasConditionBranchTarget(branch) ? '默认分支' : '→ 结束';
  }
  const rules = (branch.conditions || []).filter((r) => String(r.left || '').trim());
  if (!rules.length) {
    return hasConditionBranchTarget(branch) ? '点击配置条件' : '→ 结束';
  }
  // 已配置条件时优先展示条件；有无出口由连线/target 表达，不改写条件文案
  return rules.map(formatCondRule).join(' 且 ');
}

/** 节点 data.summary：取首条非 else 分支的摘要 */
export function formatConditionSummary(data: Record<string, unknown> | null | undefined): string {
  const branches = getConditionBranches(data);
  const first = branches.find((b) => b.kind !== 'else');
  if (!first) return '条件分支';
  return `${branchKindLabel(first)} ${formatBranchSummary(first)}`;
}

/** 判断单条分支条件是否成立（else 恒 true；if/elif 为 conditions 的 AND） */
export function evalBranchConditions(
  branch: Pick<ConditionBranch, 'kind' | 'conditions'>,
  ctx: FlowRunContext,
): boolean {
  if (branch.kind === 'else') return true;
  const rules = branch.conditions || [];
  if (!rules.length) return false;
  return rules.every((r) => String(r.left || '').trim() && evalCompareRule(r, ctx));
}

/** 按分支顺序求值：返回第一条命中分支及其对应出边；结束分支时 edge 为 null */
export function pickConditionEdge(
  node: { id: string; data?: Record<string, unknown> },
  edges: Array<{ id: string; source: string; target: string }>,
  ctx: FlowRunContext,
): { edge: { id: string; source: string; target: string } | null; branch: ConditionBranch | null } {
  const branches = getConditionBranches(node.data);
  for (const branch of branches) {
    if (!evalBranchConditions(branch, ctx)) continue;
    if (isTerminalBranch(branch)) return { edge: null, branch };
    const targetId = branch.target;
    const edge = edges.find((e) => e.source === node.id && e.target === targetId);
    return { edge: edge ?? null, branch };
  }
  return { edge: null, branch: null };
}

/**
 * 从分支锚点 handle id 解析 branchId。
 * handle 格式为 out-<branchId>。
 */
export function resolveBranchFromHandle(handle: string | null | undefined): string | null {
  if (!handle || !handle.startsWith('out-')) return null;
  return handle.slice(4) || null;
}

/** 为 condition 出边补全 type 与 sourceHandle，便于 vue-flow 挂到正确分支锚点 */
export function applyConditionEdgeProps(
  edge: { type?: string; sourceHandle?: string | null; target?: string },
  srcNode: { type?: string; data?: Record<string, unknown> } | null | undefined,
  rawEdge?: { target?: string; sourceHandle?: string | null },
): void {
  if (srcNode?.type !== 'condition') return;
  edge.type = 'condition';
  edge.sourceHandle = rawEdge?.sourceHandle ?? getConditionEdgeHandle(srcNode, edge);
}

/**
 * 根据 condition 源节点与边的 target，反推边应挂载的分支锚点 id（out-{branchId}）。
 * 先按 branches[].target 匹配；再回退到首条尚无 target 的分支；都没有则用第一条分支。
 */
export function getConditionEdgeHandle(
  srcNode: { type?: string; data?: Record<string, unknown> } | null | undefined,
  edge: { target?: string },
): string {
  if (srcNode?.type !== 'condition') return 'out';
  const branches = getConditionBranches(srcNode.data);
  const byTarget = branches.find((b) => b.target === edge.target);
  if (byTarget) return `out-${byTarget.id}`;
  // 找不到「out」通用锚点时：挂到首条尚未绑定 target 的分支，避免边因无效 handle 不渲染
  const unbound = branches.find((b) => !hasConditionBranchTarget(b));
  if (unbound) return `out-${unbound.id}`;
  const fallback = branches[0];
  return fallback ? `out-${fallback.id}` : 'out';
}

/** 统计当前 ELIF 分支数量 */
export function countElifBranches(branches: ConditionBranch[]): number {
  return branches.filter((b) => b.kind === 'elif').length;
}

/** 是否还能继续添加 ELIF */
export function canAddElifBranch(branches: ConditionBranch[]): boolean {
  return countElifBranches(branches) < MAX_ELIF_COUNT;
}

/** 生成新 ELIF 分支 id（雪花数字串） */
export function createElifBranchId(_branches: ConditionBranch[]): string {
  return nextSnowflakeId();
}

/**
 * 将 condition 出边的 target 写入 data.branches[].target。
 *
 * 有 sourceHandle（out-{branchId}）时写入对应分支；否则若已有分支指向同一 target 则跳过；
 * 再否则写入第一条尚无 target 的分支。
 *
 * @returns 是否实际修改了 branches
 */
export function bindConditionBranchTarget(
  nodeData: Record<string, unknown>,
  edge: { target?: string; sourceHandle?: string | null },
): boolean {
  if (!edge.target) return false;
  const branches = getConditionBranches(nodeData).map((b) => ({ ...b }));
  let branchId = resolveBranchFromHandle(edge.sourceHandle);
  if (!branchId) {
    if (branches.some((b) => b.target === edge.target)) return false;
    const unbound = branches.find((b) => !hasConditionBranchTarget(b));
    if (!unbound) return false;
    branchId = unbound.id;
  }
  const branch = branches.find((b) => b.id === branchId);
  if (!branch || branch.target === edge.target) return false;
  branch.target = edge.target;
  nodeData.branches = branches;
  return true;
}

/**
 * 切换分支结束出口。
 * asEnd=true：清除 target（命中后结束本流）。<br>
 * asEnd=false：不自动恢复出边（需用户再连线）。
 */
export function setBranchTerminal(
  nodeData: Record<string, unknown>,
  branchId: string,
  asEnd: boolean,
): boolean {
  const branches = getConditionBranches(nodeData).map((b) => ({ ...b }));
  const branch = branches.find((b) => b.id === branchId);
  if (!branch) return false;
  if (asEnd) delete branch.target;
  nodeData.branches = branches;
  return true;
}

/**
 * 清除 branches 中所有指向给定 target 的绑定。
 * 删边或分支改连其他节点前调用。
 *
 * @returns 是否清除了至少一条 target
 */
export function clearConditionBranchTarget(nodeData: Record<string, unknown>, target: string): boolean {
  const branches = getConditionBranches(nodeData).map((b) => ({ ...b }));
  let changed = false;
  branches.forEach((b) => {
    if (b.target === target) {
      b.target = undefined;
      changed = true;
    }
  });
  if (changed) nodeData.branches = branches;
  return changed;
}
