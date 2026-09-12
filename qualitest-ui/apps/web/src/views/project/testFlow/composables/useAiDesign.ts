/**
 * 测试流画布 AI 设计 composable。
 *
 * 职责：
 * - 加载模型与会话列表，刷新后恢复历史消息
 * - SSE 流式设计，支持取消
 * - patch 到达 → hydratePatchToStaging / createAiStagingHydration（画布就地确认）
 */
import { reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';

import {
  listAiChatSessions,
  pruneFlowDesignClientIdMap,
  type AiChatSessionItem,
} from '@/api/ai/chat';
import type { TestFlowDesignRequestPayload } from '@/api/project/testFlowAi';

import { parseAiChatSessionMessages } from '@/utils/ai/parseAiChatSessionMessages';
import { useLazyPatchHydration } from '@/composables/ai/useLazyPatchHydration';

import {
  createAiChatSessionState,
  useAiChatSession,
} from '@/composables/ai/useAiChatSession';

import { toGraphJson } from '../graphAdapter';
import { useAiDesignStream } from './useAiDesignStream';
import { useFlowCanvasStore } from '../stores/flowCanvasStore';
import { useAiStagingStore } from '../stores/aiStagingStore';
import { useRunLibraryStore } from '../stores/runLibraryStore';
import { useFlowGraph } from './useFlowGraph';
import { isAutopilotEnabled } from '../utils/aiDesignPreferences';
import type { AiDesignMessageView, AiDesignSystemAction, FlowDesignPatch, TestFlowDesignResult } from '../types/aiDesignTypes';
import { parseAssistantFromServer, parseUserFromServer } from '../types/aiDesignTypes';
import type { ComposerSendPayload } from './mentionComposer';
import {
  createClientMessageId,
  DRAFT_SESSION_ID,
  isPersistedSessionId,
} from '@/utils/ai/aiChatSession';
import { buildComposerPayloadFromUserMessage } from '@/utils/ai/buildComposerPayloadFromUser';
import {
  resolveAssistantContent,
  resolveFailedAssistantFields,
  resolveThinkingContent,
} from '@/utils/ai/assistantMessageContent';
import type { ComposerDoc } from '../types/mentionTypes';
import { COMPOSER_DOC_VERSION } from '../types/mentionTypes';
import { createAiStagingHydration } from './useAiStagingHydration';
import { openPendingStagingReview } from './useStagingNavigation';
import { buildFlowGraphInput } from '../utils/stagingGraphInput';
import {
  clearAllStagingState,
  hasPendingStagingFromMessageIndex,
  revertPendingStagingForMessageIds,
} from '../utils/stagingCleanup';
import {
  bindStagingAcceptanceMaps,
  ensureStagingAcceptanceEntry,
  migrateStagingAcceptanceMaps,
  pruneStagingAcceptanceForMessages,
  resetStagingAcceptanceMaps,
} from '../utils/stagingAcceptance';
import { restorePendingStagingIfNeeded as restorePendingStagingFromMessages } from '../utils/stagingSessionRestore';
import { graphObjectIdFromUnit } from '../utils/stagingUnitIds';

export { createClientMessageId, DRAFT_SESSION_ID, isPersistedSessionId } from '@/utils/ai/aiChatSession';

/** 模块级会话状态：关闭面板不销毁，切换 testFlow 时由 resetPanel 清空 */
const chatState = createAiChatSessionState<AiDesignMessageView>();
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
} = chatState;

/** 已取消的 Staging 单元 id；会话重灌时把对应单元标为 rejected，避免再次待确认 */
const messageRejectedMap = ref<Record<string, Set<string>>>({});

bindStagingAcceptanceMaps(messageAcceptedMap, messageRejectedMap);

/** Run 详情「AI 修复」预填文案与 runId，由面板消费后插入 chip */
const designContext = reactive<{
  pendingRunId: string;
  pendingPrompt: string;
  pendingNodeId: string;
}>({
  pendingRunId: '',
  pendingPrompt: '',
  pendingNodeId: '',
});

/** 节点右键「添加到对话」触发计数，供面板 watch 消费 pendingNodeId */
const pendingNodeToken = ref(0);

const { streamText, streamThinking, activeTool, runDesignStream, cancelStream } = useAiDesignStream();

