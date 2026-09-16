/**
 * AI API 助手 SSE 流封装：累积 token/思考、跟踪当前工具，并转发 session 事件。
 */
import { ref } from 'vue';

import {
  designApiStream,
  type ApiDesignRequestPayload,
  type ApiDesignStreamHandlers,
} from '@/api/project/testApiAi';
import type { ApiDesignResult } from '../types/apiDesignAiTypes';

export function useApiAiStream() {
  const streamText = ref('');
  const streamThinking = ref('');
  const activeTool = ref('');
  let abortController: AbortController | null = null;

  /** 中止进行中的 SSE 请求并清空工具状态。 */
  function cancelStream() {
    abortController?.abort();
    abortController = null;
    activeTool.value = '';
  }

  /**
   * 发起流式设计请求，累积 token/思考文本并跟踪当前工具名。
   * @returns 服务端 done 事件中的完整 ApiDesignResult
   */
  async function runDesignStream(
    payload: ApiDesignRequestPayload,
    handlers?: Partial<ApiDesignStreamHandlers>,
  ): Promise<ApiDesignResult> {
    cancelStream();
    streamText.value = '';
    streamThinking.value = '';
    const controller = new AbortController();
    abortController = controller;

    try {
      return await designApiStream(
        payload,
        {
          onToken: (text) => {
            streamText.value += text;
            handlers?.onToken?.(text);
          },
          onThinking: (text) => {
            streamThinking.value += text;
            handlers?.onThinking?.(text);
          },
          onToolStart: (tool) => {
            activeTool.value = tool;
            handlers?.onToolStart?.(tool);
          },
          onToolEnd: () => {
            activeTool.value = '';
            handlers?.onToolEnd?.();
          },
          // 会话已就绪：尽早记下 sessionId，取消后可重拉半成品
          onSession: (aiChatSessionId) => {
            handlers?.onSession?.(aiChatSessionId);
          },
          onEvent: handlers?.onEvent,
          onDone: handlers?.onDone,
          onError: handlers?.onError,
        },
        controller.signal,
      );
    } finally {
      if (abortController === controller) abortController = null;
      activeTool.value = '';
    }
  }

  return {
    streamText,
    streamThinking,
    activeTool,
    runDesignStream,
    cancelStream,
  };
}
