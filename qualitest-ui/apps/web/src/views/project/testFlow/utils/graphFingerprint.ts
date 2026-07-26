/**
 * 流程图指纹工具：计算图快照哈希与结构指纹，供运行库对比与回放错位提示。
 */
import { unwrapGraphPayload } from '@/utils/flow/graphValidate';
import type { GraphJson } from '@/utils/flow/graphTypes';

function parseGraphSnapshot(graph: GraphJson | Record<string, unknown>): GraphJson {
  if (!graph || typeof graph !== 'object' || Array.isArray(graph)) {
    return { nodes: [], edges: [] };
  }
  const unwrapped = unwrapGraphPayload(graph as Record<string, unknown>);
  return {
    nodes: JSON.parse(JSON.stringify(unwrapped.nodes ?? [])),
    edges: JSON.parse(JSON.stringify(unwrapped.edges ?? [])),
    meta: unwrapped.meta != null ? JSON.parse(JSON.stringify(unwrapped.meta)) : undefined,
  };
}

/**
 * 将图对象规范化为持久化形态后序列化为 JSON 字符串。
 * 用于触发 Run 前计算与库表 graph_fingerprint 同源的输入。
 */
export function graphJsonToSnapshotString(graph: GraphJson | Record<string, unknown>): string {
  return JSON.stringify(parseGraphSnapshot(graph));
}

/**
 * 对 snapshot 字符串计算 SHA-256 十六进制摘要。
 */
export async function computeGraphFingerprintFromJson(snapshotJson: string): Promise<string> {
  const data = new TextEncoder().encode(snapshotJson);
  const hash = await crypto.subtle.digest('SHA-256', data);
  return Array.from(new Uint8Array(hash))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}

/**
 * 对图对象计算 SHA-256 指纹。
 */
export async function computeGraphFingerprint(graph: GraphJson | Record<string, unknown>): Promise<string> {
  return computeGraphFingerprintFromJson(graphJsonToSnapshotString(graph));
}

/**
 * 基于排序后的节点 id 与边 id 生成结构指纹。
 * 仅反映拓扑身份，不比较坐标或节点 data 内容。
 */
export function computeStructuralFingerprint(graph: GraphJson | Record<string, unknown>): string {
  const snapshot = parseGraphSnapshot(graph);
  const nodeIds = (snapshot.nodes ?? []).map((n) => n.id).filter(Boolean).sort().join(',');
  const edgeIds = (snapshot.edges ?? []).map((e) => e.id).filter(Boolean).sort().join(',');
  return `${nodeIds}|${edgeIds}`;
}

/**
 * 从 Run 固化 snapshot 字符串解析图并计算结构指纹。
 */
export function structuralFingerprintFromSnapshot(snapshotJson: string | null | undefined): string {
  if (!snapshotJson) return '';
  try {
    return computeStructuralFingerprint(JSON.parse(snapshotJson) as GraphJson);
  } catch {
    return '';
  }
}

/**
 * 判断 Run 快照拓扑与当前画布是否不同。
 * 用于运行详情与回放前提示「图已变更，回放高亮可能错位」。
 */
export function isGraphStructurallyStale(
  snapshotJson: string | null | undefined,
  currentGraph: GraphJson | Record<string, unknown>,
): boolean {
  const saved = structuralFingerprintFromSnapshot(snapshotJson);
  if (!saved) return false;
  const current = computeStructuralFingerprint(currentGraph);
  return saved !== current;
}
