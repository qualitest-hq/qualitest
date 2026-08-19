/**
 * AI patch 试算 base graph_json 的稳定哈希计算。
 *
 * 试算接口返回 baseGraphHash，合并前对当前画布重新计算 hash 并比对；
 * 不一致说明用户在试算后手改了画布，应阻断合并并等待重新试算。
 *
 * 哈希规则：节点/边按 id 排序 → 对象键递归排序序列化 → SHA-256 取前 16 位 hex。
 */
import type { GraphJson } from '@/utils/flow/graphTypes';

import type { ToGraphJsonInput } from '../graphAdapter';
import { toGraphJson } from '../graphAdapter';

/** 递归稳定 JSON 序列化，对象键按字典序排列，保证相同图结构产生相同字符串 */
export function stableStringify(value: unknown): string {
  if (value === null || typeof value !== 'object') {
    return JSON.stringify(value);
  }
  if (Array.isArray(value)) {
    return `[${value.map((item) => stableStringify(item)).join(',')}]`;
  }
  const record = value as Record<string, unknown>;
  const keys = Object.keys(record).sort();
  const entries = keys
    .filter((key) => record[key] !== undefined)
    .map((key) => `${JSON.stringify(key)}:${stableStringify(record[key])}`);
  return `{${entries.join(',')}}`;
}

/** 复制 graph_json 并按节点 id、边 id 排序，消除数组顺序对哈希的影响 */
export function canonicalizeGraphJsonForHash(graph: GraphJson): GraphJson {
  return {
    nodes: [...(graph.nodes ?? [])].sort((a, b) => a.id.localeCompare(b.id)),
    edges: [...(graph.edges ?? [])].sort((a, b) => a.id.localeCompare(b.id)),
    meta: graph.meta != null ? JSON.parse(JSON.stringify(graph.meta)) : undefined,
  };
}

/** 返回 canonical 图结构的稳定 JSON 字符串，用于 equality 比对与哈希输入 */
export function canonicalGraphJsonString(graph: GraphJson): string {
  return stableStringify(canonicalizeGraphJsonForHash(graph));
}

async function sha256Prefix(input: string): Promise<string> {
  const data = new TextEncoder().encode(input);
  const hash = await crypto.subtle.digest('SHA-256', data);
  const hex = Array.from(new Uint8Array(hash))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
  return hex.substring(0, 16);
}

/** 对 graph_json 对象计算 16 位 baseGraphHash */
export async function computeBaseGraphHashFromGraphJson(graph: GraphJson): Promise<string> {
  return sha256Prefix(canonicalGraphJsonString(graph));
}

/** 从画布编辑态（节点、边、视口、场景配置）计算 baseGraphHash，与 preview 请求的 graphJson 同源 */
export async function computeBaseGraphHash(input: ToGraphJsonInput): Promise<string> {
  return computeBaseGraphHashFromGraphJson(toGraphJson(input));
}

/**
 * 判断是否应阻断 confirm（画布在请求期间已变更）。
 */
export function shouldBlockConfirmByBaseGraphHash(
  expectedBaseGraphHash: string | undefined,
  currentHash: string,
): boolean {
  const expected = expectedBaseGraphHash?.trim();
  if (!expected) return false;
  return expected !== currentHash;
}
