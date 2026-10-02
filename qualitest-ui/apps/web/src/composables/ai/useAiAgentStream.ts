/**
 * 通用 AI 设计 SSE 流封装：累积 token/思考、跟踪当前工具、AbortController 取消。
 * 工具调用之后到达的正文自动另起一段，各步说明文字不会粘在一起。
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
  /** 全自动隐式写库成功：携带测试流 id 与新图版本号 */
  onGraphCommitted?: (testFlowId: string, graphRevision?: number) => void;
  /** 全自动已触发 Run：携带运行 id，供画布步骤高亮 */
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
    /** true：刚开始调用工具，下一段正文需另起一段 */
    let paragraphBreakPending = false;

    try {
      const merged = {
        onToken: (text: string) => {
          // 工具调用后的首个 token：已有正文末尾补成段落分隔
          if (paragraphBreakPending && streamText.value && !streamText.value.endsWith('\n\n')) {
            streamText.value = `${streamText.value.trimEnd()}\n\n`;
          }
          paragraphBreakPending = false;
          streamText.value += text;
          handlers?.onToken?.(text);
        },
        onThinking: (text: string) => {
          streamThinking.value += text;
          handlers?.onThinking?.(text);
        },
        onToolStart: (tool: string) => {
          activeTool.value = tool;
          // 标记下一段正文另起一段
          paragraphBreakPending = true;
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
        onGraphCommitted: (testFlowId: string, graphRevision?: number) => {
          handlers?.onGraphCommitted?.(testFlowId, graphRevision);
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
