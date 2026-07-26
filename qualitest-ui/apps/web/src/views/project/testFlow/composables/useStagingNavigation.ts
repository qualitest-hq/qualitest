/**
 * Staging 单元在画布与左栏之间的定位导航。
 *
 * 图类型单元（节点/连线）通过 useFlowViewport 移动视口；
 * 场景类型单元切换左栏 Tab 与当前活动场景。
 */
import type { Edge } from '@vue-flow/core';

import type { AiStagingUnit } from '../types/aiStagingTypes';
import { useFlowCanvasStore } from '../stores/flowCanvasStore';
import { useAiStagingStore } from '../stores/aiStagingStore';
import { useFlowViewport } from './useFlowViewport';
import { collectStagingConfirmHighlightIds } from '../utils/mergeHighlight';
import { graphObjectIdFromUnit, isGraphStagingKind } from '../utils/stagingUnitIds';

/**
 * 将视口移动到指定 Staging 图单元（不修改紫色高亮状态）。
 * 用于确认成功后自动跳到下一待确认项，避免覆盖已累积的确认高亮。
 */
export function navigateGraphStagingUnit(unit: AiStagingUnit, edges: Edge[]) {
  const stagingStore = useAiStagingStore();
  const viewport = useFlowViewport();
  const patch = stagingStore.getPatchForMessage(unit.messageId);
  const nodeIds = collectStagingConfirmHighlightIds(unit.unitId, unit.kind, edges, patch);
  if (nodeIds.length) {
    void viewport.focusNodeIds(nodeIds);
  }
}

/**
 * 侧栏用户点击「定位到画布」：临时紫色高亮目标节点，并移动视口到该单元。
 */
export function focusGraphStagingUnit(unit: AiStagingUnit, edges: Edge[]) {
  const store = useFlowCanvasStore();
  const stagingStore = useAiStagingStore();
  const viewport = useFlowViewport();
  const patch = stagingStore.getPatchForMessage(unit.messageId);
  const nodeIds = collectStagingConfirmHighlightIds(unit.unitId, unit.kind, edges, patch);
  if (nodeIds.length) {
    store.setAiHighlightFocus(nodeIds);
    void viewport.focusNodeIds(nodeIds);
  }
}

/**
 * 将左栏切换到运行配置 Tab，并选中指定场景。
 * 用于场景类 Staging 单元（新增/修改/删除场景、切换活动场景）的定位。
 */
export function focusScenarioStagingUnit(scenarioId: string) {
  const store = useFlowCanvasStore();
  store.ui.leftCollapsed = false;
  store.ui.leftTab = 'runConfig';
  store.runConfig.activeScenarioId = scenarioId;
  store.showScenarioPanel();
}

/**
 * 收集当前所有 pending 图类型 Staging 单元涉及的节点 id（去重）。
 * 连线类单元会展开为 source/target 节点 id。
 */
export function collectPendingStagingNodeIds(
  stagingStore: ReturnType<typeof useAiStagingStore>,
  edges: Edge[],
): string[] {
  const ids = new Set<string>();
  for (const unit of Object.values(stagingStore.unitsById)) {
    if (unit.status !== 'pending' || !isGraphStagingKind(unit.kind)) continue;
    const patch = stagingStore.getPatchForMessage(unit.messageId);
    for (const nodeId of collectStagingConfirmHighlightIds(unit.unitId, unit.kind, edges, patch)) {
      ids.add(nodeId);
    }
  }
  return [...ids];
}

/**
 * 首次出现 Staging 待确认项时，将视口移动到所有 pending 图单元的并集区域。
 * 由 FlowCanvasLayout 在 pendingCount 增加时调用。
 */
export function focusPendingStagingGraph(
  stagingStore: ReturnType<typeof useAiStagingStore>,
  edges: Edge[],
) {
  const nodeIds = collectPendingStagingNodeIds(stagingStore, edges);
  if (!nodeIds.length) return;
  void useFlowViewport().focusNodeIds(nodeIds);
}

/** 从场景类 Staging 单元解析目标 scenarioId */
export function resolveScenarioIdFromUnit(unit: AiStagingUnit): string | null {
  if (unit.kind === 'setActiveScenario') {
    const { scenarioId } = graphObjectIdFromUnit(unit);
    return scenarioId ?? null;
  }
  const { scenarioId } = graphObjectIdFromUnit(unit);
  return scenarioId ?? null;
}

/** 在单元列表中查找第一个 pending 的图类型 Staging 单元 */
export function findFirstPendingGraphUnit(units: AiStagingUnit[]): AiStagingUnit | undefined {
  return units.find((unit) => unit.status === 'pending' && isGraphStagingKind(unit.kind));
}

/** 在单元列表中查找第一个 pending 的场景类 Staging 单元 */
export function findFirstPendingScenarioUnit(units: AiStagingUnit[]): AiStagingUnit | undefined {
  return units.find(
    (unit) =>
      unit.status === 'pending' &&
      (unit.kind === 'setActiveScenario' ||
        unit.kind === 'addScenario' ||
        unit.kind === 'updateScenario' ||
        unit.kind === 'deleteScenario'),
  );
}
