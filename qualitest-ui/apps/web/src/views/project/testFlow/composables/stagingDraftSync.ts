/**
 * Staging 单元在属性面板编辑后回写 store.draft。
 */
import { useAiStagingStore } from '../stores/aiStagingStore';
import { useFlowCanvasStore } from '../stores/flowCanvasStore';
import {
  buildDraftFromCanvasEdge,
  buildDraftFromCanvasNode,
} from './useAiStagingCanvas';
import { buildDraftFromCanvasScenario } from './useAiStagingScenario';

export function syncStagingDraftFromNode(nodeId: string) {
  const stagingStore = useAiStagingStore();
  const mark = stagingStore.stagingByNodeId[nodeId];
  if (!mark) return;

  const unit = stagingStore.getUnit(mark.unitId);
  if (!unit || unit.status !== 'pending') return;
  if (unit.kind !== 'addNode' && unit.kind !== 'updateNode') return;

  const store = useFlowCanvasStore();
  const node = store.nodes.find((n) => n.id === nodeId);
  if (!node) return;

  stagingStore.updateDraft(mark.unitId, buildDraftFromCanvasNode(node));
}

export function syncStagingDraftFromEdge(edgeId: string) {
  const stagingStore = useAiStagingStore();
  const mark = stagingStore.stagingByEdgeId[edgeId];
  if (!mark) return;

  const unit = stagingStore.getUnit(mark.unitId);
  if (!unit || unit.status !== 'pending') return;
  if (unit.kind !== 'addEdge' && unit.kind !== 'updateEdge') return;

  const store = useFlowCanvasStore();
  const edge = store.edges.find((e) => e.id === edgeId);
  if (!edge) return;

  stagingStore.updateDraft(mark.unitId, buildDraftFromCanvasEdge(edge));
}

export function syncStagingDraftFromScenario(scenarioId: string) {
  const stagingStore = useAiStagingStore();
  const mark = stagingStore.stagingByScenarioId[scenarioId];
  if (!mark) return;

  const unit = stagingStore.getUnit(mark.unitId);
  if (!unit || unit.status !== 'pending') return;
  if (unit.kind !== 'addScenario' && unit.kind !== 'updateScenario') return;

  const store = useFlowCanvasStore();
  const scenario = store.runConfig.scenarios.find((s) => s.id === scenarioId);
  if (!scenario) return;

  stagingStore.updateDraft(mark.unitId, buildDraftFromCanvasScenario(scenario));
}
