/**
 * AI 多轮会话通用 composable。
 * 封装模型列表、会话 CRUD、思考开关、消息 id 同步、重新生成/编辑重发等共享逻辑。
 */
import { computed, ref, type Ref } from 'vue';
import { ElMessageBox } from 'element-plus';

import {
  deleteAiChatSession,
  getAiChatSession,
  listAiModels,
  truncateAiChatMessagesAfter,
  updateAiChatSessionModel,
  updateAiChatSessionThinking,
  type AiChatMessageDetailMode,
  type AiChatMessageItem,
  type AiChatSessionDetail,
  type AiChatSessionItem,
  type AiModelVendorGroup,
  type AiModelsListResult,
} from '@/api/ai/chat';
import { clearMessageMetaCache } from '@/utils/ai/lazyMessageMeta';
import { AI_CHAT_MESSAGE_PAGE_SIZE } from '@/utils/ai/aiChatMessagePage';
import {
  DRAFT_SESSION_ID,
  isPersistedSessionId,
  normalizeChatSessionList,
  parseSessionThinkingFlag,
} from '@/utils/ai/aiChatSession';
import {
  findModelOption,
  pickDefaultModelId,
  resolveThinkingForUi,
} from '@/utils/ai/aiModelThinking';
import { isServerMessageId } from '@/utils/ai/serverMessageId';

export { DRAFT_SESSION_ID, isPersistedSessionId } from '@/utils/ai/aiChatSession';

/** 面板消息最低约束 */
export interface AiChatMessageBase {
  id: string;
  role: 'user' | 'assistant' | 'system';
  content?: string;
  merged?: boolean;
}

export interface AiChatSessionParseResult<TMessage extends AiChatMessageBase> {
  messages: TMessage[];
  messageAcceptedMap: Record<string, Set<string>>;
}

export interface AiChatSessionState<TMessage extends AiChatMessageBase> {
  modelGroups: Ref<AiModelVendorGroup[]>;
  selectedModelId: Ref<string>;
  loadingModels: Ref<boolean>;
  designing: Ref<boolean>;
  designError: Ref<string>;
  messages: Ref<TMessage[]>;
  messageAcceptedMap: Ref<Record<string, Set<string>>>;
  sessions: Ref<AiChatSessionItem[]>;
  activeSessionId: Ref<string>;
  loadedSessionId: Ref<string>;
  syncedModelId: Ref<string>;
  loadingSessions: Ref<boolean>;
  thinkingEnabled: Ref<boolean>;
  sessionThinkingEnabled: Ref<number | null>;
}

export function createAiChatSessionState<TMessage extends AiChatMessageBase>(): AiChatSessionState<TMessage> {
  return {
    modelGroups: ref([]),
    selectedModelId: ref(''),
    loadingModels: ref(false),
    designing: ref(false),
    designError: ref(''),
    messages: ref([]) as Ref<TMessage[]>,
    messageAcceptedMap: ref({}),
    sessions: ref([]),
    activeSessionId: ref(''),
    loadedSessionId: ref(''),
    syncedModelId: ref(''),
    loadingSessions: ref(false),
    thinkingEnabled: ref(false),
    sessionThinkingEnabled: ref<number | null>(null),
  };
}

export interface UseAiChatSessionOptions<TMessage extends AiChatMessageBase> {
  state: AiChatSessionState<TMessage>;
  /** 拉取当前业务锚点下的会话列表 */
  listSessions: () => Promise<AiChatSessionItem[]>;
  /** 将服务端消息解析为面板视图与初始勾选项 */
  parseSessionMessages: (raw: AiChatMessageItem[]) => AiChatSessionParseResult<TMessage>;
  /** 取消进行中的流式请求 */
  onCancelStream?: () => void;
  /** 进入新对话草稿前的额外清理（如画布 Staging、合并快照等） */
  onClearConversationExtra?: () => void;
  /** 是否允许进入新对话草稿 */
  canStartNewConversation?: () => boolean;
  /** 新对话草稿就绪后的补充初始化 */
  onNewConversationReady?: () => void;
  /** 删除当前活跃会话后的额外清理（如画布 Staging） */
  onDeleteActiveSessionExtra?: () => void;
  /** 消息 id 迁移时的额外 map 同步（如 Staging messageId） */
  onMigrateMessageIds?: (idMap: Map<string, string>) => void;
  /** 截断本地消息后的额外清理（如画布回滚 + 删 Staging 单元） */
  onPruneMessages?: (removedIds: Set<string>) => void;
  /** 重新生成：基于 user 消息再次发起设计 */
  rerunFromUserMessage: (userMsg: TMessage) => Promise<void>;
  /** 编辑重发：从 user 消息提取 composer 内容 */
  extractEditContent: (userMsg: TMessage) => unknown;
  /** 截断时若后续存在已合并 patch，弹窗确认文案 */
  truncateMergedConfirmText?: string;
  /** 截断时弹窗确认文案（业务方可覆盖 truncateMergedConfirmText） */
  truncateStagingConfirmText?: string;
  /** 截断范围内是否存在待确认 Staging；为 true 时也应弹窗确认 */
  hasPendingStagingFromIndex?: (fromIndex: number) => boolean;
  /** bootstrap 完成后回调 */
  onBootstrapComplete?: () => void;
  /** 会话消息摘要加载完成后回调（如 Lazy Patch 预加载活跃 patch） */
  onAfterSessionLoaded?: (messages: TMessage[]) => void | Promise<void>;
  /** 会话消息 detail 模式，默认 summary */
  messageDetail?: AiChatMessageDetailMode;
  /** 会话消息分页条数；>0 时首屏只加载最新一页 */
  messagePageSize?: number;
}