function attachMessageView(raw: Parameters<typeof parseAssistantFromServer>[0]): AiDesignMessageView {
  return parseAssistantFromServer(raw);
}

const stagingHydration = createAiStagingHydration(messages);

const { ensureMessagePatchLoaded, eagerLoadActivePatch } = useLazyPatchHydration<AiDesignMessageView>({
  messages,
  messageAcceptedMap,
  hydrateAssistant: (raw) => attachMessageView(raw),
  onHydrated: stagingHydration.onPatchHydrated,
  buildAcceptedIds: (hydrated) => messageAcceptedMap.value[hydrated.id] ?? new Set<string>(),
});

function applyPendingRunContextModule() {
  const store = useFlowCanvasStore();
  const runId = store.pendingAiDesignRunId;
  if (!runId) return;
  designContext.pendingRunId = runId;
  const runLib = useRunLibraryStore();
  const run = runLib.runs.find((r) => r.id === runId) ?? runLib.selectedRun;
  const has401 = (run?.steps ?? []).some((s) => Number(s?.http?.status) === 401);
  designContext.pendingPrompt = has401
    ? '这次失败含 HTTP 401（未认证/凭证无效）。请结合 @run 详情检查鉴权：是否漏 Authorization/Bearer、是否缺登录抽取 flow.token 或 flow.adminToken、双端是否串用；用对应 submit_* 单元工具给出修改建议，确认前不改图。'
    : '帮我分析这次失败原因并给出修改建议';
  store.pendingAiDesignRunId = '';
}

function migrateExtraMessageIdMaps(idMap: Map<string, string>) {
  useAiStagingStore().migrateMessageIds(idMap);
  migrateStagingAcceptanceMaps(idMap);
}

/**
 * 消息被截断时的 Staging 清理：先回滚这些消息在画布上的 pending 效果，再删 store 记录。
 */
function pruneExtraMessageState(removedIds: Set<string>) {
  revertPendingStagingForMessageIds(removedIds);
  const stagingStore = useAiStagingStore();
  const snowflakeIds: string[] = [];
  for (const id of removedIds) {
    for (const unit of stagingStore.listUnitsForMessage(id)) {
      const { nodeId, edgeId } = graphObjectIdFromUnit(unit);
      if (nodeId && /^\d+$/.test(nodeId)) snowflakeIds.push(nodeId);
      if (edgeId && /^\d+$/.test(edgeId)) snowflakeIds.push(edgeId);
    }
    stagingStore.removeMessageUnits(id);
  }
  pruneStagingAcceptanceForMessages(removedIds);
  const sessionId = activeSessionId.value;
  if (isPersistedSessionId(sessionId) && snowflakeIds.length) {
    void pruneFlowDesignClientIdMap(sessionId, [...new Set(snowflakeIds)]).catch(() => {
      /* 映射摘除失败不阻断 UI */
    });
  }
}

/** 新建对话或等价「清空会话」时，撤掉画布 Staging 并重置 store */
function clearConversationStaging() {
  clearAllStagingState();
  resetStagingAcceptanceMaps();
}

/** 由 useAiDesign 注入，供 chat.rerunFromUserMessage 调用 */
let executeDesignRequestRef: (payload: ComposerSendPayload) => Promise<void> = async () => {};

