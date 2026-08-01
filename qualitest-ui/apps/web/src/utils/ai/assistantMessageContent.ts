/**
 * AI 助手气泡正文与失败字段的共用解析（测试流 / API 设计两侧一致）。
 */

/** 组装助手正文：summary → 流式正文 → 有 patch 时的占位。 */
export function resolveAssistantContent(options: {
  summary?: string | null;
  streamText?: string | null;
  hasPatch: boolean;
  emptyPatchPlaceholder: string;
}): string {
  const summary = (options.summary ?? '').trim() || (options.streamText ?? '').trim();
  if (summary) {
    return summary;
  }
  return options.hasPatch ? options.emptyPatchPlaceholder : '';
}

/** 合并服务端与流式思考内容。 */
export function resolveThinkingContent(
  serverThinking?: string | null,
  streamThinking?: string | null,
): string | undefined {
  return (serverThinking ?? '').trim() || (streamThinking ?? '').trim() || undefined;
}

/** 设计失败时的助手气泡字段（保留已流式输出的正文与思考）。 */
export function resolveFailedAssistantFields(options: {
  errorMessage: string;
  streamText?: string | null;
  streamThinking?: string | null;
}): { content: string; thinkingContent?: string } {
  const streamed = (options.streamText ?? '').trim();
  return {
    content: streamed || options.errorMessage,
    thinkingContent: resolveThinkingContent(undefined, options.streamThinking),
  };
}
