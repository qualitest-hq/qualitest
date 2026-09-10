/**
 * 画布校验条用的结构化问题：文案、严重级别、可定位节点 id、来源分类。
 * 来源含图结构校验、Staging 确认失败、API 语义告警。
 */
import type { GraphJson, GraphNode } from '@/utils/flow/graphTypes'
import type { GraphValidationResult } from '@/utils/flow/graphValidate'
import { findStartNodeIds } from '@/utils/flow/graphValidate'

import type { AiStagingUnit } from '../types/aiStagingTypes'
import { objectIdFromUnitId } from './stagingUnitIds'

/** 问题来源：结构校验 / Staging 确认 / API 健康探测 */
export type FlowCanvasValidationIssueSource = 'structure' | 'staging' | 'apiHealth'

/** 单条画布校验问题 */
export type FlowCanvasValidationIssue = {
  level: 'error' | 'warning'
  message: string
  /** 可点击定位的节点 id；边类问题可挂两端节点 */
  nodeIds: string[]
  source: FlowCanvasValidationIssueSource
}

/** 节点展示名：优先 data.name，否则用 id */
export function nodeDisplayName(node: GraphNode | undefined, fallbackId: string): string {
  const name = node?.data?.name
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

/** 把图结构校验的 errors/warnings 转成带节点定位的问题列表 */
export function issuesFromGraphValidation(
  result: GraphValidationResult,
  graph: Pick<GraphJson, 'nodes' | 'edges'>,
): FlowCanvasValidationIssue[] {
  const map = (level: 'error' | 'warning', message: string): FlowCanvasValidationIssue => ({
    level,
    message,
    nodeIds: resolveNodeIdsFromMessage(message, graph),
    source: 'structure',
  })
  return [
    ...(result.errors ?? []).map((m) => map('error', m)),
    ...(result.warnings ?? []).map((m) => map('warning', m)),
  ]
}

/**
 * Staging 确认失败转问题列表。
 * 节点类单元挂到对应节点；边类单元挂到边的两端节点。
 */
export function issuesFromStagingConfirmFailures(
  units: Iterable<AiStagingUnit>,
  edges: Array<{ id?: string; source?: string; target?: string }>,
): FlowCanvasValidationIssue[] {
  const out: FlowCanvasValidationIssue[] = []
  for (const unit of units) {
    if (!unit?.lastValidation || unit.lastValidation.ok) continue
    const objectId = objectIdFromUnitId(unit.unitId)
    let nodeIds: string[] = []
    if (unit.kind === 'addEdge' || unit.kind === 'updateEdge' || unit.kind === 'deleteEdge') {
      const edge = edges.find((e) => e.id === objectId)
      if (edge?.source) nodeIds.push(edge.source)
      if (edge?.target) nodeIds.push(edge.target)
    } else if (
      unit.kind === 'addNode'
      || unit.kind === 'updateNode'
      || unit.kind === 'deleteNode'
    ) {
      if (objectId) nodeIds = [objectId]
    }
    nodeIds = [...new Set(nodeIds.filter(Boolean))]
    for (const message of unit.lastValidation.errors ?? []) {
      out.push({
        level: 'error',
        message,
        nodeIds,
        source: 'staging',
      })
    }
    for (const message of unit.lastValidation.warnings ?? []) {
      out.push({
        level: 'warning',
        message,
        nodeIds,
        source: 'staging',
      })
    }
  }
  return out
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
