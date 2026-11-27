/**
 * 画布校验条用的结构化问题。
 * 每条含：严重级别、文案、可定位节点 id、可选服务端 CODE。
 * 统一来自 validate?stage=canvas（含 API 语义与插件 platform 检查），不再按 source 分桶。
 */
import type { GraphJson, GraphNode } from '@/utils/flow/graphTypes'
import type { GraphValidationResult } from '@/utils/flow/graphValidate'
import { findStartNodeIds } from '@/utils/flow/graphValidate'

/** 单条画布校验问题 */
export type FlowCanvasValidationIssue = {
  level: 'error' | 'warning'
  message: string
  /** 可点击定位的节点 id；边类问题可挂两端节点 */
  nodeIds: string[]
  /** 服务端 FlowIssue.code；本地地板可无 */
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

/** 把本地地板 errors 转成带节点定位的问题列表 */
export function issuesFromGraphValidation(
  result: GraphValidationResult,
  graph: Pick<GraphJson, 'nodes' | 'edges'>,
): FlowCanvasValidationIssue[] {
  return (result.errors ?? []).map((message) => ({
    level: 'error' as const,
    message,
    nodeIds: resolveNodeIdsFromMessage(message, graph),
  }))
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

/** 按节点过滤问题（属性面板内联） */
export function issuesForNode(
  issues: FlowCanvasValidationIssue[],
  nodeId: string | undefined | null,
): FlowCanvasValidationIssue[] {
  if (!nodeId) return []
  const id = String(nodeId)
  return issues.filter((i) => (i.nodeIds || []).includes(id))
}
