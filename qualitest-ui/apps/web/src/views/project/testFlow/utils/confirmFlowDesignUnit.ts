/**
 * 单 Staging 单元确认 API 封装。
 * 组装 graph_json，裁剪未决 patch，调用 confirmUnit，并解析 saveRiskWarnings。
 */
import type { Edge, Node } from '@vue-flow/core';

import { confirmFlowDesignUnit } from '@/api/project/testFlowAi';
import type { GraphFlowOutput, GraphScenarioConfig, GraphJson, GraphViewport } from '@/utils/flow/graphTypes';
import type { GraphValidationResult } from '@/utils/flow/graphValidate';
import { promiseWithTimeout } from '@/utils/promiseWithTimeout';

import { toGraphJson } from '../graphAdapter';
import type { FlowDesignPatch } from '../types/aiDesignTypes';
import type { StagingPersistFilter } from '../types/aiStagingTypes';
import { slimPatchKeepingUnresolved } from './filterPatchForConfirm';

/** 确认请求超时毫秒数 */
export const CONFIRM_UNIT_TIMEOUT_MS = 30_000;

/** 确认单单元请求参数 */
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
  /** 组装 graph_json 时排除其它 pending Staging 的过滤规则 */
  stagingFilter?: StagingPersistFilter;
}

/** 确认单单元响应 */
export interface ConfirmFlowDesignUnitResult {
  validation: GraphValidationResult;
  graphJson: GraphJson | null;
  dependencyHints?: string[];
  /** 本轮已无未决单元时的保存风险文案（不阻断本次确认） */
  saveRiskWarnings?: string[];
  baseGraphHash?: string;
}

/**
 * 调用服务端确认单元：底图 + 裁剪后的 patch + draft。
 * 成功时 graphJson 非空；失败时 validation.ok=false。
 */
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

  const confirmed = new Set(input.confirmedUnitIds);
  const rejected = new Set(input.rejectedUnitIds ?? []);
  const slimPatch = slimPatchKeepingUnresolved(input.patch, input.unitId, confirmed, rejected);

  const result = await promiseWithTimeout(
    confirmFlowDesignUnit({
      testProjectId: projectId,
      graphJson,
      patch: slimPatch,
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
    saveRiskWarnings: result.saveRiskWarnings,
    baseGraphHash: result.baseGraphHash,
  };
}
