/**
 * AI API 助手 SSE 流封装：累积 token/思考、跟踪当前工具，并转发 session 事件。
 */
import {
  designApiStream,
  type ApiDesignRequestPayload,
  type ApiDesignStreamHandlers,
} from '@/api/project/testApiAi';
import { useAiAgentStream } from '@/composables/ai/useAiAgentStream';
import type { ApiDesignResult } from '../types/apiDesignAiTypes';

export function useApiAiStream() {
  const stream = useAiAgentStream<
    ApiDesignRequestPayload,
    ApiDesignResult,
    ApiDesignStreamHandlers
  >({
    designFn: designApiStream,
  });

  return {
    streamText: stream.streamText,
    streamThinking: stream.streamThinking,
    activeTool: stream.activeTool,
    runDesignStream: stream.runDesignStream,
    cancelStream: stream.cancelStream,
  };
}
