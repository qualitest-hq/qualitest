/**
 * Staging 摘要定位辅助（re-export 导航 composable）。
 */
import type { Edge } from '@vue-flow/core';

import type { AiStagingUnit } from '../types/aiStagingTypes';
import { collectStagingConfirmHighlightIds } from './mergeHighlight';
import {
  findFirstPendingGraphUnit,
  findFirstPendingScenarioUnit,
  resolveScenarioIdFromUnit,
} from '../composables/useStagingNavigation';
import { isGraphStagingKind, isScenarioStagingKind } from './stagingUnitIds';

export {
  findFirstPendingGraphUnit,
  findFirstPendingScenarioUnit,
  isGraphStagingKind,
  isScenarioStagingKind,
  resolveScenarioIdFromUnit,
};

export function resolveLocateNodeIds(unit: AiStagingUnit, edges: Edge[]): string[] {
  return collectStagingConfirmHighlightIds(unit.unitId, unit.kind, edges);
}
