/**
 * Staging 单元在画布与左栏之间的定位导航。
 *
 * 图类型单元：视口聚焦并 selectItem 打开右栏对照（关闭 AI）；
 * 场景类型单元：切换左栏运行场景 Tab 并打开右栏运行配置（关闭 AI）。
 */
import type { Edge } from '@vue-flow/core';

import type { AiStagingUnit } from '../types/aiStagingTypes';
import { useFlowCanvasStore } from '../stores/flowCanvasStore';
import { useAiStagingStore } from '../stores/aiStagingStore';
import { useFlowViewport } from './useFlowViewport';
import { collectStagingConfirmHighlightIds } from '../utils/mergeHighlight';
import {
  graphObjectIdFromUnit,
  isGraphStagingKind,
  isScenarioStagingKind,
} from '../utils/stagingUnitIds';

function resolveUnitHighlightNodeIds(unit: AiStagingUnit, edges: Edge[]): string[] {
  const stagingStore = useAiStagingStore();
  const patch = stagingStore.getPatchForMessage(unit.messageId);
  return collectStagingConfirmHighlightIds(unit.unitId, unit.kind, edges, patch);
}

/**
 * 侧栏「定位到画布」或新设计 Staging 到达时：高亮 + 视口，并选中节点/边打开右栏对照（关闭 AI）。
 */
export function focusGraphStagingUnit(unit: AiStagingUnit, edges: Edge[]) {
  const store = useFlowCanvasStore();
  const nodeIds = resolveUnitHighlightNodeIds(unit, edges);
  if (nodeIds.length) {
    store.setAiHighlightFocus(nodeIds);
    void useFlowViewport().focusNodeIds(nodeIds);
  }
  const { nodeId, edgeId } = graphObjectIdFromUnit(unit);
  if (nodeId) {
    store.selectItem('node', nodeId);
  } else if (edgeId) {
    store.selectItem('edge', edgeId);
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
 * 进入确认态：优先打开首个图单元右栏对照，否则打开场景运行配置对照（均关闭 AI）。
 * 由新设计 patch 灌入后显式调用（勿对 pendingCount 做 watch，以免 session 重灌误触发）。
 */
export function openPendingStagingReview(edges: Edge[]) {
  const stagingStore = useAiStagingStore();
  const pending = Object.values(stagingStore.unitsById) as AiStagingUnit[];
  const graphUnit = findFirstPendingGraphUnit(pending);
  if (graphUnit) {
    focusGraphStagingUnit(graphUnit, edges);
    return;
  }
  const scenarioUnit = findFirstPendingScenarioUnit(pending);
  if (!scenarioUnit) return;
  const scenarioId = resolveScenarioIdFromUnit(scenarioUnit);
  if (scenarioId) {
    focusScenarioStagingUnit(scenarioId);
  }
}

/** 从场景类 Staging 单元解析目标 scenarioId */
export function resolveScenarioIdFromUnit(unit: AiStagingUnit): string | null {
  return graphObjectIdFromUnit(unit).scenarioId ?? null;
}

/** 在单元列表中查找第一个 pending 的图类型 Staging 单元 */
export function findFirstPendingGraphUnit(units: AiStagingUnit[]): AiStagingUnit | undefined {
  return units.find((unit) => unit.status === 'pending' && isGraphStagingKind(unit.kind));
}

/** 在单元列表中查找第一个 pending 的场景类 Staging 单元 */
export function findFirstPendingScenarioUnit(units: AiStagingUnit[]): AiStagingUnit | undefined {
  return units.find((unit) => unit.status === 'pending' && isScenarioStagingKind(unit.kind));
}
