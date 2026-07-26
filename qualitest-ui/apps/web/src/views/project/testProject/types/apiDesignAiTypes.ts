/**
 * AI API 助手：消息视图、patch Diff、服务端消息解析（脚本 / 约束 / 测值 / meta）。
 */
import type { AiChatMessageItem } from '@/api/ai/chat';

/** 单条 patch 变更：脚本、约束、测值或 meta。 */
export interface ApiDesignPatchChange {
  unitId?: string;
  target: string;
  phase?: 'pre' | 'post';
  path?: string;
  type?: string;
  responseId?: string;
  action: 'update' | 'clear' | 'set' | 'updateConstraints';
  constraints?: Record<string, unknown>;
  description?: string;
  value?: unknown;
  content?: string;
}

/** 一轮设计产出的结构化修改建议包。 */
export interface ApiDesignPatch {
  summary?: string;
  changes?: ApiDesignPatchChange[];
}

/** patch 规范化后的校验结论。 */
export interface ApiDesignValidationResult {
  ok: boolean;
  errors: string[];
  warnings: string[];
}

/** 流式设计接口返回的单轮结果。 */
export interface ApiDesignResult {
  aiChatSessionId?: string | null;
  aiLlmModelId?: string;
  vendorName?: string;
  modelName?: string;
  summary?: string;
  thinkingContent?: string;
  patch?: ApiDesignPatch;
  validation?: ApiDesignValidationResult;
  explainOnly?: boolean;
}

export type ApiDesignMessageRole = 'user' | 'assistant' | 'system';

/** Diff 变更类型，用于侧栏展示标签 */
export type ApiDesignDiffKind =
  | 'updateScript'
  | 'clearScript'
  | 'updateConstraints'
  | 'setTestValue'
  | 'clearTestValue'
  | 'updateMeta'
  | 'clearMeta';

/** 侧栏展示用的 Diff 行：含展示文案与原始 change。 */
export interface ApiDesignDiffItem {
  id: string;
  kind: ApiDesignDiffKind;
  label: string;
  detail?: string;
  /** 原始 change，apply 时使用 */
  change: ApiDesignPatchChange;
}

/** 聊天消息视图：含 patch、Diff 勾选状态与是否已应用到工作台。 */
export interface ApiDesignMessageView {
  id: string;
  role: ApiDesignMessageRole;
  content: string;
  thinkingContent?: string;
  createTime?: string;
  aiLlmModelId?: string;
  vendorName?: string;
  modelName?: string;
  patch?: ApiDesignPatch;
  validation?: ApiDesignValidationResult;
  explainOnly?: boolean;
  merged?: boolean;
  diffItems?: ApiDesignDiffItem[];
  patchPending?: boolean;
  patchLoading?: boolean;
}

/** 截断预览文本，超出 max 时追加省略号。 */
function previewText(text: string, max = 80): string {
  const t = text.trim();
  if (t.length <= max) return t;
  return `${t.slice(0, max)}…`;
}

/** 将 AI patch 转为可勾选 Diff 列表项 */
export function buildDesignDiffItems(patch: ApiDesignPatch): ApiDesignDiffItem[] {
  const items: ApiDesignDiffItem[] = [];
  for (const change of patch.changes ?? []) {
    if (!change?.target || !change.action) continue;
    const id =
      change.unitId?.trim() ||
      `${change.target}:${change.path || change.phase || change.action}`;

    if (change.target === 'script') {
      const phaseLabel = change.phase === 'post' ? '后置' : '前置';
      if (change.action === 'clear') {
        items.push({
          id,
          kind: 'clearScript',
          label: `清空${phaseLabel}脚本`,
          change,
        });
      } else {
        items.push({
          id,
          kind: 'updateScript',
          label: `更新${phaseLabel}脚本`,
          detail: change.content ? previewText(change.content, 100) : undefined,
          change,
        });
      }
      continue;
    }

    if (change.target === 'meta') {
      const field = change.path === 'apiName' ? 'API 名称' : '接口说明';
      items.push({
        id,
        kind: change.action === 'clear' ? 'clearMeta' : 'updateMeta',
        label: change.action === 'clear' ? `清空${field}` : `更新${field}`,
        detail: change.content ? previewText(String(change.content)) : undefined,
        change,
      });
      continue;
    }

    if (change.action === 'updateConstraints') {
      const keys = Object.keys(change.constraints || {}).join(', ');
      items.push({
        id,
        kind: 'updateConstraints',
        label: `约束 ${change.target}${change.path ? '.' + change.path : ''}`,
        detail: keys || change.description || undefined,
        change,
      });
      continue;
    }

    if (change.target.startsWith('testValue.')) {
      const valPreview =
        change.action === 'clear'
          ? undefined
          : change.value != null
            ? previewText(typeof change.value === 'string' ? change.value : JSON.stringify(change.value))
            : undefined;
      items.push({
        id,
        kind: change.action === 'clear' ? 'clearTestValue' : 'setTestValue',
        label: `测值 ${change.path || change.target.replace(/^testValue\./, '')}`,
        detail: valPreview,
        change,
      });
    }
  }
  return items;
}

/** 将服务端用户消息转为侧栏视图。 */
export function parseUserMessageFromServer(msg: AiChatMessageItem): ApiDesignMessageView {
  return {
    id: String(msg.aiChatMessageId),
    role: 'user',
    content: msg.messageContent ?? '',
    createTime: msg.createTime,
  };
}

/** 将服务端助手消息转为侧栏视图，解析 meta 中的 patch 与 explainOnly 标志。 */
export function parseAssistantMessageFromServer(msg: AiChatMessageItem): ApiDesignMessageView {
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
  let patch: ApiDesignPatch | undefined;
  const hasPatchFlag = meta.hasPatch === true;
  if (!explainOnly && meta.patchJson && typeof meta.patchJson === 'object') {
    patch = meta.patchJson as ApiDesignPatch;
  }
  const view: ApiDesignMessageView = {
    id: String(msg.aiChatMessageId),
    role: 'assistant',
    content: summary,
    thinkingContent: msg.thinkingContent?.trim() || undefined,
    createTime: msg.createTime,
    aiLlmModelId: msg.aiLlmModelId != null ? String(msg.aiLlmModelId) : undefined,
    vendorName: typeof meta.vendorName === 'string' ? meta.vendorName : undefined,
    modelName: typeof meta.modelName === 'string' ? meta.modelName : undefined,
    patch,
    explainOnly,
    patchPending: hasPatchFlag && !patch && !explainOnly,
  };
  if (patch && !explainOnly) {
    view.diffItems = buildDesignDiffItems(patch);
  }
  return view;
}
