/**
 * AI API 助手状态与业务逻辑：patch Diff 勾选与合并写入工作台。
 */
import { type Ref } from 'vue';
import { ElMessage } from 'element-plus';

import {
  listApiDesignChatSessions,
  type AiChatSessionItem,
} from '@/api/ai/chat';
import type { ApiDesignRequestPayload } from '@/api/project/testApiAi';
import {
  createAiChatSessionState,
  DRAFT_SESSION_ID,
  useAiChatSession,
} from '@/composables/ai/useAiChatSession';
import { createClientMessageId, isPersistedSessionId } from '@/utils/ai/aiChatSession';
import {
  resolveAssistantContent,
  resolveFailedAssistantFields,
  resolveThinkingContent,
} from '@/utils/ai/assistantMessageContent';
import { parseAiChatSessionMessages } from '@/utils/ai/parseAiChatSessionMessages';
import { useLazyPatchHydration } from '@/composables/ai/useLazyPatchHydration';

import type {
  ApiDesignResult,
  ApiDesignMessageView,
  ApiDesignPatchChange,
} from '../types/apiDesignAiTypes';
import {
  buildDesignDiffItems,
  parseAssistantMessageFromServer,
  parseUserMessageFromServer,
} from '../types/apiDesignAiTypes';
import { isApiAiAutopilotEnabled } from '../utils/apiAiPreferences';
import { useApiAiStream } from './useApiAiStream';

export { DRAFT_SESSION_ID, createClientMessageId, isPersistedSessionId } from '@/utils/ai/aiChatSession';

export interface ApiAiContext {
  testProjectId: Ref<string>;
  testProjectApiId: Ref<string>;
  preRequestScript?: Ref<string | undefined>;
  postRequestScript?: Ref<string | undefined>;
}

/** 用户确认后写回工作台的变更（不落库） */
export interface ApplyDesignChanges {
  changes: ApiDesignPatchChange[];
}

