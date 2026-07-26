import type { GraphValidationResult } from '@/utils/flow/graphValidate';

import type { DiffItemKind, FlowDesignPatch } from './aiDesignTypes';

/** Staging 单元生命周期 */
export type AiStagingStatus = 'pending' | 'confirmed' | 'rejected';

/** 画布节点/边上的 Staging 模式 */
export type AiStagingCanvasMode = 'add' | 'update' | 'delete';

/** 画布 parallel map 条目：不写入 graph_json */
export interface AiStagingCanvasMark {
  unitId: string;
  messageId: string;
  mode: AiStagingCanvasMode;
}

/** 单个待确认变更单元 */
export interface AiStagingUnit {
  unitId: string;
  messageId: string;
  kind: DiffItemKind;
  status: AiStagingStatus;
  /** 摘要用短文案 */
  label: string;
  /** patch 原始片段（节点/边/场景子对象） */
  patchSlice: unknown;
  /** update/delete 类：确认前的画布基线 */
  baseline?: Record<string, unknown>;
  /** add/update 类：用户可编辑 draft（默认来自 patch） */
  draft?: Record<string, unknown>;
  /** 最近一次 confirm 校验；失败时供重试 UI，成功后清除 */
  lastValidation?: GraphValidationResult;
  /** 最近一次 confirm 的依赖提示（与 errors 分开展示来源） */
  lastDependencyHints?: string[];
  /** confirm 请求进行中，防止重复提交 */
  confirmInFlight?: boolean;
  confirmedAt?: number;
}

/** 按 message 聚合，供会话摘要卡片使用 */
export interface AiStagingMessageSummary {
  messageId: string;
  pending: number;
  confirmed: number;
  rejected: number;
  addNodeCount: number;
  updateNodeCount: number;
  deleteNodeCount: number;
  addEdgeCount: number;
  updateEdgeCount: number;
  deleteEdgeCount: number;
  scenarioCount: number;
}

/** buildStagingUnits 读取的画布上下文 */
export interface StagingBuildContext {
  nodes: Array<{ id: string; type?: string; position?: { x: number; y: number }; data?: Record<string, unknown> }>;
  edges: Array<{ id: string; source: string; target: string; label?: string }>;
  runConfig?: {
    activeScenarioId: string;
    scenarios: Array<{
      id: string;
      name?: string;
      testProjectEnvId?: string;
      flowSeed?: Record<string, unknown>;
      remark?: string;
    }>;
  };
}

/** 保存 graph_json 时排除/回滚未 confirm 的 Staging */
export interface StagingPersistFilter {
  excludeNodeIds: ReadonlySet<string>;
  excludeEdgeIds: ReadonlySet<string>;
  nodeBaselines: ReadonlyMap<
    string,
    { type?: string; position?: { x: number; y: number }; data?: Record<string, unknown> }
  >;
  edgeBaselines: ReadonlyMap<string, { source?: string; target?: string; label?: string }>;
  scenarioFilter?: ScenarioStagingPersistFilter;
}

export interface ScenarioStagingPersistFilter {
  excludeScenarioIds: ReadonlySet<string>;
  scenarioBaselines: ReadonlyMap<string, Record<string, unknown>>;
  baselineActiveScenarioId?: string;
}

export type { FlowDesignPatch };
