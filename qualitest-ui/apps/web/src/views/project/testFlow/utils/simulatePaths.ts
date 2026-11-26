/**
 * 路径模拟：自唯一开始节点 DFS 枚举全部出边组合（不评估 condition 条件）。
 * 非 condition 的多出边是隐式扇出（各臂都跑），路径 label 会标注「扇出各臂」而非互斥分支。
 */
import { SIMULATE_MAX_LOOP_EDGE_USES, SIMULATE_MAX_PATH_STEPS } from '../constants/flowConfig';

export interface SimulatePath {
  nodeIds: string[];
  edgeIds: Array<string | null>;
  label: string;
  /** 本条路径是否经过隐式扇出（非 condition 多出边） */
  fanout: boolean;
}

function edgeLabel(
  edge: { id: string; source: string; target: string; label?: string } | undefined,
  nodeNameById: Map<string, string>,
): string {
  if (!edge) return '';
  if (edge.label?.trim()) return edge.label.trim();
  const targetName = nodeNameById.get(edge.target);
  return targetName || edge.target;
}

function formatPathLabel(
  edgeIds: Array<string | null>,
  edges: Array<{ id: string; source: string; target: string; label?: string }>,
  nodeNameById: Map<string, string>,
): string {
  const labels = edgeIds
    .filter(Boolean)
    .map((id) => edgeLabel(edges.find((e) => e.id === id), nodeNameById))
    .filter(Boolean);
  if (!labels.length) return '顺序执行';
  return labels.slice(0, 4).join(' → ') + (labels.length > 4 ? ' …' : '');
}

/** DFS 枚举从 startNodeId 出发的全部路径（condition 展开全部出边） */
export function enumerateSimulatePaths(
  edges: Array<{ id: string; source: string; target: string; label?: string }>,
  startNodeId: string,
  nodeNameById: Map<string, string> = new Map(),
  nodeTypeById: Map<string, string> = new Map(),
): SimulatePath[] {
  const paths: SimulatePath[] = [];
  const seenKeys = new Set<string>();

  /** 是否是隐式扇出臂：源节点非 condition 且出边 > 1 */
  function isFanoutEdge(edgeId: string | null): boolean {
    if (!edgeId) return false;
    const edge = edges.find((e) => e.id === edgeId);
    if (!edge) return false;
    if (nodeTypeById.get(edge.source) === 'condition') return false;
    return edges.filter((e) => e.source === edge.source).length > 1;
  }

  function recordPath(nodeIds: string[], edgeIds: Array<string | null>) {
    const key = nodeIds.join('>');
    if (seenKeys.has(key)) return;
    seenKeys.add(key);
    const fanout = edgeIds.some((id) => isFanoutEdge(id));
    const baseLabel = formatPathLabel(edgeIds, edges, nodeNameById);
    paths.push({
      nodeIds: [...nodeIds],
      edgeIds: [...edgeIds],
      label: fanout ? `扇出各臂 · ${baseLabel}` : baseLabel,
      fanout,
    });
  }

  function dfs(
    nodeIds: string[],
    edgeIds: Array<string | null>,
    edgeUseCount: Map<string, number>,
    currentId: string,
  ) {
    if (nodeIds.length >= SIMULATE_MAX_PATH_STEPS) {
      recordPath(nodeIds, edgeIds);
      return;
    }
    const outs = edges.filter((e) => e.source === currentId);
    if (!outs.length) {
      recordPath(nodeIds, edgeIds);
      return;
    }
    let extended = false;
    for (const edge of outs) {
      const next = edge.target;
      const onPath = nodeIds.includes(next);
      const used = edgeUseCount.get(edge.id) || 0;
      if (onPath && used >= SIMULATE_MAX_LOOP_EDGE_USES) continue;
      extended = true;
      edgeUseCount.set(edge.id, used + 1);
      dfs([...nodeIds, next], [...edgeIds, edge.id], edgeUseCount, next);
      edgeUseCount.set(edge.id, used);
    }
    if (!extended) recordPath(nodeIds, edgeIds);
  }

  dfs([startNodeId], [null], new Map(), startNodeId);
  return paths;
}
