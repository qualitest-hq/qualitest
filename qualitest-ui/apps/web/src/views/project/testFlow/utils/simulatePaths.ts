/**
 * 路径模拟：自唯一开始节点 DFS 枚举全部出边组合（不评估 condition 条件）。
 */
import { SIMULATE_MAX_LOOP_EDGE_USES, SIMULATE_MAX_PATH_STEPS } from '../constants/flowConfig';

export interface SimulatePath {
  nodeIds: string[];
  edgeIds: Array<string | null>;
  label: string;
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
): SimulatePath[] {
  const paths: SimulatePath[] = [];
  const seenKeys = new Set<string>();

  function recordPath(nodeIds: string[], edgeIds: Array<string | null>) {
    const key = nodeIds.join('>');
    if (seenKeys.has(key)) return;
    seenKeys.add(key);
    paths.push({
      nodeIds: [...nodeIds],
      edgeIds: [...edgeIds],
      label: formatPathLabel(edgeIds, edges, nodeNameById),
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