const chat = useAiChatSession<AiDesignMessageView>({
  state: chatState,
  listSessions: async () => {
    const store = useFlowCanvasStore();
    if (store.canvasMode === 'template') return [];
    if (!store.testFlowId || !store.testProjectId) return [];
    const res = await listAiChatSessions(store.testProjectId, store.testFlowId);
    return (res.rows ?? []) as AiChatSessionItem[];
  },
  parseSessionMessages: (raw) => {
    const prevAccepted = messageAcceptedMap.value;
    const prevRejected = messageRejectedMap.value;
    const parsed = parseAiChatSessionMessages(raw, {
      parseUser: parseUserFromServer,
      parseAssistant: (msg) => attachMessageView(msg),
    });

    const mergedAccepted: Record<string, Set<string>> = { ...parsed.messageAcceptedMap };
    const mergedRejected: Record<string, Set<string>> = {};

    for (const msg of parsed.messages) {
      if (prevAccepted[msg.id]?.size) {
        mergedAccepted[msg.id] = new Set(prevAccepted[msg.id]);
      }
      if (prevRejected[msg.id]?.size) {
        mergedRejected[msg.id] = new Set(prevRejected[msg.id]);
      }
    }

    messageRejectedMap.value = mergedRejected;

    return {
      messages: parsed.messages,
      messageAcceptedMap: mergedAccepted,
    };
  },
  onCancelStream: () => cancelStream(),
  // 新建对话：清空 Staging，避免上一会话残留
  onClearConversationExtra: clearConversationStaging,
  // 删除当前会话：同样清空 Staging
  onDeleteActiveSessionExtra: clearConversationStaging,
  onMigrateMessageIds: migrateExtraMessageIdMaps,
  // 截断消息：先回滚画布再删单元
  onPruneMessages: pruneExtraMessageState,
  // 编辑/重新生成前：检测截断范围内是否还有待确认项
  hasPendingStagingFromIndex: (fromIndex) =>
    hasPendingStagingFromMessageIndex(messages.value, fromIndex),
  truncateStagingConfirmText:
    '后续消息中有 AI 建议变更，截断后待确认项将丢失。是否继续？',
  rerunFromUserMessage: (userMsg) =>
    executeDesignRequestRef(
      buildComposerPayloadFromUserMessage(userMsg.content, userMsg.composerDoc),
    ),
  extractEditContent: (userMsg) =>
    userMsg.composerDoc ??
    ({
      version: COMPOSER_DOC_VERSION,
      nodes: [{ type: 'text', text: userMsg.content }],
    } satisfies ComposerDoc),
  onBootstrapComplete: applyPendingRunContextModule,
  // 会话消息加载完成后：拉齐活跃 patch，再按消息重建 Staging 供确认
  onAfterSessionLoaded: async (loadedMessages) => {
    await eagerLoadActivePatch(loadedMessages);
    await stagingHydration.onSessionLoaded();
  },
});

let testFlowIdWatcherBound = false;

