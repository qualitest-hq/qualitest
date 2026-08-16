/**
 * Staging unitId 解析与 kind 分类（唯一真相源）。
 */
import type { AiStagingCanvasMark, AiStagingUnit } from '../types/aiStagingTypes';
import type { DiffItemKind } from '../types/aiDesignTypes';

const GRAPH_KINDS = new Set<DiffItemKind>([
  'addNode',
  'updateNode',
  'deleteNode',
  'addEdge',
  'updateEdge',
  'deleteEdge',
]);

const SCENARIO_KINDS = new Set<DiffItemKind>([
  'setActiveScenario',
  'addScenario',
  'updateScenario',
  'deleteScenario',
]);

const UNIT_ID_PREFIXES: Record<string, string> = {
  addNode: 'addNode:',
  updateNode: 'updateNode:',
  deleteNode: 'deleteNode:',
  addEdge: 'addEdge:',
  updateEdge: 'updateEdge:',
  deleteEdge: 'deleteEdge:',
  addScenario: 'addScenario:',
  updateScenario: 'updateScenario:',
  deleteScenario: 'deleteScenario:',
};

export function isGraphStagingKind(kind: DiffItemKind | string): boolean {
  return GRAPH_KINDS.has(kind as DiffItemKind);
}

/** 连线类 Staging（增/改/删边，会影响开始节点拓扑判断） */
const EDGE_STAGING_KINDS = new Set<DiffItemKind>(['addEdge', 'updateEdge', 'deleteEdge']);

/** 是否为连线类 Staging kind */
export function isEdgeStagingKind(kind: DiffItemKind | string): boolean {
  return EDGE_STAGING_KINDS.has(kind as DiffItemKind);
}

/** 是否存在 status=pending 的连线类 Staging 单元 */
export function hasPendingStagingEdgeUnits(
  units: Iterable<Pick<AiStagingUnit, 'status' | 'kind'>>,
): boolean {
  for (const u of units) {
    if (u.status === 'pending' && isEdgeStagingKind(u.kind)) {
      return true;
    }
  }
  return false;
}

export function isScenarioStagingKind(kind: DiffItemKind | string): boolean {
  return SCENARIO_KINDS.has(kind as DiffItemKind);
}

/** @alias isScenarioStagingKind */
export function isScenarioKind(kind: DiffItemKind | string): boolean {
  return isScenarioStagingKind(kind);
}

export function stagingKindToCanvasMode(kind: AiStagingUnit['kind']): AiStagingCanvasMark['mode'] | null {
  if (kind === 'addNode' || kind === 'addEdge' || kind === 'addScenario') return 'add';
  if (kind === 'updateNode' || kind === 'updateEdge' || kind === 'updateScenario' || kind === 'setActiveScenario') {
    return 'update';
  }
  if (kind === 'deleteNode' || kind === 'deleteEdge' || kind === 'deleteScenario') return 'delete';
  return null;
}

/** 是否为删除类 Staging 单元（节点 / 边 / 运行场景） */
export function isDeleteStagingUnit(unit: Pick<AiStagingUnit, 'kind'> | null | undefined): boolean {
  if (!unit) return false;
  return stagingKindToCanvasMode(unit.kind) === 'delete';
}

/** 从 unitId 提取对象 id（不含 kind 前缀） */
export function objectIdFromUnitId(unitId: string): string {
  if (unitId === 'scenario:activeScenarioId') return '';
  const colon = unitId.indexOf(':');
  if (colon < 0) return unitId;
  return unitId.slice(colon + 1);
}

export function graphObjectIdFromUnit(unit: AiStagingUnit | Pick<AiStagingUnit, 'unitId' | 'kind' | 'draft' | 'patchSlice'>): {
  nodeId?: string;
  edgeId?: string;
  scenarioId?: string;
} {
  const { unitId, kind } = unit;
  if (kind === 'addNode' || kind === 'updateNode' || kind === 'deleteNode') {
    return { nodeId: objectIdFromUnitId(unitId) };
  }
  if (kind === 'addEdge' || kind === 'updateEdge' || kind === 'deleteEdge') {
    return { edgeId: objectIdFromUnitId(unitId) };
  }
  if (kind === 'addScenario' || kind === 'updateScenario' || kind === 'deleteScenario') {
    return { scenarioId: objectIdFromUnitId(unitId) };
  }
  if (kind === 'setActiveScenario') {
    const targetId =
      unit.draft?.activeScenarioId ??
      (unit.patchSlice as { activeScenarioId?: string } | undefined)?.activeScenarioId;
    return targetId ? { scenarioId: String(targetId) } : {};
  }
  return {};
}

export function scenarioIdFromUnit(unit: AiStagingUnit): string {
  return objectIdFromUnitId(unit.unitId);
}

export function unitIdPrefixForKind(kind: DiffItemKind): string | undefined {
  return UNIT_ID_PREFIXES[kind];
}
