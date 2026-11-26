/**
 * 单 Staging 单元确认 API 封装。
 * 组装 graph_json，裁剪未决 patch，调用 confirmUnit。
 */
import type { Edge, Node } from '@vue-flow/core';

import { confirmFlowDesignUnit } from '@/api/project/testFlowAi';
import type { GraphScenarioConfig, GraphJson, GraphViewport } from '@/utils/flow/graphTypes';
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
  viewport: GraphViewport;
  /** 当前排版方式，随确认后的图一起写回 */
  layoutPreset?: string | null;
  testProjectId: string;
  patch: FlowDesignPatch;
  unitId: string;
  draftOverride?: Record<string, unknown>;
  confirmedUnitIds: string[];
  rejectedUnitIds?: string[];
  /** 组装 graph_json 时排除其它 pending Staging 的过滤规则 */
  stagingFilter?: StagingPersistFilter;
}

/** 确认单单元响应；无图时表示请求层失败 */
export interface ConfirmFlowDesignUnitResult {
  graphJson: GraphJson | null;
  /** 客户端/请求层失败文案（无 graphJson 时） */
  error?: string;
  baseGraphHash?: string;
}

/**
 * 调用服务端确认单元：底图 + 裁剪后的 patch + draft。
 * 以 graphJson 有无为落盘依据；图校验 findings 不挡确认，由画布展示。
 */
export async function requestConfirmFlowDesignUnit(
  input: ConfirmFlowDesignUnitInput,
): Promise<ConfirmFlowDesignUnitResult> {
  const projectId = input.testProjectId.trim();
  if (!projectId) {
    return {
      graphJson: null,
      error: '缺少 testProjectId',
    };
  }

  const graphJson = toGraphJson({
    nodes: input.nodes,
    edges: input.edges,
    viewport: input.viewport,
    runConfig: input.runConfig,
    layoutPreset: input.layoutPreset,
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

  const mergedGraph = result.graphJson ?? null;
  return {
    graphJson: mergedGraph,
    error: mergedGraph ? undefined : '确认失败：未返回合并图',
    baseGraphHash: result.baseGraphHash,
  };
}
