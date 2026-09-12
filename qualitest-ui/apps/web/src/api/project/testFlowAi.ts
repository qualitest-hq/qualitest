/**
 * 测试流 AI 设计 HTTP 客户端。
 *
 * - POST /project/testFlow/ai/design/stream — SSE 流式设计
 * - POST /project/testFlow/ai/patch/confirmUnit — 确认单个画布变更单元
 * - POST /project/testFlow/ai/patch/savePrecheck — 保存前鉴权/必填预检（不写库）
 * - POST /project/testFlow/ai/assetProposal/confirm — 确认素材库写入提案并落盘
 * - POST /project/testFlow/ai/assetProposal/reject — 拒绝素材库写入提案
 * - GET  /project/testFlow/ai/promptTemplates — 设计面板提示词模板
 */
import request from '@/utils/request';
import { consumeAuthenticatedSsePost } from '@/utils/ai/consumeSseStream';

import type { GraphJson } from '@/utils/flow/graphTypes';

import type { AiDesignMention, ComposerDoc } from '@/views/project/testFlow/types/mentionTypes';
import type {
  DesignValidationResult,
  FlowDesignPatch,
  TestFlowDesignResult,
} from '@/views/project/testFlow/types/aiDesignTypes';

/** 流式设计请求体 */
export interface TestFlowDesignRequestPayload {
  /** 当前测试流 id */
  testFlowId: string;
  /** 所属测试项目 id；模板模式可空 */
  testProjectId?: string | null;
  /** project（默认）| template（项目模板预制流） */
  designMode?: 'project' | 'template';
  /** designMode=template 时内联的预制接口列表 */
  templateApis?: unknown[];
  /** 模板模式会话锚点（非雪花 id） */
  templateFlowKey?: string;
  /** 选用的 LLM 模型 id */
  aiLlmModelId: string;
  /** 多轮会话 id；空则服务端创建新会话 */
  aiChatSessionId?: string | null;
  /** 用户输入纯文本（由 composerDoc 线性化） */
  prompt: string;
  /** 从 composerDoc 提取的去重 @ 引用 */
  mentions?: AiDesignMention[];
  /** 编辑器完整文档，用于会话恢复时重建 chip */
  composerDoc?: ComposerDoc;
  /** 当前画布 graph_json */
  graphJson: GraphJson;
  /** 对话级思考开关 */
  thinkingEnabled?: boolean;
  /** 限定可检索的 API id 范围（可选） */
  scopeApiIds?: string[];
  /** 画布上下文：选中的节点 id 列表（可选） */
  contextNodeIds?: string[];
  /** 画布上下文：关联的 Run id，用于失败修复场景（可选） */
  contextRunId?: string;
}

/** SSE 推送的事件类型 */
export type AiDesignStreamEventType =
  | 'token'
  | 'thinking'
  | 'tool_start'
  | 'tool_end'
  | 'done'
  | 'error';

/** 单条 SSE 事件载荷 */
export interface AiDesignStreamEvent {
  type: AiDesignStreamEventType;
  /** token / thinking 事件的文本增量 */
  text?: string;
  /** tool_start / tool_end 的工具名 */
  tool?: string;
  /** error 事件的错误说明 */
  message?: string;
  /** done 事件的完整设计结果 */
  result?: TestFlowDesignResult;
}

/** SSE 消费回调，各事件类型对应可选处理器 */
export interface AiDesignStreamHandlers {
  onEvent?: (event: AiDesignStreamEvent) => void;
  onToken?: (text: string) => void;
  onThinking?: (text: string) => void;
  onToolStart?: (tool: string) => void;
  onToolEnd?: (tool: string) => void;
  onDone?: (result: TestFlowDesignResult) => void;
  onError?: (message: string) => void;
}

const BASE_API = import.meta.env.VITE_APP_BASE_API as string;

/** AI 设计面板提示词模板项 */
export interface AiPromptTemplateItem {
  aiPromptTemplateId: string;
  templateScope: 'platform' | 'project';
  testProjectId?: string;
  sessionScene?: string;
  templateTitle: string;
  templateDescription?: string;
  templateContent: string;
  builtinStatus?: number;
  enableStatus?: number;
  sortNum?: number;
  remark?: string;
}

/**
 * 查询 AI 设计面板可用提示词模板（平台级 + 当前项目级）。
 */
export async function listAiDesignPromptTemplates(
  testProjectId?: string | null,
  sessionScene = 'test_flow_design',
): Promise<AiPromptTemplateItem[]> {
  const res = await request({
    url: '/project/testFlow/ai/promptTemplates',
    method: 'get',
    params: {
      ...(testProjectId && testProjectId !== '0' ? { testProjectId } : {}),
      sessionScene,
    },
  });
  return (res.data ?? []) as AiPromptTemplateItem[];
}

