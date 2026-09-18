/**
 * 通用 AI 设计 SSE 流封装：累积 token/思考、跟踪当前工具、AbortController 取消。
 * 领域差异（API 函数、flow 专属事件）由 designFn / handlers 注入。
 */
import { ref, type Ref } from 'vue';

/** 流式过程中可转发的通用回调（含可选的画布/Run 事件） */
export interface AiAgentStreamHandlerHooks {
  onToken?: (text: string) => void;
  onThinking?: (text: string) => void;
  onToolStart?: (tool: string) => void;
  onToolEnd?: (tool?: string) => void;
  onSession?: (aiChatSessionId: string) => void;
  onGraphCommitted?: (testFlowId: string) => void;
  onRunStarted?: (runId: string) => void;
  onEvent?: (event: unknown) => void;
  onDone?: (result: unknown) => void;
  onError?: (message: string) => void;
}

/** 实际发起 SSE 请求的函数签名 */
export type AiAgentStreamDesignFn<TPayload, TResult, THandlers> = (
  payload: TPayload,
  handlers: THandlers,
  signal: AbortSignal,
) => Promise<TResult>;

export interface UseAiAgentStreamOptions<TPayload, TResult, THandlers extends AiAgentStreamHandlerHooks> {
  /** 发起领域流式请求 */
  designFn: AiAgentStreamDesignFn<TPayload, TResult, THandlers>;
}

export interface UseAiAgentStreamReturn<TPayload, TResult, THandlers> {
  streamText: Ref<string>;
  streamThinking: Ref<string>;
  activeTool: Ref<string>;
  abortController: Ref<AbortController | null>;
  runDesignStream: (
    payload: TPayload,
    handlers?: Partial<THandlers>,
  ) => Promise<TResult>;
  cancelStream: () => void;
}

/**
 * 创建可取消的 AI 设计流式会话状态。
 */
export function useAiAgentStream<
  TPayload,
  TResult,
  THandlers extends AiAgentStreamHandlerHooks,
>(
  options: UseAiAgentStreamOptions<TPayload, TResult, THandlers>,
): UseAiAgentStreamReturn<TPayload, TResult, THandlers> {
  const streamText = ref('');
  const streamThinking = ref('');
  const activeTool = ref('');
  const abortController = ref<AbortController | null>(null);

  /** 取消进行中的流式请求 */
  function cancelStream() {
    abortController.value?.abort();
    abortController.value = null;
    activeTool.value = '';
  }

  /**
   * 发起流式设计请求。
   * 返回 done 时的完整结果；取消时抛出 DOMException。
   */
  async function runDesignStream(
    payload: TPayload,
    handlers?: Partial<THandlers>,
  ): Promise<TResult> {
    cancelStream();
    streamText.value = '';
    streamThinking.value = '';
    activeTool.value = '';
    const controller = new AbortController();
    abortController.value = controller;

    try {
      const merged = {
        onToken: (text: string) => {
          streamText.value += text;
          handlers?.onToken?.(text);
        },
        onThinking: (text: string) => {
          streamThinking.value += text;
          handlers?.onThinking?.(text);
        },
        onToolStart: (tool: string) => {
          activeTool.value = tool;
          handlers?.onToolStart?.(tool);
        },
        onToolEnd: ((tool?: string) => {
          activeTool.value = '';
          // 领域 handlers 的 onToolEnd 签名略有差异，统一清空 activeTool 后转发
          (handlers?.onToolEnd as ((t?: string) => void) | undefined)?.(tool);
        }) as THandlers['onToolEnd'],
        onSession: (aiChatSessionId: string) => {
          handlers?.onSession?.(aiChatSessionId);
        },
        onGraphCommitted: (testFlowId: string) => {
          handlers?.onGraphCommitted?.(testFlowId);
        },
        onRunStarted: (runId: string) => {
          handlers?.onRunStarted?.(runId);
        },
        onEvent: handlers?.onEvent,
        onDone: handlers?.onDone as THandlers['onDone'],
        onError: handlers?.onError,
      } as THandlers;

      return await options.designFn(payload, merged, controller.signal);
    } finally {
      if (abortController.value === controller) {
        abortController.value = null;
      }
      activeTool.value = '';
    }
  }

  return {
    streamText,
    streamThinking,
    activeTool,
    abortController,
    runDesignStream,
    cancelStream,
  };
}
