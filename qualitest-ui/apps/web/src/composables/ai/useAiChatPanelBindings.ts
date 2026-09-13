/**
 * AI 助手面板壳层绑定。
 *
 * 把各业务面板都要做的 UI 胶水逻辑集中在这里，包括：
 * - 绑定 AiChatShell 组件实例
 * - 流式生成时在列表末尾插入占位 assistant 消息
 * - 消息列表智能贴底、向上分页、patch 懒加载观察
 * - 「回到最新」：jumpToBottom 强制贴底；stickToBottom 控制悬浮钮显隐
 * - 会话 Tab 切换/删除/草稿关闭
 * - 判断某条消息是否可显示「编辑」「重新生成」等操作按钮
 */
import { computed, nextTick, ref, watch, type Ref } from 'vue';

import AiChatShell from '@/components/ai/AiChatShell.vue';
import { useAiChatPanelList } from '@/composables/ai/useAiChatPanelList';
import { createClientMessageId, DRAFT_SESSION_ID } from '@/utils/ai/aiChatSession';
import { isPatchPendingMessage, type AiPatchMessageView } from '@/utils/ai/aiPatchMessageSelectors';

/** 面板渲染消息所需的最低字段 */
export interface AiChatRenderableMessage {
  id: string;
  role: string;
  content?: string;
}

/** useAiChatPanelBindings 入参 */
export interface UseAiChatPanelBindingsOptions<TMessage extends AiChatRenderableMessage> {
  /** 当前会话已加载的消息列表 */
  messages: Ref<TMessage[]>;
  /** 是否正在等待 AI 流式回复 */
  designing: Ref<boolean>;
  /** 当前选中的会话 id（含本地草稿 id） */
  activeSessionId: Ref<string>;
  /** 向上滚动触顶时加载更早一页消息，返回是否成功加载 */
  loadOlderMessages: () => Promise<boolean>;
  /** 某条消息的 patch 进入视口时按需拉取详情 */
  ensureMessagePatchLoaded: (messageId: string) => Promise<boolean>;
  /** 切换到指定会话 */
  switchSession: (sessionId: string) => void;
  /** 关闭本地草稿会话时的处理（不落库的新对话） */
  onDraftSessionRemove: () => void;
  /** 删除已持久化的会话 */
  deleteSession: (sessionId: string) => Promise<void>;
  /** 对某条 assistant 消息重新发起生成 */
  regenerateAssistantResponse: (messageId: string) => Promise<void>;
  /** 切换会话后的额外清理，例如清空输入框 */
  onActiveSessionChange?: () => void;
}

export function useAiChatPanelBindings<TMessage extends AiChatRenderableMessage>(
  options: UseAiChatPanelBindingsOptions<TMessage>,
) {
  /** AiChatShell 根组件 ref，供模板绑定和滚动会话 Tab */
  const shellRef = ref<InstanceType<typeof AiChatShell> | null>(null);
  /** 流式生成期间占位 assistant 消息的临时 id */
  const streamingMessageId = ref<string | null>(null);

  // 开始流式生成时创建占位 id，结束后清除
  watch(options.designing, (active) => {
    streamingMessageId.value = active ? createClientMessageId() : null;
  });

  /**
   * 实际渲染用的消息列表。
   * 流式进行中会在末尾追加一条空内容的 assistant，用于展示打字机效果。
   */
  const displayMessages = computed((): TMessage[] => {
    if (!options.designing.value || !streamingMessageId.value) {
      return options.messages.value;
    }
    return [
      ...options.messages.value,
      { id: streamingMessageId.value, role: 'assistant', content: '' } as TMessage,
    ];
  });

  const { onListScroll, onLoadOlderMessages, stickToBottom, scrollToBottom } = useAiChatPanelList({
    displayMessages,
    messageCount: computed(() => displayMessages.value.length),
    designing: options.designing,
    loadOlderMessages: options.loadOlderMessages,
    ensureMessagePatchLoaded: options.ensureMessagePatchLoaded,
    shellRef,
  });

  /**
   * 「回到最新」按钮点击：强制滚到列表最底并重新打开贴底跟随。
   */
  function jumpToBottom() {
    void scrollToBottom(true);
  }

  // 切换会话时执行场景清理，并把当前 Tab 滚入可视区域
  watch(options.activeSessionId, () => {
    options.onActiveSessionChange?.();
    void nextTick(() => shellRef.value?.scrollActiveSessionIntoView());
  });

  /** 生成中、system 消息、流式占位消息不显示编辑/重新生成按钮 */
  function canShowMessageActions(msg: TMessage): boolean {
    if (options.designing.value) return false;
    if (msg.role !== 'user' && msg.role !== 'assistant') return false;
    if (msg.id === streamingMessageId.value) return false;
    return true;
  }

  /** 消息 patch 详情尚未加载，需要在 DOM 上标记以便 IntersectionObserver 触发懒加载 */
  function isMessagePatchPending(msg: TMessage): boolean {
    return isPatchPendingMessage(msg as AiPatchMessageView);
  }

  function onSessionChange(sessionId: string) {
    options.switchSession(sessionId);
  }

  /** 删除草稿走 onDraftSessionRemove，删除已保存会话走后端接口 */
  async function onSessionRemove(sessionId: string) {
    if (sessionId === DRAFT_SESSION_ID) {
      options.onDraftSessionRemove();
      return;
    }
    await options.deleteSession(sessionId);
  }

  async function onRegenerateAssistant(messageId: string) {
    await options.regenerateAssistantResponse(messageId);
  }

  return {
    shellRef,
    streamingMessageId,
    displayMessages,
    /** 是否贴底跟随；模板用 !stickToBottom 控制「回到最新」显隐 */
    stickToBottom,
    onListScroll,
    onLoadOlderMessages,
    jumpToBottom,
    canShowMessageActions,
    isMessagePatchPending,
    onSessionChange,
    onSessionRemove,
    onRegenerateAssistant,
  };
}
