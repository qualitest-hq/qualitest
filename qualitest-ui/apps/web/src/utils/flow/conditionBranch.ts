/** condition 分支「结束流程」(terminal) 判定，供校验层与 UI 共用 */

export function isTerminalBranch(
  branch: { terminal?: boolean } | null | undefined,
): boolean {
  return branch?.terminal === true;
}
