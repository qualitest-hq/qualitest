import type { GraphEdge, GraphNode, GraphRunScenario } from '@/utils/flow/graphTypes';

import type { AiChatMessageItem } from '@/api/ai/chat';

import type { ComposerDoc } from './mentionTypes';
import { COMPOSER_DOC_VERSION } from './mentionTypes';

/** AI 建议删除的节点/边 id 列表 */
export interface FlowDesignSuggestedDeletes {
  nodeIds?: string[];
  edgeIds?: string[];
}

/** AI 对运行场景配置 meta 的增量修改建议 */
export interface FlowDesignScenarioPatch {
  /** 切换默认运行场景 id */
  activeScenarioId?: string;
  /** 新增运行场景 */
  addScenarios?: GraphRunScenario[];
  /** 按 id 修改已有运行场景 */
  updateScenarios?: GraphRunScenario[];
  /** 建议删除的运行场景 id */
  deleteScenarioIds?: string[];
}

/**
 * AI 返回的测试流增量 patch（来自 submit_flow_design_patch 工具）。
 * 用户在画布上逐项 confirm 后写入正式图。
 */
export interface FlowDesignPatch {
  addNodes?: GraphNode[];
  updateNodes?: GraphNode[];
  addEdges?: GraphEdge[];
  updateEdges?: GraphEdge[];
  suggestedDeletes?: FlowDesignSuggestedDeletes;
  /** 运行场景 meta 增量 */
  scenarioPatch?: FlowDesignScenarioPatch;
  summary?: string;
}

/** 图结构校验摘要：ok 为 true 表示无 errors，warnings 不阻断合并 */
export type DesignValidationResult = import('@/utils/flow/graphValidate').GraphValidationResult;

/** AI 设计流式接口响应体；patch 来自 submit 工具，explainOnly 表示本轮未提交修改建议 */
export interface TestFlowDesignResult {
  aiChatSessionId?: string | null;
  aiLlmModelId?: string;
  vendorName?: string;
  modelName?: string;
  summary?: string;
  thinkingContent?: string;
  patch?: FlowDesignPatch;
  validation?: DesignValidationResult;
  explainOnly?: boolean;
}

/** 对话消息角色 */
export type AiDesignMessageRole = 'user' | 'assistant' | 'system';

/** system 消息上可点击的操作项，当前支持触发 test_flow 保存 */
export type AiDesignSystemAction =
  | { type: 'saveFlow'; label: string };

/**
 * 对话面板渲染用单条消息。
 * assistant 的 patch 来自实时 SSE 响应或会话 resultMetaJson.patchJson；
 * content 始终为自然语言 summary。
 */
export interface AiDesignMessageView {
  /** 客户端临时 id 或服务端消息 id */
  id: string;
  role: AiDesignMessageRole;
  /** user 为原文；assistant 为 summary；system 为本地提示文案 */
  content: string;
  /** assistant 模型思考过程 */
  thinkingContent?: string;
  createTime?: string;
  aiLlmModelId?: string;
  vendorName?: string;
  modelName?: string;
  /** 结构化修改建议；explainOnly 或纯答疑轮次无此字段 */
  patch?: FlowDesignPatch;
  validation?: DesignValidationResult;
  explainOnly?: boolean;
  /** 摘要模式：patch 尚未懒加载 */
  patchPending?: boolean;
  /** 正在拉取 patch meta */
  patchLoading?: boolean;
  /** user 消息编辑器文档，用于历史气泡中的只读 chip 渲染 */
  composerDoc?: ComposerDoc;
  /** 请求进行中，尚未收到响应 */
  pending?: boolean;
  /** system 消息附带的操作按钮，如保存提示 */
  actions?: AiDesignSystemAction[];
}

/** Staging 单元变更类型 */
export type DiffItemKind =
  | 'addNode'
  | 'addEdge'
  | 'updateNode'
  | 'updateEdge'
  | 'deleteNode'
  | 'deleteEdge'
  | 'setActiveScenario'
  | 'addScenario'
  | 'updateScenario'
  | 'deleteScenario';

/**
 * 将服务端持久化的 user 消息还原为面板视图。
 * messageContent 为线性化 prompt；composerDoc 来自 result_meta_json，用于历史气泡 chip 展示。
 */
export function parseUserFromServer(msg: AiChatMessageItem): AiDesignMessageView {
  let composerDoc: ComposerDoc | undefined;
  if (msg.resultMetaJson) {
    try {
      const meta = JSON.parse(msg.resultMetaJson) as Record<string, unknown>;
      const raw = meta.composerDoc;
      if (raw && typeof raw === 'object' && (raw as ComposerDoc).version === COMPOSER_DOC_VERSION) {
        composerDoc = raw as ComposerDoc;
      }
    } catch {
      composerDoc = undefined;
    }
  }
  return {
    id: msg.aiChatMessageId,
    role: 'user',
    content: msg.messageContent ?? '',
    createTime: msg.createTime,
    composerDoc,
  };
}

/**
 * 将服务端持久化的 assistant 消息还原为面板视图。
 *
 * messageContent：自然语言 summary，作为气泡正文。
 * resultMetaJson.explainOnly：本轮是否未调用 submit（纯答疑）。
 * resultMetaJson.patchJson：完整 FlowDesignPatch，用于 Staging 与 confirm。
 * patch 不从 messageContent 解析 JSON。
 */
export function parseAssistantFromServer(msg: AiChatMessageItem): AiDesignMessageView {
  let meta: Record<string, unknown> = {};
  if (msg.resultMetaJson) {
    try {
      meta = JSON.parse(msg.resultMetaJson) as Record<string, unknown>;
    } catch {
      meta = {};
    }
  }
  const explainOnly = meta.explainOnly === true;
  const summary =
    (typeof meta.summary === 'string' && meta.summary) ||
    msg.messageContent ||
    '';
  let patch: FlowDesignPatch | undefined;
  const hasPatchFlag = meta.hasPatch === true;
  if (!explainOnly && meta.patchJson && typeof meta.patchJson === 'object') {
    patch = meta.patchJson as FlowDesignPatch;
  }
  return {
    id: msg.aiChatMessageId,
    role: 'assistant',
    content: summary,
    thinkingContent: msg.thinkingContent?.trim() || undefined,
    createTime: msg.createTime,
    aiLlmModelId: msg.aiLlmModelId,
    vendorName: typeof meta.vendorName === 'string' ? meta.vendorName : undefined,
    modelName: typeof meta.modelName === 'string' ? meta.modelName : undefined,
    patch,
    explainOnly,
    patchPending: hasPatchFlag && !patch && !explainOnly,
  };
}
