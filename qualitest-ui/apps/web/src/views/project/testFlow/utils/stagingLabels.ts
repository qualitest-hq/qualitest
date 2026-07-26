/**
 * Staging 单元展示文案：标题、删除提示、会话摘要 breakdown。
 */
import type { AiStagingMessageSummary } from '../types/aiStagingTypes';
import type { DiffItemKind } from '../types/aiDesignTypes';

type LabelVariant = 'panel' | 'banner';

const PANEL_TITLES: Record<DiffItemKind, string> = {
  addNode: '新增节点',
  updateNode: '修改节点',
  deleteNode: '删除节点',
  addEdge: '新增连线',
  updateEdge: '修改连线',
  deleteEdge: '删除连线',
  setActiveScenario: '切换默认场景',
  addScenario: '新增场景',
  updateScenario: '修改场景',
  deleteScenario: '删除场景',
};

const BANNER_TITLES: Partial<Record<DiffItemKind, string>> = {
  addScenario: '待新增场景',
  updateScenario: '待修改场景',
  deleteScenario: '待删除场景',
  setActiveScenario: '待切换默认场景',
};

export function stagingKindTitle(kind: DiffItemKind, variant: LabelVariant = 'panel'): string {
  if (variant === 'banner') {
    return BANNER_TITLES[kind] ?? PANEL_TITLES[kind];
  }
  return PANEL_TITLES[kind];
}

export function stagingDeleteHint(
  kind: DiffItemKind,
  ctx?: { edgeCount?: number; objectLabel?: string },
): string {
  const label = ctx?.objectLabel ?? '';
  if (kind === 'deleteNode') {
    const edgeCount = ctx?.edgeCount ?? 0;
    return edgeCount > 0
      ? `确认后将删除该节点及 ${edgeCount} 条关联连线`
      : '确认后将删除该节点';
  }
  if (kind === 'deleteEdge') return '确认后将删除此连线';
  if (kind === 'deleteScenario') return '确认后将删除此运行场景';
  if (label) return `待确认删除：${label}`;
  return '待确认删除';
}

export function formatMessageSummaryBreakdown(summary: AiStagingMessageSummary): string {
  const parts: string[] = [];
  if (summary.addNodeCount) parts.push(`新增节点 ${summary.addNodeCount}`);
  if (summary.updateNodeCount) parts.push(`修改节点 ${summary.updateNodeCount}`);
  if (summary.deleteNodeCount) parts.push(`删除节点 ${summary.deleteNodeCount}`);
  if (summary.addEdgeCount) parts.push(`新增连线 ${summary.addEdgeCount}`);
  if (summary.updateEdgeCount) parts.push(`修改连线 ${summary.updateEdgeCount}`);
  if (summary.deleteEdgeCount) parts.push(`删除连线 ${summary.deleteEdgeCount}`);
  if (summary.scenarioCount) parts.push(`场景变更 ${summary.scenarioCount}`);
  return parts.length ? parts.join(' · ') : '无结构化变更';
}
