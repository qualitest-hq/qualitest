/**
 * 测试流图 JSON 本地地板校验。
 *
 * 业务规则以服务端 POST /project/testFlow/validate?stage= 为准；
 * 本模块只做无需网络的即时检查：JSON 可解析、nodes/edges 形状、节点 id、边端点。
 * deferTopologyStructureRules 保留类型兼容，地板阶段无拓扑规则可延后。
 */
import { isAnyEntryType, nodeTypeLabel } from './nodeTypes';
import type { GraphEdge, GraphJson, GraphNode } from './graphTypes';

/** 图校验结果；ok 为 true 当且仅当 errors 为空 */
export interface GraphValidationResult {
  ok: boolean;
  errors: string[];
  warnings: string[];
}

/** 开始节点校验结果（有节点须有入口；允许多入口并存） */
export interface StartNodesValidation {
  ok: boolean;
  ids: string[];
  /** 首条错误文案；无错误时为空串 */
  message: string;
  /** 全部错误文案（入口规则下可能多条） */
  messages: string[];
}

export type ValidateGraphJsonOptions = {
  /**
   * true：暂不校验开始节点（半成品）。地板校验忽略此选项，保留仅为兼容旧调用。
   */
  deferTopologyStructureRules?: boolean;
  /**
   * true：只做落库地板。本模块始终按地板执行，传 false 也不会跑业务规则。
   */
  persistMinimalOnly?: boolean;
};

/** 从 graph_json / graphJson 包装中取出图对象 */
export function unwrapGraphPayload(raw: Record<string, unknown>): Record<string, unknown> {
  if (raw.graph_json != null && typeof raw.graph_json === 'object' && !Array.isArray(raw.graph_json)) {
    return raw.graph_json as Record<string, unknown>;
  }
  if (raw.graphJson != null && typeof raw.graphJson === 'object' && !Array.isArray(raw.graphJson)) {
    return raw.graphJson as Record<string, unknown>;
  }
  return raw;
}

/** 列出无入边节点 id（候选开始节点；同一 id 只计一次） */
export function findStartNodeIds(graph: Pick<GraphJson, 'nodes' | 'edges'>): string[] {
  const nodes = graph.nodes ?? [];
  const edges = graph.edges ?? [];
  if (!nodes.length) return [];
  const hasIncoming = new Set(edges.map((e) => e.target));
  const uniqueIds = [...new Set(nodes.map((n) => n.id).filter((id): id is string => Boolean(id)))];
  return uniqueIds.filter((id) => !hasIncoming.has(id));
}

/** 节点展示名：优先顶层 name，否则类型中文名，再否则 id */
function resolveGraphNodeName(node: Pick<GraphNode, 'id' | 'name' | 'type'> | null | undefined): string {
  const top = node?.name;
  if (top != null && String(top).trim()) return String(top).trim();
  return nodeTypeLabel(node?.type) || String(node?.id ?? '');
}

/**
 * 校验开始节点（供校验条文案定位「开始节点」类问题）。
 * 空白图通过；有节点须至少有一个入口，且仅入口可作无入边起点。
 */
export function validateStartNodes(graph: Pick<GraphJson, 'nodes' | 'edges'>): StartNodesValidation {
  const nodes = graph.nodes ?? [];
  const ids = findStartNodeIds(graph);
  if (!nodes.length) {
    return { ok: true, ids, message: '', messages: [] };
  }

  const messages: string[] = [];
  const hasEntry = nodes.some((n) => isAnyEntryType(n?.type));

  if (!hasEntry) {
    messages.push('须至少配置一个入口节点（场景 / HTTP / 子流入口）才能开跑');
    return {
      ok: false,
      ids,
      message: messages[0],
      messages,
    };
  }

  if (ids.length === 0) {
    messages.push('未找到开始节点（入口须无入边）');
  } else {
    const byId = new Map(nodes.map((n) => [n.id, n]));
    for (const id of ids) {
      const node = byId.get(id);
      if (!node || !isAnyEntryType(node.type)) {
        messages.push(
          `存在非入口的无入边节点：${resolveGraphNodeName(node) || id}（有入口时仅入口可作开始）`,
        );
      }
    }
    for (const n of nodes) {
      if (!n || !isAnyEntryType(n.type)) continue;
      if (n.id && !ids.includes(n.id)) {
        messages.push(`入口节点须无入边：${resolveGraphNodeName(n)}`);
      }
    }
  }

  return {
    ok: messages.length === 0,
    ids,
    message: messages[0] ?? '',
    messages,
  };
}

/**
 * 本地地板校验：nodes/edges 为数组、节点非空不重复 id、边有 source/target。
 * 业务结构规则请调服务端 validate。
 */
export function validateGraphJson(
  raw: unknown,
  _options?: ValidateGraphJsonOptions,
): GraphValidationResult {
  const errors: string[] = [];
  const warnings: string[] = [];

  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    return { ok: false, errors: ['根对象必须是 JSON 对象'], warnings };
  }

  const graph = unwrapGraphPayload(raw as Record<string, unknown>);
  const nodes = graph.nodes;
  const edges = graph.edges;

  if (!Array.isArray(nodes)) errors.push('缺少 nodes 数组');
  if (!Array.isArray(edges)) errors.push('缺少 edges 数组');
  if (errors.length) return { ok: false, errors, warnings };

  const nodeIds = new Set<string>();
  (nodes as GraphNode[]).forEach((node, i) => {
    const p = `nodes[${i}]`;
    if (!node || typeof node !== 'object') {
      errors.push(`${p} 不是有效对象`);
      return;
    }
    const id = node.id;
    if (!id || typeof id !== 'string' || !id.trim()) {
      errors.push(`${p} 缺少 id`);
      return;
    }
    if (nodeIds.has(id)) {
      errors.push(`nodes 存在重复 id：${id}`);
    } else {
      nodeIds.add(id);
    }
  });
  (edges as GraphEdge[]).forEach((edge, i) => {
    const p = `edges[${i}]`;
    if (!edge || typeof edge !== 'object') {
      errors.push(`${p} 不是有效对象`);
      return;
    }
    if (!edge.source) errors.push(`${p} 缺少 source`);
    if (!edge.target) errors.push(`${p} 缺少 target`);
  });
  return { ok: errors.length === 0, errors, warnings };
}
