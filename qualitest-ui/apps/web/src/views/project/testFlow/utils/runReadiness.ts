/**
 * 运行前就绪检查。
 *
 * 汇总三类问题：完整图结构错误、断言/条件路径结构错误、鉴权与 HTTP 必填预检错误。
 * 保存图时不跑本检查；点「运行」前用本结果决定是否允许开跑。
 */
import { savePrecheckFlowDesign } from '@/api/project/testFlowAi'
import { validateGraphJson } from '@/utils/flow/graphValidate'

import { resolveCanvasApiDetail } from './resolveCanvasApiDetail'
import {
  collectAssertPathDesignIssues,
  extractResponseSchemaPaths,
  resolveTrialApiId,
} from './jsonPathTrial'

/**
 * 按图上 assert/condition 节点，拉取上游接口响应 schema，检查路径是否合法。
 * 返回 errors（结构硬错）与 warnings（schema 缺字段等软提示）。
 */
export async function loadAssertPathDesignIssues(graph: {
  nodes?: Array<Record<string, unknown>>
  edges?: Array<Record<string, unknown>>
}): Promise<{ errors: string[]; warnings: string[] }> {
  const nodes = graph.nodes ?? []
  const edges = graph.edges ?? []
  const apiIds = new Set<string>()
  for (const node of nodes) {
    const type = String(node.type ?? '').trim().toLowerCase()
    if (type !== 'assert' && type !== 'condition') continue
    const apiId = resolveTrialApiId(
      node as { id?: string; type?: string; data?: Record<string, unknown> },
      nodes as never,
      edges as never,
    )
    if (apiId) apiIds.add(apiId)
  }
  const schemaPathsByApiId = new Map<string, string[]>()
  await Promise.all(
    [...apiIds].map(async (apiId) => {
      const detail = await resolveCanvasApiDetail(apiId)
      schemaPathsByApiId.set(apiId, extractResponseSchemaPaths(detail?.responseConfig))
    }),
  )
  return collectAssertPathDesignIssues(graph, schemaPathsByApiId)
}

/**
 * 请求后端运行风险预检：鉴权凭证、登录抽取、HTTP 必填。
 * 不写库；失败时把异常信息收成单条错误返回。
 */
export async function fetchSavePrecheckErrors(
  testProjectId: string,
  graph: unknown,
): Promise<string[]> {
  const projectId = String(testProjectId || '').trim()
  if (!projectId) return []
  try {
    const precheck = await savePrecheckFlowDesign({
      testProjectId: projectId,
      graphJson: typeof graph === 'string' ? graph : JSON.stringify(graph),
    })
    return Array.isArray(precheck?.errors) ? precheck.errors : []
  } catch (e: unknown) {
    return [e instanceof Error ? e.message : '运行前预检失败']
  }
}

/** 运行就绪检查结果 */
export type RunBlockingResult = {
  /** 全部阻断文案（结构 + 断言路径 + 鉴权/必填） */
  errors: string[]
  /** 仅鉴权/登录抽取/HTTP 必填子集，用于校验条「运行风险」段，避免与结构段重复 */
  precheckErrors: string[]
}

/**
 * 收集阻止开跑的错误。
 * 顺序：完整图结构 → 断言路径 → 鉴权与必填预检。
 */
export async function collectRunBlockingErrors(opts: {
  graph: unknown
  testProjectId?: string | null
}): Promise<RunBlockingResult> {
  const errors: string[] = []
  const validation = validateGraphJson(opts.graph)
  errors.push(...(validation.errors ?? []))

  const graphObj = opts.graph && typeof opts.graph === 'object' && !Array.isArray(opts.graph)
    ? (opts.graph as { nodes?: Array<Record<string, unknown>>; edges?: Array<Record<string, unknown>> })
    : { nodes: [], edges: [] }
  const assertPath = await loadAssertPathDesignIssues(graphObj)
  errors.push(...(assertPath.errors ?? []))

  const projectId = opts.testProjectId != null ? String(opts.testProjectId).trim() : ''
  const precheckErrors = projectId ? await fetchSavePrecheckErrors(projectId, opts.graph) : []
  errors.push(...precheckErrors)
  return { errors, precheckErrors }
}
