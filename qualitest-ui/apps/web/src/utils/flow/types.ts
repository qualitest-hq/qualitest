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
  /** 环境变量，占位符 env. 读取 */
  env: Record<string, unknown>;
  /** 流变量，占位符 flow. 读取 */
  flow: Record<string, unknown>;
  /** 资产数据，占位符 asset. 读取 */
  asset: Record<string, unknown>;
  /** 本次运行的入参集合，占位符 in. 读取 */
  in?: Record<string, unknown>;
  /** 本次运行的出参集合（状态码 / 响应头 / 响应体等），占位符 out. 读取 */
  out?: Record<string, unknown>;
  /** 本次运行所用入口的种类，如 scenario / http / subflow，占位符 run.entryKind 读取 */
  entryKind?: string;
  /** 本次运行进入的入口节点 id，占位符 run.entryNodeId 读取 */
  entryNodeId?: string;
  /** 触发来源短名：ui / mcp / suite / http / subflow，占位符 run.trigger 读取 */
  trigger?: string;
  /** 本上下文内最近一次 HTTP 响应快照 */
  lastResponse?: HttpResponseSnapshot | null;
  /** 按画布节点 id 索引的 HTTP 快照；同节点多次执行时覆盖为最后一次 */
  nodeResponses?: Record<string, HttpResponseSnapshot>;
  /** 按画布节点 id 索引的定稿出参（如子流挂载的 out）；占位符 nodes.<id>.out 读取 */
  nodeOuts?: Record<string, Record<string, unknown>>;
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
