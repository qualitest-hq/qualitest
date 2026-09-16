import type { GraphEdge, GraphNode, GraphRunScenario } from '@/utils/flow/graphTypes';

import type { AiChatMessageItem } from '@/api/ai/chat';

import type { ComposerDoc } from './mentionTypes';
import { COMPOSER_DOC_VERSION } from './mentionTypes';
import {
  parseToolTraceFromMeta,
  type AiToolTraceCallView,
  type AiToolTraceView,
} from '@/utils/ai/toolTrace';

export type { AiToolTraceCallView, AiToolTraceView };
export { parseToolTraceFromMeta };

/** AI 建议删除的节点/边 id 列表 */
export interface FlowDesignSuggestedDeletes {
  nodeIds?: string[];
  edgeIds?: string[];
}

/** AI 对运行场景配置 meta 的增量修改建议（不含切换默认场景；默认场景由界面直接改） */
export interface FlowDesignScenarioPatch {
  /** 新增运行场景 */
  addScenarios?: GraphRunScenario[];
  /** 按 id 修改已有运行场景 */
  updateScenarios?: GraphRunScenario[];
  /** 建议删除的运行场景 id */
  deleteScenarioIds?: string[];
}

/**
 * AI 返回的测试流增量 patch（后端多次 submit_* 成功单元累积合并后下发）。
 * 用户在画布上逐项 confirm 后才写入正式图。
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

/** AI 设计流式接口一轮响应：说明文案、画布 patch、校验、素材库写入提案、鉴权 Profile 提案等 */
export interface TestFlowDesignResult {
  aiChatSessionId?: string | null;
  aiLlmModelId?: string;
  vendorName?: string;
  modelName?: string;
  summary?: string;
  thinkingContent?: string;
  patch?: FlowDesignPatch;
  validation?: DesignValidationResult;
  /** 本轮未成功接受任何 submit_* 单元时为 true（纯答疑，无画布 patch） */
  explainOnly?: boolean;
  /** 本轮素材库写入提案；流式结束事件中通常带 fields 明文 */
  assetProposals?: AssetUpsertProposalView[];
  /** 本轮项目鉴权 Profile 写入提案（新建或更新项目 auth_config 中的 Profile） */
  authProfileProposals?: AuthProfileUpsertProposalView[];
  /** 本轮工具调用轨迹（已脱敏截断，气泡内默认折叠展示） */
  toolTrace?: AiToolTraceView;
  /** 本轮因用户取消或连接中断结束，内容可能不完整 */
  interrupted?: boolean;
}

/** 素材提案处理状态：待确认 / 已确认落盘 / 已拒绝 */
export type AssetUpsertProposalStatus = 'pending' | 'confirmed' | 'rejected';

/** 提案动作：新建条目或更新已有 key */
export type AssetUpsertProposalAction = 'created' | 'updated';

/**
 * 聊天里展示的一条素材库写入提案。
 * 完整数据含 fields 明文；会话列表摘要可能只有 fieldNames、没有 fields。
 */
export interface AssetUpsertProposalView {
  /** 素材键名 */
  key: string;
  /** created 或 updated */
  action?: AssetUpsertProposalAction | string;
  remark?: string;
  /** 字段明文；列表摘要可能没有 */
  fields?: Record<string, unknown>;
  /** 仅字段名（列表摘要或由 fields 推导） */
  fieldNames?: string[];
  /** pending / confirmed / rejected */
  status?: AssetUpsertProposalStatus | string;
}

/**
 * 聊天里展示的一条项目鉴权 Profile 写入提案。
 * 确认后写入项目 auth_config；拒绝只改提案状态，不改项目配置。
 */
export interface AuthProfileUpsertProposalView {
  /** Profile 唯一 id（提案定位与确认/拒绝入参） */
  profileId: string;
  /** 动作：created 新建 / updated 更新已有 Profile */
  action?: string;
  /** 处理状态：pending 待确认 / confirmed 已写入 / rejected 已拒绝 */
  status?: string;
  /** 变更前 Profile 字段快照（用于 Diff 展示） */
  before?: Record<string, unknown>;
  /** 变更后 Profile 字段快照（确认成功后可能带回服务端规范化结果） */
  after?: Record<string, unknown>;
  /** AI 给出的增量补丁字段（相对 before 的变更集合） */
  patch?: Record<string, unknown>;
  /** 相对 before 发生变更的字段名列表，供卡片逐行展示 */
  changedFields?: string[];
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
  /** 画布修改建议；explainOnly 或本轮未成功 submit 时无 */
  patch?: FlowDesignPatch;
  validation?: DesignValidationResult;
  /** 本轮未成功接受任何 submit_*：纯答疑，不灌 Staging */
  explainOnly?: boolean;
  /** 列表摘要：有改图标记但尚未拉到完整 patch */
  patchPending?: boolean;
  /** 正在拉取含 patch 的完整消息元数据 */
  patchLoading?: boolean;
  /** 本轮素材库写入提案列表 */
  assetProposals?: AssetUpsertProposalView[];
  /** 列表摘要：服务端标了有提案，但本条尚未解析出提案内容 */
  assetProposalsPending?: boolean;
  /** 本轮项目鉴权 Profile 写入提案列表（确认后写项目 auth_config） */
  authProfileProposals?: AuthProfileUpsertProposalView[];
  /** 本轮工具调用轨迹（脱敏截断后，气泡内默认折叠展示） */
  toolTrace?: AiToolTraceView;
  /** 本轮因用户取消或连接中断结束，内容可能不完整 */
  interrupted?: boolean;
  /** user 消息编辑器文档，用于历史气泡中的只读 chip 渲染 */
  composerDoc?: ComposerDoc;
  /** 请求进行中，尚未收到响应 */
  pending?: boolean;
  /** system 消息附带的操作按钮，如保存提示 */
  actions?: AiDesignSystemAction[];
}