export function useAiDesign() {
  const store = useFlowCanvasStore();
  const stagingStore = useAiStagingStore();

  if (!testFlowIdWatcherBound) {
    testFlowIdWatcherBound = true;
    watch(
      () => useFlowCanvasStore().testFlowId,
      () => {
        resetPanel();
      },
    );
  }

  /** 读取 store 中的 Run 修复待办，供输入框预插 run chip */
  function applyPendingRunContext() {
    applyPendingRunContextModule();
  }

  /** 消费并清空 Run 修复预填上下文 */
  function consumePendingRunContext(): { runId: string; prompt: string } | null {
    if (!designContext.pendingRunId) return null;
    const ctx = {
      runId: designContext.pendingRunId,
      prompt: designContext.pendingPrompt || '帮我分析这次失败原因并给出修改建议',
    };
    designContext.pendingRunId = '';
    designContext.pendingPrompt = '';
    return ctx;
  }

  /** 节点右键「添加到对话」：打开侧栏并在输入框插入节点 chip */
  function queueNodeForChat(nodeId: string) {
    const id = String(nodeId || '').trim();
    if (!id || !store.nodes.some((n: { id: string }) => n.id === id)) return;
    designContext.pendingNodeId = id;
    pendingNodeToken.value += 1;
    store.openAiDesignPanel();
  }

  /** 消费并清空待插入的节点 id */
  function consumePendingNodeContext(): string | null {
    const id = designContext.pendingNodeId.trim();
    designContext.pendingNodeId = '';
    return id || null;
  }

  /** 切换测试流时清空状态 */
  function resetPanel() {
    chat.cancelDesign();
    messages.value = [];
    messageAcceptedMap.value = {};
    messageRejectedMap.value = {};
    sessions.value = [];
    activeSessionId.value = '';
    loadedSessionId.value = '';
    syncedModelId.value = '';
    designError.value = '';
    thinkingEnabled.value = false;
    sessionThinkingEnabled.value = null;
    designContext.pendingRunId = '';
    designContext.pendingPrompt = '';
    designContext.pendingNodeId = '';
    pendingNodeToken.value = 0;
    stagingStore.reset();
  }

  /**
   * 打开 AI 助手侧栏时，在 Staging 已被画布重载清空的情况下按会话消息回灌待确认变更。
   * 若画布上仍有待确认单元则不做任何事，避免重复清空再灌入导致节点闪烁。
   */
  async function restorePendingStagingIfNeeded() {
    await restorePendingStagingFromMessages({
      pendingCount: stagingStore.pendingCount,
      messages: messages.value,
      // 列表消息可能只有 patchPending，需先拉齐完整 patch
      eagerLoadActivePatch: (msgs) => eagerLoadActivePatch(msgs as AiDesignMessageView[]),
      // 按当前消息重建 Staging，并恢复本会话内已确认/已拒绝进度
      onSessionLoaded: () => stagingHydration.onSessionLoaded(),
    });
  }

  /** 追加一条本地 system 消息；可附带操作按钮（如保存提示） */
  function appendLocalSystemMessage(text: string, actions?: AiDesignSystemAction[]) {
    messages.value = [
      ...messages.value,
      {
        id: createClientMessageId(),
        role: 'system',
        content: text,
        actions,
      },
    ];
  }

  /** 移除 system 消息上的操作按钮，通常在保存成功后调用 */
  function clearSystemMessageActions(messageId: string) {
    const index = messages.value.findIndex((m) => m.id === messageId);
    if (index < 0) return;
    const msg = messages.value[index];
    if (msg.role !== 'system' || !msg.actions?.length) return;
    const updated = [...messages.value];
    updated[index] = { ...msg, actions: undefined };
    messages.value = updated;
  }

  /** 构建设计请求体；graph_json 排除尚未确认的 Staging，避免 AI 基于临时对象继续改图 */
  function buildDesignPayload(payload: ComposerSendPayload): TestFlowDesignRequestPayload {
    const graphJson = toGraphJson({
      ...buildFlowGraphInput(store),
      stagingFilter: stagingStore.buildPersistFilter(),
    });
    const isTemplate = store.canvasMode === 'template';
    const base: TestFlowDesignRequestPayload = {
      testFlowId: store.testFlowId,
      testProjectId: store.testProjectId || undefined,
      aiLlmModelId: selectedModelId.value,
      aiChatSessionId: isPersistedSessionId(activeSessionId.value) ? activeSessionId.value : null,
      prompt: payload.prompt,
      mentions: payload.mentions.length ? payload.mentions : undefined,
      composerDoc: payload.doc,
      graphJson,
      thinkingEnabled: thinkingEnabled.value,
      autopilotEnabled: !isTemplate && isAutopilotEnabled(),
    };
    if (isTemplate) {
      base.designMode = 'template';
      base.testFlowId = '0';
      base.testProjectId = undefined;
      base.templateFlowKey = store.testFlowId || undefined;
      base.templateApis = (store.templateApiCatalog || []).map((e) => ({
        ...(e.api || {}),
        testProjectApiId: e.syntheticId,
      }));
      base.autopilotEnabled = false;
    }
    return base;
  }

  /**
   * 发送用户消息并走 SSE 流式设计。
   * appendUser=false 时用于重新生成（保留已有 user 气泡）。
   */
  async function sendMessage(payload: ComposerSendPayload, options?: { appendUser?: boolean }) {
    if (!payload.prompt.trim()) return;
    if (!selectedModelId.value) {
      designError.value = '请选择 AI 模型';
      return;
    }

    const appendUser = options?.appendUser !== false;
    designError.value = '';
    streamText.value = '';
    streamThinking.value = '';
    if (appendUser) {
      const userMessage: AiDesignMessageView = {
        id: createClientMessageId(),
        role: 'user',
        content: payload.prompt,
        composerDoc: payload.doc,
      };
      messages.value = [...messages.value, userMessage];
    }

    await executeDesignRequest(payload);
  }

  /** 执行一轮 SSE 设计（不含追加 user 气泡） */
  async function executeDesignRequest(payload: ComposerSendPayload) {
    designing.value = true;
    const { loadFlow } = useFlowGraph();
    try {
      const data = await runDesignStream(buildDesignPayload(payload), {
        onGraphCommitted: (testFlowId) => {
          clearAllStagingState();
          resetStagingAcceptanceMaps();
          void loadFlow(testFlowId)
            .then(() => {
              ElMessage.success('全自动已落库，画布已同步');
            })
            .catch(() => {
              ElMessage.warning('已落库，但刷新画布失败，请手动重新打开测试流');
            });
        },
      });
      if (data.aiChatSessionId) {
        await chat.afterDesignSessionCreated(String(data.aiChatSessionId));
      }
      appendAssistantMessage(data);
      await chat.refreshServerMessageIds();
    } catch (e: unknown) {
      if (e instanceof DOMException && e.name === 'AbortError') {
        appendLocalSystemMessage('已取消设计');
      } else {
        const msg = e instanceof Error ? e.message : 'AI 助手请求失败';
        designError.value = msg;
        // 失败也必须在对话里留下助手气泡，避免「有提问、无回复」
        appendFailedAssistantMessage(msg);
      }
    } finally {
      designing.value = false;
      streamText.value = '';
      streamThinking.value = '';
    }
  }
  executeDesignRequestRef = executeDesignRequest;

  /**
   * 把设计接口响应转成助手消息写入列表。
   * explainOnly（本轮无成功 submit_*）时不带 patch、不灌 Staging；
   * 有 patch 则灌入待确认单元；有素材提案则挂在消息上供卡片展示。
   */
  function appendAssistantMessage(data: TestFlowDesignResult) {
    const messageId = createClientMessageId();
    // 纯答疑：清空 patch，避免误开 Staging
    const explainOnly = data.explainOnly === true;
    const patch = explainOnly ? undefined : data.patch;
    const assetProposals = Array.isArray(data.assetProposals) && data.assetProposals.length > 0
      ? data.assetProposals
      : undefined;
    const assistantMessage: AiDesignMessageView = {
      id: messageId,
      role: 'assistant',
      content: resolveAssistantContent({
        summary: data.summary,
        streamText: streamText.value,
        hasPatch: Boolean(patch && !explainOnly),
        emptyPatchPlaceholder: '已生成流程变更建议，请在画布上逐项确认。',
      }),
      thinkingContent: resolveThinkingContent(data.thinkingContent, streamThinking.value),
      aiLlmModelId: data.aiLlmModelId,
      vendorName: data.vendorName,
      modelName: data.modelName,
      patch,
      validation: data.validation,
      explainOnly,
      assetProposals,
    };
    messages.value = [...messages.value, assistantMessage];

    if (patch && !explainOnly) {
      ensureStagingAcceptanceEntry(messageId);
      void stagingHydration.hydrateStagingForMessage(messageId, patch).then(() => {
        openPendingStagingReview(store.edges);
      });
    }
  }

  /** 设计失败时写入助手回复（保留已流式输出的思考过程） */
  function appendFailedAssistantMessage(errorMessage: string) {
    messages.value = [
      ...messages.value,
      {
        id: createClientMessageId(),
        role: 'assistant',
        ...resolveFailedAssistantFields({
          errorMessage,
          streamText: streamText.value,
          streamThinking: streamThinking.value,
        }),
      },
    ];
  }

  return {
    modelGroups,
    selectedModelId,
    loadingModels,
    designing,
    designError,
    messages,
    sessions,
    activeSessionId,
    isDraftSession: chat.isDraftSession,
    loadingSessions,
    thinkingEnabled,
    thinkingCapable: chat.thinkingCapable,
    streamText,
    streamThinking,
    activeTool,
    bootstrap: chat.bootstrap,
    loadSessions: chat.loadSessions,
    startNewConversation: chat.startNewConversation,
    discardDraft: chat.discardDraft,
    switchSession: chat.switchSession,
    deleteSession: chat.deleteSession,
    onModelChange: chat.onModelChange,
    onThinkingChange: chat.onThinkingChange,
    sendMessage,
    cancelDesign: chat.cancelDesign,
    regenerateAssistantResponse: chat.regenerateAssistantResponse,
    startEditUserMessage: chat.startEditUserMessage as (id: string) => Promise<ComposerDoc | null>,
    loadOlderMessages: chat.loadOlderMessages,
    hasMoreOlderMessages: chat.hasMoreOlderMessages,
    loadingOlderMessages: chat.loadingOlderMessages,
    totalMessageCount: chat.totalMessageCount,
    applyPendingRunContext,
    consumePendingRunContext,
    pendingNodeToken,
    queueNodeForChat,
    consumePendingNodeContext,
    ensureMessagePatchLoaded,
    clearSystemMessageActions,
    /** 侧栏打开后：Staging 已空时按会话消息回灌待确认变更 */
    restorePendingStagingIfNeeded,
  };
}
