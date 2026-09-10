/**
 * 条件分支结束判定。
 *
 * 有有效 target：命中后往下游走。
 * 无有效 target：命中后本流正常结束。
 */

/** 分支是否配置了有效下游 target（空串 / 字面量 null 视为无效） */
export function hasBranchTarget(
  branch: { target?: unknown } | null | undefined,
): boolean {
  if (branch == null) return false;
  const target = branch.target;
  if (target == null) return false;
  const s = String(target).trim();
  return s !== '' && s.toLowerCase() !== 'null';
}

/** 是否为结束分支：命中后不再走向下一节点（没有有效 target） */
export function isTerminalBranch(
  branch: { target?: unknown } | null | undefined,
): boolean {
  return !hasBranchTarget(branch);
}
