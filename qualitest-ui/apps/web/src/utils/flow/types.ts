/**
 * 测试流运行时上下文与 HTTP 快照类型。
 * 用于占位符解析、断言/条件求值、提取写入。
 */
export interface HttpResponseSnapshot {
  status?: number;
  headers?: Record<string, unknown>;
  body?: unknown;
  /** 响应原文；XML 已转对象时仍保留原报文 */
  bodyText?: string;
  /** 该步 HTTP 请求耗时（毫秒） */
  durationMs?: number;
  /** 实际响应形态：json / xml / nonJson */
  responseKind?: string;
  /** 实际形态是否符合接口配置的期望形态 */
  expectedMatch?: boolean;
}

/** 单次测试流 Run 的运行时上下文 */
export interface FlowRunContext {
  env: Record<string, unknown>;
  flow: Record<string, unknown>;
  asset: Record<string, unknown>;
  /** 本上下文内最近一次 HTTP 响应快照 */
  lastResponse?: HttpResponseSnapshot | null;
  /** 按画布节点 id 索引的 HTTP 快照；同节点多次执行时覆盖为最后一次 */
  nodeResponses?: Record<string, HttpResponseSnapshot>;
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