/** 单 Staging 单元 confirm 的请求体 */
export interface FlowDesignPatchConfirmPayload {
  testProjectId: string;
  graphJson: GraphJson;
  patch: FlowDesignPatch;
  unitId: string;
  draftOverride?: Record<string, unknown>;
  confirmedUnitIds: string[];
  rejectedUnitIds?: string[];
}

/** 单 Staging 单元 confirm 的响应体 */
export interface FlowDesignPatchConfirmResult extends DesignValidationResult {
  graphJson?: GraphJson | null;
  dependencyHints?: string[];
  /**
   * 本轮已无未决 Staging 单元时的保存风险文案
   *（鉴权凭证 / 登录抽取 / HTTP 必填等），不阻断本次确认
   */
  saveRiskWarnings?: string[];
  baseGraphHash?: string;
}

/**
 * 确认单个 Staging 变更单元：draft 合并 → 裁剪 patch → 全图校验。
 * 成功返回 graphJson；本轮已无未决单元时可能带 saveRiskWarnings（不阻断确认）。
 * 失败时 graphJson 为 null。
 */
export async function confirmFlowDesignUnit(
  data: FlowDesignPatchConfirmPayload,
): Promise<FlowDesignPatchConfirmResult> {
  const res = await request({
    url: '/project/testFlow/ai/patch/confirmUnit',
    method: 'post',
    headers: { repeatSubmit: false },
    data,
  });
  return res.data as FlowDesignPatchConfirmResult;
}

/** 保存前预检请求：项目 id + 拟持久化的 graph_json 字符串 */
export interface FlowDesignSavePrecheckPayload {
  testProjectId: string;
  graphJson: string;
}

/** 保存前预检响应：ok 为 false 时 errors 含鉴权凭证、登录抽取、HTTP 必填等错误文案 */
export interface FlowDesignSavePrecheckResult {
  ok: boolean;
  errors?: string[];
}

/**
 * 保存前预检：检查鉴权凭证来源、登录抽取、HTTP 必填测值。
 * 不写库；用于在调用保存 API 前阻断并提示。
 */
export async function savePrecheckFlowDesign(
  data: FlowDesignSavePrecheckPayload,
): Promise<FlowDesignSavePrecheckResult> {
  const res = await request({
    url: '/project/testFlow/ai/patch/savePrecheck',
    method: 'post',
    headers: { repeatSubmit: false },
    data,
  });
  return res.data as FlowDesignSavePrecheckResult;
}

/** 确认或拒绝素材库写入提案的请求参数 */
export interface AssetUpsertProposalDecisionPayload {
  /** 测试项目 id */
  testProjectId: string;
  /** 存有提案的助手消息 id */
  aiChatMessageId: string;
  /** 素材 key */
  key: string;
}

/** 确认或拒绝素材库写入提案的响应 */
export interface AssetUpsertProposalDecisionResult {
  ok: boolean;
  errors?: string[];
  key?: string;
  action?: string;
  status?: string;
  /** 字段名列表，无明文 */
  fields?: string[];
  placeholderHint?: string;
}

/**
 * 确认素材库写入提案：服务端把提案 fields 写入项目素材库。
 */
export async function confirmAssetUpsertProposal(
  data: AssetUpsertProposalDecisionPayload,
): Promise<AssetUpsertProposalDecisionResult> {
  const res = await request({
    url: '/project/testFlow/ai/assetProposal/confirm',
    method: 'post',
    headers: { repeatSubmit: false },
    data,
  });
  return res.data as AssetUpsertProposalDecisionResult;
}

/**
 * 拒绝素材库写入提案：服务端只改消息元数据状态，不写素材库。
 */
export async function rejectAssetUpsertProposal(
  data: AssetUpsertProposalDecisionPayload,
): Promise<AssetUpsertProposalDecisionResult> {
  const res = await request({
    url: '/project/testFlow/ai/assetProposal/reject',
    method: 'post',
    headers: { repeatSubmit: false },
    data,
  });
  return res.data as AssetUpsertProposalDecisionResult;
}

/**
 * SSE 流式设计。
 * 使用 fetch + ReadableStream 解析 `data:` 行；支持 AbortSignal 取消。
 * 流结束时应收到 type=done 事件，否则抛出异常。
 */
export async function designTestFlowStream(
  data: TestFlowDesignRequestPayload,
  handlers: AiDesignStreamHandlers,
  signal?: AbortSignal,
): Promise<TestFlowDesignResult> {
  return consumeAuthenticatedSsePost<AiDesignStreamEvent, TestFlowDesignResult>({
    url: `${BASE_API}/project/testFlow/ai/design/stream`,
    body: data,
    handlers,
    signal,
    defaultErrorMessage: 'AI 助手请求失败',
    missingResultMessage: '未收到设计结果',
  });
}

