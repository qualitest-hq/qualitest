import { getToken } from '@/utils/auth';

export interface SseStreamHandlers<TEvent, TResult> {
  onEvent?: (event: TEvent) => void;
  onToken?: (text: string) => void;
  onThinking?: (text: string) => void;
  onToolStart?: (tool: string) => void;
  onToolEnd?: (tool: string) => void;
  /** 测试流全自动隐式落盘成功（SSE type=graphCommitted） */
  onGraphCommitted?: (testFlowId: string) => void;
  onDone?: (result: TResult) => void;
  onError?: (message: string) => void;
}

export interface ConsumeSsePostOptions<TEvent extends { type: string }, TResult> {
  url: string;
  body: unknown;
  handlers: SseStreamHandlers<TEvent, TResult>;
  signal?: AbortSignal;
  defaultErrorMessage?: string;
  missingResultMessage?: string;
}

function dispatchSseEvent<
  TEvent extends {
    type: string;
    text?: string;
    tool?: string;
    message?: string;
    testFlowId?: string;
    result?: TResult;
  },
  TResult,
>(
  event: TEvent,
  handlers: SseStreamHandlers<TEvent, TResult>,
  defaultErrorMessage: string,
): TResult | null {
  handlers.onEvent?.(event);
  if (event.type === 'token' && event.text) handlers.onToken?.(event.text);
  if (event.type === 'thinking' && event.text) handlers.onThinking?.(event.text);
  if (event.type === 'tool_start' && event.tool) handlers.onToolStart?.(event.tool);
  if (event.type === 'tool_end' && event.tool) handlers.onToolEnd?.(event.tool);
  // 全自动写库后：清 Staging 并 reload 画布
  if (event.type === 'graphCommitted' && event.testFlowId) {
    handlers.onGraphCommitted?.(event.testFlowId);
  }
  if (event.type === 'done' && event.result !== undefined) {
    handlers.onDone?.(event.result);
    return event.result;
  }
  if (event.type === 'error') {
    const msg = event.message ?? defaultErrorMessage;
    handlers.onError?.(msg);
    throw new Error(msg);
  }
  return null;
}

/**
 * 携带鉴权头发起 POST 请求并消费 SSE 流（data: JSON 行，空行分块）。
 */
export async function consumeAuthenticatedSsePost<
  TEvent extends { type: string },
  TResult,
>(options: ConsumeSsePostOptions<TEvent, TResult>): Promise<TResult> {
  const {
    url,
    body,
    handlers,
    signal,
    defaultErrorMessage = 'AI 助手请求失败',
    missingResultMessage = '未收到结果',
  } = options;

  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;

  const response = await fetch(url, {
    method: 'POST',
    headers,
    body: JSON.stringify(body),
    signal,
  });

  if (!response.ok) {
    const text = await response.text().catch(() => '');
    throw new Error(text || `请求失败 (${response.status})`);
  }
  if (!response.body) {
    throw new Error('流式响应无正文');
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  let finalResult: TResult | null = null;

  const parseChunk = (chunk: string) => {
    const lines = chunk.split('\n');
    for (const line of lines) {
      const trimmed = line.trim();
      if (!trimmed.startsWith('data:')) continue;
      const payload = trimmed.slice(5).trim();
      if (!payload) continue;
      try {
        const event = JSON.parse(payload) as TEvent;
        const doneResult = dispatchSseEvent(event, handlers, defaultErrorMessage);
        if (doneResult !== null) finalResult = doneResult;
      } catch (e) {
        // SyntaxError：半包/脏行，忽略；其余（含 type=error 业务失败）必须上抛
        if (e instanceof SyntaxError) {
          continue;
        }
        throw e;
      }
    }
  };

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const parts = buffer.split('\n\n');
    buffer = parts.pop() ?? '';
    for (const part of parts) parseChunk(part);
  }
  if (buffer.trim()) parseChunk(buffer);

  if (finalResult === null) throw new Error(missingResultMessage);
  return finalResult;
}
