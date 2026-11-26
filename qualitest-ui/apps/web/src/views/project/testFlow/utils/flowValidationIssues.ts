/**
 * 画布校验条用的结构化问题。
 * 每条含：严重级别、文案、可定位节点 id、可选服务端 CODE、来源分类
 *（图 error / API 语义）。
 */
import type { GraphJson, GraphNode } from '@/utils/flow/graphTypes'
import type { GraphValidationResult } from '@/utils/flow/graphValidate'
import { findStartNodeIds } from '@/utils/flow/graphValidate'

/** 问题来源：画布图 error（含结构与鉴权/必填）/ API 健康探测 */
export type FlowCanvasValidationIssueSource = 'structure' | 'apiHealth'

/** 单条画布校验问题；warning 仅 API 健康，其余来源一律 error */
export type FlowCanvasValidationIssue = {
  level: 'error' | 'warning'
  message: string
  /** 可点击定位的节点 id；边类问题可挂两端节点 */
  nodeIds: string[]
  source: FlowCanvasValidationIssueSource
  /** 服务端 FlowIssue.code（如 RUN_READINESS_AUTH）；本地地板可无 */
  code?: string
}

/** 节点展示名：优先壳字段 name，否则用 id */
export function nodeDisplayName(node: GraphNode | undefined, fallbackId: string): string {
  const name = node?.name
  if (name != null && String(name).trim()) return String(name).trim()
  return fallbackId
}

/**
 * 从校验文案里解析关联节点 id。
 * 「开始节点」类文案取拓扑起点；「名称」取同名节点；也能直接匹配节点 id。
 */
export function resolveNodeIdsFromMessage(
  message: string,
  graph: Pick<GraphJson, 'nodes' | 'edges'>,
): string[] {
  const text = String(message ?? '')
  const nodes = graph.nodes ?? []

  if (/开始节点/.test(text)) {
    return findStartNodeIds(graph)
  }

  const ids: string[] = []
  for (const m of text.matchAll(/「([^」]+)」/g)) {
    const token = m[1]?.trim()
    if (!token) continue
    const byName = nodes.find((n) => nodeDisplayName(n, n.id) === token)
    if (byName?.id) {
      ids.push(byName.id)
      continue
    }
    const byId = nodes.find((n) => n.id === token)
    if (byId?.id) ids.push(byId.id)
  }

  return [...new Set(ids)]
}

/** 把图结构校验的 errors 转成带节点定位的问题列表 */
export function issuesFromGraphValidation(
  result: GraphValidationResult,
  graph: Pick<GraphJson, 'nodes' | 'edges'>,
): FlowCanvasValidationIssue[] {
  return (result.errors ?? []).map((message) => ({
    level: 'error' as const,
    message,
    nodeIds: resolveNodeIdsFromMessage(message, graph),
    source: 'structure' as const,
  }))
}

/** API 健康探测告警转问题列表（有 nodeId 则可定位） */
export function issuesFromApiHealthWarnings(
  warnings: Array<{ message?: string; detail?: string; code?: string; nodeId?: string | number | null }>,
): FlowCanvasValidationIssue[] {
  return warnings
    .map((w) => {
      const message = w.message || w.detail || w.code || ''
      if (!message) return null
      const nodeId = w.nodeId != null && String(w.nodeId).trim() ? String(w.nodeId) : ''
      return {
        level: 'warning' as const,
        message,
        nodeIds: nodeId ? [nodeId] : [],
        source: 'apiHealth' as const,
        code: w.code != null && String(w.code).trim() ? String(w.code).trim() : undefined,
      }
    })
    .filter((x): x is FlowCanvasValidationIssue => x != null)
}

/** 汇总所有问题涉及的节点 id，供卡片描边高亮 */
export function collectIssueNodeIds(issues: FlowCanvasValidationIssue[]): Set<string> {
  const ids = new Set<string>()
  for (const issue of issues) {
    for (const id of issue.nodeIds) {
      if (id) ids.add(id)
    }
  }
  return ids
}