export function useAiChatSession<TMessage extends AiChatMessageBase>(
  options: UseAiChatSessionOptions<TMessage>,
) {
  const {
    state,
    listSessions: fetchSessions,
    parseSessionMessages,
    onCancelStream,
    onClearConversationExtra,
    canStartNewConversation,
    onNewConversationReady,
    onDeleteActiveSessionExtra,
    onMigrateMessageIds,
    onPruneMessages,
    rerunFromUserMessage,
    extractEditContent,
    truncateMergedConfirmText: truncateMergedConfirmTextOption = '后续消息中有已合并的 AI 建议，截断后不会自动回滚。是否继续？',
    truncateStagingConfirmText,
    hasPendingStagingFromIndex,
    onBootstrapComplete,
    onAfterSessionLoaded,
    messageDetail = 'summary',
    messagePageSize = AI_CHAT_MESSAGE_PAGE_SIZE,
  } = options;

  const truncateMergedConfirmText = truncateStagingConfirmText ?? truncateMergedConfirmTextOption;

  const hasMoreOlderMessages = ref(false);
  const loadingOlderMessages = ref(false);
  const totalMessageCount = ref(0);

  const {
    modelGroups,
    selectedModelId,
    loadingModels,
    designing,
    designError,
    messages,
    messageAcceptedMap,
    sessions,
    activeSessionId,
    loadedSessionId,
    syncedModelId,
    loadingSessions,
    thinkingEnabled,
    sessionThinkingEnabled,
  } = state;

  const isDraftSession = computed(() => activeSessionId.value === DRAFT_SESSION_ID);

  function applyThinkingForCurrentModel() {
    thinkingEnabled.value = resolveThinkingForUi(
      sessionThinkingEnabled.value,
      findModelOption(modelGroups.value, selectedModelId.value),
    );
  }

  const thinkingCapable = computed(
    () => findModelOption(modelGroups.value, selectedModelId.value)?.thinkingCapable === true,
  );

  function cancelDesign() {
    onCancelStream?.();
    designing.value = false;
  }

  async function loadModels() {
    loadingModels.value = true;
    designError.value = '';
    try {
      const res = await listAiModels();
      const data = res.data as AiModelsListResult;
      modelGroups.value = data.vendors ?? [];
      selectedModelId.value = pickDefaultModelId(data, modelGroups.value);
      syncedModelId.value = selectedModelId.value;
      applyThinkingForCurrentModel();
    } catch (e: unknown) {
      designError.value = e instanceof Error ? e.message : '加载模型列表失败';
    } finally {
      loadingModels.value = false;
    }
  }

  async function loadSessions() {
    loadingSessions.value = true;
    try {
      const rows = await fetchSessions();
      sessions.value = normalizeChatSessionList(rows);
    } catch (e: unknown) {
      designError.value = e instanceof Error ? e.message : '加载会话失败';
      sessions.value = [];
    } finally {
      loadingSessions.value = false;
    }
  }

  function patchActiveSessionField<K extends keyof AiChatSessionItem>(
    field: K,
    value: AiChatSessionItem[K],
  ) {
    const sid = activeSessionId.value;
    if (!isPersistedSessionId(sid)) return;
    sessions.value = sessions.value.map((s) =>
      s.aiChatSessionId === sid ? { ...s, [field]: value } : s,
    );
  }

  function syncMessageIdsFromServer(serverMessages: AiChatMessageItem[]) {
    let si = 0;
    const idMap = new Map<string, string>();

    for (const local of messages.value) {
      if (local.role === 'system') continue;
      while (si < serverMessages.length && serverMessages[si].messageRole !== local.role) {
        si += 1;
      }
      if (si >= serverMessages.length) break;
      const serverId = String(serverMessages[si].aiChatMessageId);
      si += 1;
      if (local.id !== serverId) {
        idMap.set(local.id, serverId);
      }
    }

    if (idMap.size === 0) return;

    messages.value = messages.value.map((m) =>
      idMap.has(m.id) ? { ...m, id: idMap.get(m.id)! } : m,
    );

    const nextAccepted = { ...messageAcceptedMap.value };
    for (const [oldId, newId] of idMap) {
      if (nextAccepted[oldId]) {
        nextAccepted[newId] = nextAccepted[oldId];
        delete nextAccepted[oldId];
      }
    }
    messageAcceptedMap.value = nextAccepted;
    onMigrateMessageIds?.(idMap);
  }

  async function refreshServerMessageIds() {
    if (!isPersistedSessionId(activeSessionId.value)) return;
    const res = await getAiChatSession(activeSessionId.value, messageDetail, {
      limit: messagePageSize > 0 ? messagePageSize : undefined,
    });
    const detail = res.data as AiChatSessionDetail;
    syncMessageIdsFromServer(detail.messages ?? []);
  }

  function resetMessagePagination() {
    hasMoreOlderMessages.value = false;
    loadingOlderMessages.value = false;
    totalMessageCount.value = 0;
  }

  function applySessionPageMeta(detail: AiChatSessionDetail) {
    if (messagePageSize > 0) {
      totalMessageCount.value = detail.totalMessageCount ?? detail.messages?.length ?? 0;
      hasMoreOlderMessages.value = detail.hasMoreOlder === true;
    } else {
      totalMessageCount.value = detail.messages?.length ?? 0;
      hasMoreOlderMessages.value = false;
    }
  }

  async function loadSession(sessionId: string): Promise<boolean> {
    const nextId = String(sessionId).trim();
    if (!nextId) return false;
    // 同一会话已在内存中：跳过重拉，避免 clear+重灌 Staging 导致画布节点/场景卡片闪烁
    if (nextId === loadedSessionId.value) return true;
    clearMessageMetaCache();
    resetMessagePagination();
    try {
      const res = await getAiChatSession(nextId, messageDetail, {
        limit: messagePageSize > 0 ? messagePageSize : undefined,
      });
      const detail = res.data as AiChatSessionDetail;
      activeSessionId.value = nextId;
      if (detail.session?.currentModelId) {
        selectedModelId.value = String(detail.session.currentModelId);
      }
      sessionThinkingEnabled.value = parseSessionThinkingFlag(detail.session?.thinkingEnabled);
      applyThinkingForCurrentModel();

      const parsed = parseSessionMessages(detail.messages ?? []);
      messages.value = parsed.messages;
      messageAcceptedMap.value = parsed.messageAcceptedMap;
      applySessionPageMeta(detail);
      loadedSessionId.value = nextId;
      syncedModelId.value = selectedModelId.value;
      await onAfterSessionLoaded?.(parsed.messages);
      return true;
    } catch (e: unknown) {
      designError.value = e instanceof Error ? e.message : '加载会话消息失败';
      return false;
    }
  }

  async function loadOlderMessages(): Promise<boolean> {
    if (!hasMoreOlderMessages.value || loadingOlderMessages.value) return false;
    if (!isPersistedSessionId(activeSessionId.value)) return false;
    const oldestId = messages.value[0]?.id;
    if (!oldestId || !isServerMessageId(oldestId)) return false;

    loadingOlderMessages.value = true;
    try {
      const res = await getAiChatSession(activeSessionId.value, messageDetail, {
        limit: messagePageSize,
        beforeMessageId: oldestId,
      });
      const detail = res.data as AiChatSessionDetail;
      const parsed = parseSessionMessages(detail.messages ?? []);
      if (parsed.messages.length === 0) {
        hasMoreOlderMessages.value = false;
        return false;
      }
      messageAcceptedMap.value = {
        ...parsed.messageAcceptedMap,
        ...messageAcceptedMap.value,
      };
      messages.value = [...parsed.messages, ...messages.value];
      applySessionPageMeta(detail);
      return true;
    } catch (e: unknown) {
      designError.value = e instanceof Error ? e.message : '加载更早消息失败';
      return false;
    } finally {
      loadingOlderMessages.value = false;
    }
  }

  async function restoreFirstSessionOrDraft() {
    await loadSessions();
    if (sessions.value.length > 0) {
      await loadSession(sessions.value[0].aiChatSessionId);
    } else if (activeSessionId.value === DRAFT_SESSION_ID) {
      // 内存草稿已打开过：保留消息与 Staging，勿 startNewConversation 清空画布
      return;
    } else {
      startNewConversation();
    }
  }

  async function bootstrap() {
    await loadModels();
    await restoreFirstSessionOrDraft();
    onBootstrapComplete?.();
  }

  function startNewConversation() {
    if (canStartNewConversation && !canStartNewConversation()) return;
    cancelDesign();
    clearMessageMetaCache();
    resetMessagePagination();
    activeSessionId.value = DRAFT_SESSION_ID;
    loadedSessionId.value = '';
    messages.value = [];
    messageAcceptedMap.value = {};
    onClearConversationExtra?.();
    syncedModelId.value = selectedModelId.value;
    sessionThinkingEnabled.value = null;
    applyThinkingForCurrentModel();
    designError.value = '';
    onNewConversationReady?.();
  }

  function discardDraft() {
    cancelDesign();
    if (sessions.value.length > 0) {
      void loadSession(sessions.value[0].aiChatSessionId);
      return;
    }
    startNewConversation();
  }

  async function switchSession(sessionId: string) {
    const nextId = String(sessionId).trim();
    if (!nextId || nextId === loadedSessionId.value) return;
    if (nextId === DRAFT_SESSION_ID) {
      startNewConversation();
      return;
    }
    cancelDesign();
    const previousLoadedId = loadedSessionId.value;
    const ok = await loadSession(nextId);
    if (!ok) {
      activeSessionId.value = previousLoadedId;
    }
  }

  async function deleteSession(sessionId: string) {
    if (sessionId === DRAFT_SESSION_ID) {
      discardDraft();
      return;
    }
    try {
      await deleteAiChatSession(sessionId);
      if (activeSessionId.value === sessionId) {
        activeSessionId.value = '';
        loadedSessionId.value = '';
        messages.value = [];
        messageAcceptedMap.value = {};
        resetMessagePagination();
        onDeleteActiveSessionExtra?.();
      }
      await loadSessions();
      if (!activeSessionId.value && sessions.value.length > 0) {
        await loadSession(sessions.value[0].aiChatSessionId);
      } else if (!isPersistedSessionId(activeSessionId.value) && sessions.value.length === 0) {
        startNewConversation();
      }
    } catch (e: unknown) {
      designError.value = e instanceof Error ? e.message : '删除会话失败';
    }
  }

  async function onModelChange(modelId: string | number) {
    const nextId = String(modelId).trim();
    if (!nextId || nextId === syncedModelId.value) return;
    const previousId = syncedModelId.value;
    if (!isPersistedSessionId(activeSessionId.value)) {
      syncedModelId.value = nextId;
      applyThinkingForCurrentModel();
      return;
    }
    try {
      await updateAiChatSessionModel(activeSessionId.value, nextId);
      syncedModelId.value = nextId;
      applyThinkingForCurrentModel();
      patchActiveSessionField('currentModelId', nextId);
    } catch (e: unknown) {
      selectedModelId.value = previousId;
      designError.value = e instanceof Error ? e.message : '更新会话模型失败';
    }
  }

  async function onThinkingChange(enabled: boolean) {
    thinkingEnabled.value = enabled;
    if (!isPersistedSessionId(activeSessionId.value)) {
      sessionThinkingEnabled.value = enabled ? 1 : 0;
      return;
    }
    try {
      await updateAiChatSessionThinking(activeSessionId.value, enabled);
      sessionThinkingEnabled.value = enabled ? 1 : 0;
      patchActiveSessionField('thinkingEnabled', enabled ? 1 : 0);
    } catch (e: unknown) {
      thinkingEnabled.value = !enabled;
      designError.value = e instanceof Error ? e.message : '更新思考开关失败';
    }
  }

  function findPrecedingUserMessageIndex(fromIndex: number): number {
    for (let i = fromIndex; i >= 0; i -= 1) {
      if (messages.value[i]?.role === 'user') return i;
    }
    return -1;
  }

  /** 重新生成或编辑消息前：若截断范围内有已合并 patch 或待确认 Staging，先征得用户同意 */
  async function confirmTruncateIfMerged(fromIndex: number): Promise<boolean> {
    const hasMerged = messages.value.slice(fromIndex).some((m) => m.merged);
    const hasPendingStaging = hasPendingStagingFromIndex?.(fromIndex) ?? false;
    if (!hasMerged && !hasPendingStaging) return true;
    try {
      await ElMessageBox.confirm(truncateMergedConfirmText, '确认操作', {
        confirmButtonText: '继续',
        cancelButtonText: '取消',
        type: 'warning',
      });
      return true;
    } catch {
      return false;
    }
  }

  /** 从 fromIndex 截断本地消息列表，并触发 onPruneMessages 清理关联业务状态 */
  function pruneLocalStateFromIndex(fromIndex: number) {
    const removedIds = new Set(messages.value.slice(fromIndex).map((m) => m.id));
    messages.value = messages.value.slice(0, fromIndex);

    const nextAccepted = { ...messageAcceptedMap.value };
    for (const id of removedIds) {
      delete nextAccepted[id];
    }
    messageAcceptedMap.value = nextAccepted;
    onPruneMessages?.(removedIds);
  }

  async function truncateOnServer(anchorMessageId: string, inclusive: boolean) {
    if (!isPersistedSessionId(activeSessionId.value)) return;
    if (!isServerMessageId(anchorMessageId)) return;
    await truncateAiChatMessagesAfter(activeSessionId.value, anchorMessageId, inclusive);
  }

  async function regenerateAssistantResponse(assistantMessageId: string) {
    if (designing.value) return;
    const assistantIdx = messages.value.findIndex((m) => m.id === assistantMessageId);
    if (assistantIdx < 0 || messages.value[assistantIdx]?.role !== 'assistant') return;

    const userIdx = findPrecedingUserMessageIndex(assistantIdx - 1);
    if (userIdx < 0) return;
    if (!(await confirmTruncateIfMerged(assistantIdx))) return;

    try {
      await refreshServerMessageIds();
      const userMsg = messages.value[userIdx];
      if (!userMsg || userMsg.role !== 'user') return;
      await truncateOnServer(userMsg.id, false);
      pruneLocalStateFromIndex(assistantIdx);
      await rerunFromUserMessage(userMsg);
    } catch (e: unknown) {
      designError.value = e instanceof Error ? e.message : '重新生成失败';
    }
  }

  async function startEditUserMessage(userMessageId: string): Promise<unknown | null> {
    if (designing.value) return null;
    const userIdx = messages.value.findIndex((m) => m.id === userMessageId);
    if (userIdx < 0 || messages.value[userIdx]?.role !== 'user') return null;
    if (!(await confirmTruncateIfMerged(userIdx))) return null;

    try {
      await refreshServerMessageIds();
      const userMsg = messages.value[userIdx];
      if (!userMsg || userMsg.role !== 'user') return null;
      const content = extractEditContent(userMsg);
      await truncateOnServer(userMsg.id, true);
      pruneLocalStateFromIndex(userIdx);
      return content;
    } catch (e: unknown) {
      designError.value = e instanceof Error ? e.message : '准备编辑失败';
      return null;
    }
  }

  /** 设计过程中收到 session 事件或完成后同步 sessionId，并刷新会话列表 */
  async function afterDesignSessionCreated(sessionId: string) {
    activeSessionId.value = String(sessionId);
    loadedSessionId.value = activeSessionId.value;
    if (sessionThinkingEnabled.value == null) {
      sessionThinkingEnabled.value = thinkingEnabled.value ? 1 : 0;
    }
    await loadSessions();
  }

  /**
   * 强制重拉当前会话消息（清空已加载缓存）。
   * 用于用户取消后展示服务端已落盘的助手半成品。
   */
  async function reloadActiveSession(): Promise<boolean> {
    if (!isPersistedSessionId(activeSessionId.value)) return false;
    loadedSessionId.value = '';
    return loadSession(activeSessionId.value);
  }

  return {
    isDraftSession,
    thinkingCapable,
    applyThinkingForCurrentModel,
    cancelDesign,
    loadModels,
    loadSessions,
    refreshServerMessageIds,
    loadSession,
    reloadActiveSession,
    loadOlderMessages,
    hasMoreOlderMessages,
    loadingOlderMessages,
    totalMessageCount,
    restoreFirstSessionOrDraft,
    bootstrap,
    startNewConversation,
    discardDraft,
    switchSession,
    deleteSession,
    onModelChange,
    onThinkingChange,
    regenerateAssistantResponse,
    startEditUserMessage,
    afterDesignSessionCreated,
  };
}
