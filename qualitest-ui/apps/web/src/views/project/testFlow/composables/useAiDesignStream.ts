/**
 * 封装测试流 AI 设计 SSE 消费与 AbortController 取消。
 */
import { ref } from 'vue';

import {
  designTestFlowStream,
  type AiDesignStreamHandlers,
  type TestFlowDesignRequestPayload,
} from '@/api/project/testFlowAi';
import type { TestFlowDesignResult } from '@/views/project/testFlow/types/aiDesignTypes';

export function useAiDesignStream() {
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
    payload: TestFlowDesignRequestPayload,
    handlers?: Partial<AiDesignStreamHandlers>,
  ): Promise<TestFlowDesignResult> {
    cancelStream();
    streamText.value = '';
    streamThinking.value = '';
    activeTool.value = '';
    const controller = new AbortController();
    abortController.value = controller;

    try {
      return await designTestFlowStream(
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
          onEvent: handlers?.onEvent,
          onDone: handlers?.onDone,
          onError: handlers?.onError,
        },
        controller.signal,
      );
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
