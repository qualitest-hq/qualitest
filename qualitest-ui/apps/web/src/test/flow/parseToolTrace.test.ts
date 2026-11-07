/**
 * 测 parseToolTraceFromMeta / parseAssistantFromServer 对 toolTrace 的解析。
 * 边界：旧消息无字段、缺 name 的 call 跳过。
 * 单跑：pnpm test parseToolTrace
 */
import { describe, expect, it } from 'vitest';

import {
  parseAssistantFromServer,
  parseToolTraceFromMeta,
} from '@/views/project/testFlow/types/aiDesignTypes';
import { AI_INTERRUPTED_MESSAGE } from '@/utils/ai/toolTrace';

describe('parseToolTraceFromMeta', () => {
  it('解析完整 toolTrace，跳过无 name 的条目', () => {
    const trace = parseToolTraceFromMeta({
      toolTrace: {
        stepsUsed: 2,
        maxSteps: 8,
        truncated: false,
        calls: [
          { i: 1, name: 'submit_edges', ok: true, ms: 12, args: { edges: [{ label: 'else_cred' }] } },
          { i: 2, ok: false },
          { i: 3, name: 'search_apis', ok: false, result: { error: 'x' } },
        ],
      },
    });
    expect(trace?.stepsUsed).toBe(2);
    expect(trace?.calls).toHaveLength(2);
    expect(trace?.calls?.[0]?.name).toBe('submit_edges');
    // args 原样保留工具入参结构（submit_edges 为 { edges: [...] }）
    expect(trace?.calls?.[0]?.args).toEqual({ edges: [{ label: 'else_cred' }] });
    expect(trace?.calls?.[1]?.ok).toBe(false);
  });

  it('无 toolTrace 时返回 undefined', () => {
    expect(parseToolTraceFromMeta({})).toBeUndefined();
  });

  it('parseAssistantFromServer 挂上 toolTrace 与 interrupted', () => {
    const msg = parseAssistantFromServer({
      aiChatMessageId: '1',
      messageRole: 'assistant',
      messageContent: AI_INTERRUPTED_MESSAGE,
      resultMetaJson: JSON.stringify({
        summary: AI_INTERRUPTED_MESSAGE,
        explainOnly: true,
        interrupted: true,
        toolTrace: {
          stepsUsed: 1,
          maxSteps: 8,
          calls: [{ i: 1, name: 'get_graph_summary', ok: true, ms: 3 }],
        },
      }),
    });
    expect(msg.toolTrace?.calls?.[0]?.name).toBe('get_graph_summary');
    expect(msg.interrupted).toBe(true);
  });
});
