/**
 * 单 Staging 单元 confirm API 客户端封装。
 */
import type { Edge, Node } from '@vue-flow/core';

import { confirmFlowDesignUnit } from '@/api/project/testFlowAi';
import type { GraphFlowOutput, GraphScenarioConfig, GraphJson, GraphViewport } from '@/utils/flow/graphTypes';
import type { GraphValidationResult } from '@/utils/flow/graphValidate';
import { promiseWithTimeout } from '@/utils/promiseWithTimeout';

import { toGraphJson } from '../graphAdapter';
import type { FlowDesignPatch } from '../types/aiDesignTypes';
import type { StagingPersistFilter } from '../types/aiStagingTypes';

export const CONFIRM_UNIT_TIMEOUT_MS = 30_000;

export interface ConfirmFlowDesignUnitInput {
  nodes: Node[];
  edges: Edge[];
  runConfig: GraphScenarioConfig;
  flowOutputs: GraphFlowOutput[];
  viewport: GraphViewport;
  testProjectId: string;
  patch: FlowDesignPatch;
  unitId: string;
  draftOverride?: Record<string, unknown>;
  confirmedUnitIds: string[];
  rejectedUnitIds?: string[];
}

export interface ConfirmFlowDesignUnitResult {
  validation: GraphValidationResult;
  graphJson: GraphJson | null;
  dependencyHints?: string[];
  baseGraphHash?: string;
}

export async function requestConfirmFlowDesignUnit(
  input: ConfirmFlowDesignUnitInput,
): Promise<ConfirmFlowDesignUnitResult> {
  const projectId = input.testProjectId.trim();
  if (!projectId) {
    return {
      validation: { ok: false, errors: ['缺少 testProjectId'], warnings: [] },
      graphJson: null,
    };
  }

  const graphJson = toGraphJson({
    nodes: input.nodes,
    edges: input.edges,
    viewport: input.viewport,
    runConfig: input.runConfig,
    flowOutputs: input.flowOutputs,
    stagingFilter: input.stagingFilter,
  });

  const result = await promiseWithTimeout(
    confirmFlowDesignUnit({
      testProjectId: projectId,
      graphJson,
      patch: input.patch,
      unitId: input.unitId,
      draftOverride: input.draftOverride,
      confirmedUnitIds: input.confirmedUnitIds,
      rejectedUnitIds: input.rejectedUnitIds,
    }),
    CONFIRM_UNIT_TIMEOUT_MS,
    '确认变更超时，请稍后重试',
  );

  return {
    validation: {
      ok: result.ok,
      errors: result.errors ?? [],
      warnings: result.warnings ?? [],
    },
    graphJson: result.ok ? (result.graphJson ?? null) : null,
    dependencyHints: result.dependencyHints,
    baseGraphHash: result.baseGraphHash,
  };
}
