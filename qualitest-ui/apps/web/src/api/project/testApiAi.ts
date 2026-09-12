/**
 * AI API 助手 HTTP 客户端。
 */
import request from '@/utils/request';

import type { AiPromptTemplateItem } from '@/api/project/testFlowAi';
import { consumeAuthenticatedSsePost } from '@/utils/ai/consumeSseStream';
import { AI_SCENE_TEST_API_DESIGN } from '@/api/ai/chat';

import type { ApiDesignResult } from '@/views/project/testProject/types/apiDesignAiTypes';

export type { AiPromptTemplateItem };

/** 流式设计请求体 */
export interface ApiDesignRequestPayload {
  testProjectId: string;
  testProjectApiId: string;
  aiLlmModelId: string;
  aiChatSessionId?: string | null;
  prompt: string;
  preRequestScript?: string;
  postRequestScript?: string;
  workbenchSnapshot?: string;
  thinkingEnabled?: boolean;
  /**
   * 是否开启全自动。
   * true：前端 SSE 结束后自动把 Diff 合并进工作台草稿。
   * false：须用户勾选 Diff 再点「应用到工作台」。
   * 均不自动保存接口库、不自动调试发送。
   */
  autopilotEnabled?: boolean;
}

/** SSE 事件类型 */
export type ApiDesignStreamEventType =
  | 'token'
  | 'thinking'
  | 'tool_start'
  | 'tool_end'
  | 'done'
  | 'error';

/** 单条 SSE 事件载荷 */
export interface ApiDesignStreamEvent {
  type: ApiDesignStreamEventType;
  text?: string;
  tool?: string;
  message?: string;
  result?: ApiDesignResult;
}

/** 流式消费回调 */
export interface ApiDesignStreamHandlers {
  onEvent?: (event: ApiDesignStreamEvent) => void;
  onToken?: (text: string) => void;
  onThinking?: (text: string) => void;
  onToolStart?: (tool: string) => void;
  onToolEnd?: (tool: string) => void;
  onDone?: (result: ApiDesignResult) => void;
  onError?: (message: string) => void;
}

const BASE_API = import.meta.env.VITE_APP_BASE_API as string;

/** 获取 AI API 助手输入区可用的提示词模板列表 */
export async function listApiDesignPromptTemplates(testProjectId: string) {
  const res = await request({
    url: '/project/testProjectApi/ai/promptTemplates',
    method: 'get',
    params: {
      testProjectId,
      sessionScene: AI_SCENE_TEST_API_DESIGN,
    },
  });
  return res.data ?? [];
}

/** 发起 SSE 流式 API 设计请求，解析 token/思考/工具事件直至 done */
export async function designApiStream(
  data: ApiDesignRequestPayload,
  handlers: ApiDesignStreamHandlers,
  signal?: AbortSignal,
): Promise<ApiDesignResult> {
  return consumeAuthenticatedSsePost<ApiDesignStreamEvent, ApiDesignResult>({
    url: `${BASE_API}/project/testProjectApi/ai/design/stream`,
    body: data,
    handlers,
    signal,
    defaultErrorMessage: 'AI API 助手请求失败',
    missingResultMessage: '未收到设计结果',
  });
}
