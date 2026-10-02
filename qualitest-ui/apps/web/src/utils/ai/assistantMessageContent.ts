/**
 * AI 助手气泡的字段解析：正文、思考过程、过程旁白、失败时的显示内容。
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

/** 读取过程旁白：去掉首尾空白；非字符串或空白时返回 undefined。 */
export function normalizeProcessNarration(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim() ? value.trim() : undefined;
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