/** Staging 单元变更类型（前端拆 patch 用） */
export type DiffItemKind =
  | 'addNode'
  | 'addEdge'
  | 'updateNode'
  | 'updateEdge'
  | 'deleteNode'
  | 'deleteEdge'
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
 * 将服务端助手消息还原为面板视图。
 * 正文用 summary；explainOnly 时不挂 patch；否则读 patchJson。
 * patchPending：服务端标了有改图但本条尚未拉到完整 patchJson。
 * 另解析素材提案、鉴权提案、工具轨迹 toolTrace，以及 interrupted 中断标记。
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
  // 纯答疑：不解析 patch，避免误灌 Staging
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
  const assetProposals = parseAssetProposalsFromMeta(meta);
  const hasAssetProposalsFlag = meta.hasAssetProposals === true;
  const authProfileProposals = parseAuthProfileProposalsFromMeta(meta);
  const toolTrace = parseToolTraceFromMeta(meta);
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
    assetProposals: assetProposals.length > 0 ? assetProposals : undefined,
    assetProposalsPending: hasAssetProposalsFlag && assetProposals.length === 0,
    authProfileProposals: authProfileProposals.length > 0 ? authProfileProposals : undefined,
    toolTrace,
    interrupted: meta.interrupted === true,
  };
}

/**
 * 从消息元数据解析素材库写入提案列表。
 * 跳过无 key 的项；有 fields 时顺带填 fieldNames。
 */
export function parseAssetProposalsFromMeta(meta: Record<string, unknown>): AssetUpsertProposalView[] {
  const raw = meta.assetProposals;
  if (!Array.isArray(raw)) {
    return [];
  }
  const out: AssetUpsertProposalView[] = [];
  for (const item of raw) {
    if (!item || typeof item !== 'object') {
      continue;
    }
    const row = item as Record<string, unknown>;
    const key = typeof row.key === 'string' ? row.key.trim() : '';
    if (!key) {
      continue;
    }
    const fields =
      row.fields && typeof row.fields === 'object' && !Array.isArray(row.fields)
        ? (row.fields as Record<string, unknown>)
        : undefined;
    const fieldNames = Array.isArray(row.fieldNames)
      ? row.fieldNames.filter((n): n is string => typeof n === 'string' && n.trim().length > 0)
      : fields
        ? Object.keys(fields)
        : undefined;
    out.push({
      key,
      action: typeof row.action === 'string' ? row.action : undefined,
      remark: typeof row.remark === 'string' ? row.remark : undefined,
      fields,
      fieldNames,
      status: typeof row.status === 'string' ? row.status : 'pending',
    });
  }
  return out;
}

/**
 * 从消息元数据解析鉴权 Profile 写入提案列表。
 * 跳过无 profileId 的项；缺 status 时默认 pending；
 * before / after / patch 仅在为对象时保留。
 */
export function parseAuthProfileProposalsFromMeta(meta: Record<string, unknown>): AuthProfileUpsertProposalView[] {
  const raw = meta.authProfileProposals;
  if (!Array.isArray(raw)) {
    return [];
  }
  const out: AuthProfileUpsertProposalView[] = [];
  for (const item of raw) {
    if (!item || typeof item !== 'object') continue;
    const row = item as Record<string, unknown>;
    const profileId = typeof row.profileId === 'string' ? row.profileId.trim() : '';
    if (!profileId) continue;
    out.push({
      profileId,
      action: typeof row.action === 'string' ? row.action : undefined,
      status: typeof row.status === 'string' ? row.status : 'pending',
      before: row.before && typeof row.before === 'object' ? (row.before as Record<string, unknown>) : undefined,
      after: row.after && typeof row.after === 'object' ? (row.after as Record<string, unknown>) : undefined,
      patch: row.patch && typeof row.patch === 'object' ? (row.patch as Record<string, unknown>) : undefined,
      changedFields: Array.isArray(row.changedFields)
        ? row.changedFields.filter((n): n is string => typeof n === 'string')
        : undefined,
    });
  }
  return out;
}