/** AI API 助手侧栏：会话、流式生成、patch Diff 勾选与合并写入工作台。 */
export function useApiAi(
  context: ApiAiContext,
  onApply: (changes: ApplyDesignChanges) => void,
) {
  const chatState = createAiChatSessionState<ApiDesignMessageView>();
  const {
    messages,
    messageAcceptedMap,
    designing,
    designError,
    selectedModelId,
    activeSessionId,
    thinkingEnabled,
  } = chatState;

  const { streamText, streamThinking, activeTool, runDesignStream, cancelStream } = useApiAiStream();

  const { ensureMessagePatchLoaded, eagerLoadActivePatch } = useLazyPatchHydration<ApiDesignMessageView>({
    messages,
    messageAcceptedMap,
    hydrateAssistant: parseAssistantMessageFromServer,
    buildAcceptedIds: (hydrated) => {
      const items = hydrated.diffItems ?? [];
      return new Set(items.map((i) => i.id));
    },
    preserveLocalFields: (current) => ({ merged: current.merged }),
  });

  /** 组装流式设计请求体，携带当前项目、接口与脚本草稿。 */
  function buildPayload(prompt: string): ApiDesignRequestPayload {
    return {
      testProjectId: context.testProjectId.value,
      testProjectApiId: context.testProjectApiId.value,
      aiLlmModelId: selectedModelId.value,
      aiChatSessionId: isPersistedSessionId(activeSessionId.value) ? activeSessionId.value : null,
      prompt,
      preRequestScript: context.preRequestScript?.value ?? '',
      postRequestScript: context.postRequestScript?.value ?? '',
      thinkingEnabled: thinkingEnabled.value,
      autopilotEnabled: isApiAiAutopilotEnabled(),
    };
  }

  /**
   * 将流式完成结果追加为助手消息，并初始化 Diff 默认勾选。
   * @returns 新助手消息 id（供全自动立即 merge）
   */
  function appendAssistantMessage(data: ApiDesignResult): string {
    const messageId = createClientMessageId();
    const explainOnly = data.explainOnly === true;
    const patch = explainOnly ? undefined : data.patch;
    const autopilot = isApiAiAutopilotEnabled();
    const assistantMessage: ApiDesignMessageView = {
      id: messageId,
      role: 'assistant',
      content: resolveAssistantContent({
        summary: data.summary,
        streamText: streamText.value,
        hasPatch: Boolean(patch && !explainOnly),
        emptyPatchPlaceholder: autopilot
          ? '已生成接口变更建议，将自动应用到工作台。'
          : '已生成接口变更建议，请勾选后合并到工作台。',
      }),
      thinkingContent: resolveThinkingContent(data.thinkingContent, streamThinking.value),
      aiLlmModelId: data.aiLlmModelId,
      vendorName: data.vendorName,
      modelName: data.modelName,
      patch,
      validation: data.validation,
      explainOnly,
      diffItems: patch ? buildDesignDiffItems(patch) : undefined,
    };
    messages.value = [...messages.value, assistantMessage];
    if (patch && !explainOnly) {
      const items = assistantMessage.diffItems ?? [];
      messageAcceptedMap.value = {
        ...messageAcceptedMap.value,
        [messageId]: new Set(items.map((i) => i.id)),
      };
    }
    return messageId;
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

  const chat = useAiChatSession<ApiDesignMessageView>({
    state: chatState,
    listSessions: async () => {
      const projectId = context.testProjectId.value?.trim();
      const apiId = context.testProjectApiId.value?.trim();
      if (!projectId || !apiId) return [];
      const res = await listApiDesignChatSessions(projectId, apiId);
      return (res.rows ?? []) as AiChatSessionItem[];
    },
    parseSessionMessages: (raw) =>
      parseAiChatSessionMessages(raw, {
        parseUser: parseUserMessageFromServer,
        parseAssistant: parseAssistantMessageFromServer,
        buildAcceptedIds: (view) => {
          if (!view.patch || view.explainOnly) return undefined;
          const items = view.diffItems ?? [];
          return new Set(items.map((i) => i.id));
        },
      }),
    onCancelStream: () => cancelStream(),
    truncateMergedConfirmText:
      '后续消息中有已应用到工作台的 AI 建议，截断后不会自动回滚。是否继续？',
    rerunFromUserMessage: (userMsg) => executeDesignRequest(userMsg.content),
    extractEditContent: (userMsg) => userMsg.content,
    onAfterSessionLoaded: eagerLoadActivePatch,
  });

  /** 发起一次设计请求：流式调用、刷新会话 id 与消息列表。 */
  async function executeDesignRequest(prompt: string) {
    designing.value = true;
    try {
      const autopilot = isApiAiAutopilotEnabled();
      const data = await runDesignStream(buildPayload(prompt));
      if (data.aiChatSessionId) {
        await chat.afterDesignSessionCreated(String(data.aiChatSessionId));
      }
      const messageId = appendAssistantMessage(data);
      // 全自动须在 refreshServerMessageIds 前 merge，避免 client id 被替换后找不到消息
      if (
        autopilot
        && data.explainOnly !== true
        && data.patch
      ) {
        acceptAllForMessage(messageId);
        await mergeMessagePatch(messageId);
        ElMessage.success('全自动已应用到工作台（未保存到接口库）');
      }
      await chat.refreshServerMessageIds();
    } catch (e: unknown) {
      if (e instanceof DOMException && e.name === 'AbortError') {
        messages.value = [
          ...messages.value,
          { id: createClientMessageId(), role: 'system', content: '已取消生成' },
        ];
      } else {
        const msg = e instanceof Error ? e.message : 'AI 助手请求失败';
        designError.value = msg;
        appendFailedAssistantMessage(msg);
      }
    } finally {
      designing.value = false;
      streamText.value = '';
      streamThinking.value = '';
    }
  }

  /** 发送用户消息并触发设计；默认先追加 user 气泡。 */
  async function sendMessage(prompt: string, options?: { appendUser?: boolean }) {
    const text = prompt.trim();
    if (!text) return;
    if (!selectedModelId.value) {
      designError.value = '请选择 AI 模型';
      return;
    }

    designError.value = '';
    const appendUser = options?.appendUser !== false;
    if (appendUser) {
      messages.value = [
        ...messages.value,
        { id: createClientMessageId(), role: 'user', content: text },
      ];
    }

    await executeDesignRequest(text);
  }

  /** 切换接口或关闭面板时清空本地会话与消息状态。 */
  function resetPanel() {
    chat.cancelDesign();
    chatState.activeSessionId.value = DRAFT_SESSION_ID;
    messages.value = [];
    messageAcceptedMap.value = {};
    chatState.loadedSessionId.value = '';
    chatState.sessions.value = [];
    designError.value = '';
  }

  /** 切换单条 Diff 项的勾选状态。 */
  function toggleMessageAccepted(messageId: string, itemId: string, checked?: boolean) {
    const current = messageAcceptedMap.value[messageId] ?? new Set<string>();
    const next = new Set(current);
    const shouldAccept = checked !== undefined ? checked : !next.has(itemId);
    if (shouldAccept) next.add(itemId);
    else next.delete(itemId);
    messageAcceptedMap.value = { ...messageAcceptedMap.value, [messageId]: next };
  }

  /** 勾选该助手消息下的全部 Diff 项。 */
  function acceptAllForMessage(messageId: string) {
    const msg = messages.value.find((m) => m.id === messageId);
    if (!msg?.patch) return;
    const items = msg.diffItems ?? buildDesignDiffItems(msg.patch);
    messageAcceptedMap.value = {
      ...messageAcceptedMap.value,
      [messageId]: new Set(items.map((i) => i.id)),
    };
  }

  /** 返回某条助手消息当前已勾选的 Diff 项 id 集合。 */
  function acceptedIdsFor(messageId: string): Set<string> {
    return messageAcceptedMap.value[messageId] ?? new Set<string>();
  }

  /** 是否允许将已勾选项合并进工作台（未合并且非纯说明）。 */
  function canMergeMessage(messageId: string): boolean {
    const msg = messages.value.find((m) => m.id === messageId);
    if (!msg?.patch || msg.merged || msg.explainOnly) return false;
    return acceptedIdsFor(messageId).size > 0;
  }

  /** 将已勾选的 patch 变更交给父组件写入工作台，并标记消息已应用。 */
  async function mergeMessagePatch(messageId: string) {
    await ensureMessagePatchLoaded(messageId);
    const msg = messages.value.find((m) => m.id === messageId);
    if (!msg?.patch || msg.merged) return;
    const accepted = acceptedIdsFor(messageId);
    if (accepted.size === 0) return;

    const applied: ApiDesignPatchChange[] = [];
    for (const item of msg.diffItems ?? []) {
      if (!accepted.has(item.id)) continue;
      if (item.change) applied.push(item.change);
    }
    if (applied.length === 0) return;
    onApply({ changes: applied });

    const idx = messages.value.findIndex((m) => m.id === messageId);
    if (idx >= 0) {
      const updated = [...messages.value];
      updated[idx] = { ...updated[idx], merged: true };
      messages.value = updated;
    }
  }

  return {
    modelGroups: chatState.modelGroups,
    selectedModelId: chatState.selectedModelId,
    loadingModels: chatState.loadingModels,
    designing: chatState.designing,
    designError: chatState.designError,
    messages: chatState.messages,
    messageAcceptedMap: chatState.messageAcceptedMap,
    sessions: chatState.sessions,
    activeSessionId: chatState.activeSessionId,
    isDraftSession: chat.isDraftSession,
    loadingSessions: chatState.loadingSessions,
    thinkingEnabled: chatState.thinkingEnabled,
    thinkingCapable: chat.thinkingCapable,
    streamText,
    streamThinking,
    activeTool,
    bootstrap: chat.bootstrap,
    resetPanel,
    startNewConversation: chat.startNewConversation,
    switchSession: chat.switchSession,
    deleteSession: chat.deleteSession,
    onModelChange: chat.onModelChange,
    onThinkingChange: chat.onThinkingChange,
    sendMessage,
    cancelDesign: chat.cancelDesign,
    regenerateAssistantResponse: chat.regenerateAssistantResponse,
    startEditUserMessage: chat.startEditUserMessage,
    loadOlderMessages: chat.loadOlderMessages,
    hasMoreOlderMessages: chat.hasMoreOlderMessages,
    loadingOlderMessages: chat.loadingOlderMessages,
    totalMessageCount: chat.totalMessageCount,
    toggleMessageAccepted,
    acceptAllForMessage,
    acceptedIdsFor,
    canMergeMessage,
    mergeMessagePatch,
    ensureMessagePatchLoaded,
  };
}
