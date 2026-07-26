/**
 * Staging 节点/边 draft 与 baseline 字段合并（apply / restore 共用）。
 * project HTTP 浅合并后删除整份 requestConfig、临时 requestBody、apiPath。
 */
import type { Edge, Node } from '@vue-flow/core';
import { isExternalCallMode } from './httpSummary';

/** 合并节点 type / position / data；project HTTP 会剥掉厚请求字段与路径 */
export function mergeNodeFields(
  existing: Node,
  fields: {
    type?: string;
    position?: { x: number; y: number };
    data?: Record<string, unknown>;
  },
): Node {
  const type = fields.type ?? existing.type;
  let data: Record<string, unknown> = fields.data != null
    ? { ...(existing.data as Record<string, unknown>), ...fields.data }
    : { ...(existing.data as Record<string, unknown>) };

  if (type === 'http' && !isExternalCallMode(String(data.callMode ?? ''))) {
    delete data.requestConfig;
    delete data.requestBody;
    delete data.apiPath;
  }

  return {
    ...existing,
    type,
    position: fields.position ? { ...fields.position } : existing.position,
    data,
  };
}

/** 合并边的 source / target / label */
export function mergeEdgeFields(
  existing: Edge,
  fields: { source?: string; target?: string; label?: string },
): Edge {
  const next: Edge = { ...existing };
  if (fields.source) next.source = fields.source;
  if (fields.target) next.target = fields.target;
  if (fields.label !== undefined) {
    const label = String(fields.label ?? '').trim();
    if (label) next.label = label;
    else delete next.label;
  }
  return next;
}
