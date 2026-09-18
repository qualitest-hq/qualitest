/**
 * 封装测试流 AI 设计 SSE 消费与 AbortController 取消。
 * 转发 session / token / 思考 / 工具起止 / 隐式落盘成功 / Run 已触发 / done / error。
 */
import {
  designTestFlowStream,
  type AiDesignStreamHandlers,
  type TestFlowDesignRequestPayload,
} from '@/api/project/testFlowAi';
import { useAiAgentStream } from '@/composables/ai/useAiAgentStream';
import type { TestFlowDesignResult } from '@/views/project/testFlow/types/aiDesignTypes';

export function useAiDesignStream() {
  const stream = useAiAgentStream<
    TestFlowDesignRequestPayload,
    TestFlowDesignResult,
    AiDesignStreamHandlers
  >({
    designFn: designTestFlowStream,
  });

  return {
    streamText: stream.streamText,
    streamThinking: stream.streamThinking,
    activeTool: stream.activeTool,
    abortController: stream.abortController,
    runDesignStream: stream.runDesignStream,
    cancelStream: stream.cancelStream,
  };
}
