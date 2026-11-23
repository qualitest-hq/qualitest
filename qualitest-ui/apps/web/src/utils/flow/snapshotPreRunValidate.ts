/**
 * 跑流前静态校验：图中含执行前快照节点，且环境允许破坏性还原时，
 * 必须已启用声明 onCheckpoint 的插件；未启用则返回错误文案拦下运行。
 */
import type { GraphJson } from './graphTypes';

/** 快照前置校验结果 */
export interface SnapshotResetEndpointValidation {
  /** true 表示可继续跑流 */
  ok: boolean;
  /** 失败时的用户可见说明 */
  message: string;
}

/** 有快照节点且环境允许还原，但未启用 onCheckpoint 插件时的固定错误文案 */
export const SNAPSHOT_PLUGIN_REQUIRED_ERROR =
  '当前环境已开启数据还原且流程含快照节点，请先启用声明 onCheckpoint 的插件（如库存档点）并完成项目挂载';

/** 旧导出名，取值同 SNAPSHOT_PLUGIN_REQUIRED_ERROR，保留以免旧引用失效 */
export const SNAPSHOT_RESET_ENDPOINT_ERROR = SNAPSHOT_PLUGIN_REQUIRED_ERROR;

/**
 * 判断节点 data 是否开启执行前快照。
 */
export function isSnapshotBeforeNode(data: Record<string, unknown> | null | undefined): boolean {
  if (!data) return false;
  return data.snapshotBefore === true;
}

/** 图中是否存在任一开启执行前快照的节点 */
export function hasSnapshotBeforeNodes(graph: Pick<GraphJson, 'nodes'>): boolean {
  const nodes = graph.nodes ?? [];
  return nodes.some((n) => isSnapshotBeforeNode(n.data as Record<string, unknown> | undefined));
}

/** 运行场景绑定的环境（本校验用到的字段） */
export interface SnapshotResetEnvInput {
  /** 1 表示允许破坏性还原 */
  allowDestructiveReset?: number;
  /** 是否已启用声明 onCheckpoint 的插件 */
  checkpointPluginEnabled?: boolean;
  /** @deprecated 兼容旧字段，等同 checkpointPluginEnabled */
  savePointPluginEnabled?: boolean;
  envUrl?: string | null;
}

/**
 * 校验快照前置条件。
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
  if (env.checkpointPluginEnabled === true || env.savePointPluginEnabled === true) {
    return { ok: true, message: '' };
  }
  return { ok: false, message: SNAPSHOT_PLUGIN_REQUIRED_ERROR };
}
