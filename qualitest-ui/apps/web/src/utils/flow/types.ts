/**
 * 测试流运行时上下文与 HTTP 快照类型。
 * FlowRunContext 供占位符解析、断言/条件求值、提取写入使用。
 */
export interface HttpResponseSnapshot {
  status?: number;
  headers?: Record<string, unknown>;
  body?: unknown;
  /** 该步 HTTP 请求耗时（毫秒） */
  durationMs?: number;
}

/** 单次测试流 Run 的运行时上下文：env、flow、asset 与上一步 HTTP 快照 */
export interface FlowRunContext {
  env: Record<string, unknown>;
  flow: Record<string, unknown>;
  asset: Record<string, unknown>;
  /** 最近一步 HTTP 响应快照（含 durationMs） */
  lastResponse?: HttpResponseSnapshot | null;
}

export type PlaceholderResolveMode = 'lenient' | 'strict';

export class PlaceholderUndefinedError extends Error {
  readonly code = 'TF_PLACEHOLDER_UNDEFINED';
  readonly placeholder: string;

  constructor(placeholder: string) {
    super(`占位符未定义: {{${placeholder}}}`);
    this.name = 'PlaceholderUndefinedError';
    this.placeholder = placeholder;
  }
}
