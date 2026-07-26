/**
 * 运行前静态派生校验：snapshotBefore 节点 + 环境 allowDestructiveReset 时，
 * 检查 envUrl 能否本地派生出 test-support 根路径（不发 HTTP）。
 */
import { resolveResetBaseUrl } from '@/views/project/testProject/utils/envConfigUtils';

import type { GraphJson } from './graphTypes';

/** 快照 reset 端点静态校验结果 */
export interface SnapshotResetEndpointValidation {
  ok: boolean;
  message: string;
}

/** 运行前校验失败时的用户提示（与后端 TF_SNAPSHOT_ENDPOINT 语义一致） */
export const SNAPSHOT_RESET_ENDPOINT_ERROR =
  '当前环境已开启数据还原且流程含快照节点，但无法从前置 URL 派生 test-support 端点，请检查环境配置';

/** 解析节点 data.snapshotBefore，与后端 GraphNodeSnapshotSupport 对齐 */
export function isSnapshotBeforeNode(data: Record<string, unknown> | null | undefined): boolean {
  if (!data) return false;
  const raw = data.snapshotBefore;
  if (raw === true || raw === 1) return true;
  if (raw === 'true' || raw === '1') return true;
  return false;
}

/** 图中是否存在开启 snapshotBefore 的节点 */
export function hasSnapshotBeforeNodes(graph: Pick<GraphJson, 'nodes'>): boolean {
  const nodes = graph.nodes ?? [];
  return nodes.some((n) => isSnapshotBeforeNode(n.data as Record<string, unknown> | undefined));
}

/** 运行场景绑定的环境（校验所需字段） */
export interface SnapshotResetEnvInput {
  allowDestructiveReset?: number;
  envUrl?: string | null;
}

/**
 * 静态派生 test-support 端点；仅在「有快照节点且环境允许还原」时校验。
 * 环境未开还原时 checkpoint 会静默跳过，此处直接放行。
 */
export function validateSnapshotResetEndpointStatic(
  graph: Pick<GraphJson, 'nodes'>,
  env: SnapshotResetEnvInput | null | undefined,
): SnapshotResetEndpointValidation {
  if (!hasSnapshotBeforeNodes(graph)) {
    return { ok: true, message: '' };
  }
  if (!env || Number(env.allowDestructiveReset) !== 1) {
    return { ok: true, message: '' };
  }
  const resetBase = resolveResetBaseUrl(env.envUrl ?? '');
  if (!resetBase) {
    return { ok: false, message: SNAPSHOT_RESET_ENDPOINT_ERROR };
  }
  return { ok: true, message: '' };
}
