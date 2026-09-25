/**
 * 接口配置相关类型：请求/响应结构、资产测值、节点测值覆盖。
 * 资产测值与节点覆盖字段名可能相同，类型分开，避免互相赋值。
 */

/** 接口资产测值：参数默认值、请求体示例、按响应 id 存的示例。存在 testValueConfig。 */
export interface ApiTestValueConfig {
  request?: {
    /** 按参数名的调试默认值 */
    paramDefaults?: Record<string, unknown>
    /** JSON 请求体调试示例 */
    bodyExample?: unknown
    /** 结构里已删除但仍保留默认值记录的参数名 */
    removedParams?: string[]
  }
  response?: {
    /** 按响应条目 id 存的示例 */
    examplesById?: Record<string, unknown>
    /** 已删除响应条目的示例归档 */
    archivedExamples?: Record<string, unknown>
  }
}

/**
 * 流程 HTTP 节点测值覆盖：相对接口资产默认值的差分。
 * 存在节点 data.requestValueOverrides，不进接口资产表。
 */
export interface NodeRequestValueOverrides {
  paramDefaults?: Record<string, unknown>
  bodyExample?: unknown
}

/** 请求结构：method、参数定义、声明头、body；不含调试 value / example。 */
export interface ApiRequestStructure {
  configVersion?: number
  method?: string
  queryParams?: unknown[]
  pathParams?: unknown[]
  /** 声明头（契约定义，不是实际发出的调试头） */
  declaredHeaders?: unknown[]
  body?: Record<string, unknown>
}

/** 响应结构：期望响应形态 + 条目 id、状态码、schema 等；示例正文放在测值里。 */
export interface ApiResponseStructure {
  configVersion?: number
  /** 期望响应形态：json / html / any；跑流探活对照实际响应 */
  expectedResponseKind?: 'json' | 'html' | 'any'
  responses?: Array<{
    id?: string
    name?: string
    httpStatus?: number
    contentType?: string
    schema?: unknown
    /** 保存前可能暂存；落库时应拆到测值 examplesById */
    example?: unknown
  }>
}
