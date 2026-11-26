/**
 * 测 flowValidationIssues：文案解析节点 id、结构/API 汇总。
 * 单跑：pnpm test flowValidationIssues --run
 */
import { describe, expect, it } from 'vitest'

import {
  collectIssueNodeIds,
  issuesFromApiHealthWarnings,
  issuesFromGraphValidation,
  resolveNodeIdsFromMessage,
} from '@/views/project/testFlow/utils/flowValidationIssues'
import { isAuthReadinessIssue, isAuthRiskMessage } from '@/views/project/testFlow/utils/runReadiness'

describe('flowValidationIssues', () => {
  const graph = {
    nodes: [
      { id: 'n1', type: 'http', name: '探活', data: {} },
      { id: 'n2', type: 'condition', name: '探活结果', data: {} },
      { id: 'n3', type: 'http', name: '登录', data: {} },
    ],
    edges: [] as Array<{ id: string; source: string; target: string }>,
  }

  it('resolveNodeIdsFromMessage：解析「名称」与开始节点', () => {
    expect(resolveNodeIdsFromMessage('HTTP 节点「探活」未绑定 testProjectApiId', graph)).toEqual([
      'n1',
    ])
    expect(
      resolveNodeIdsFromMessage('条件节点「探活结果」分支 if1 的 target 无对应出边：n3', graph),
    ).toEqual(['n2'])
    expect(
      resolveNodeIdsFromMessage('流程只能有一个开始节点，当前有 2 个：探活、登录', {
        nodes: graph.nodes,
        edges: [],
      }),
    ).toEqual(['n1', 'n2', 'n3'])
  })

  it('issuesFromGraphValidation：结构错误带 nodeIds', () => {
    const issues = issuesFromGraphValidation(
      {
        ok: false,
        errors: ['nodes[0] HTTP 节点「探活」缺少 callMode'],
      },
      graph,
    )
    expect(issues).toHaveLength(1)
    expect(issues[0].level).toBe('error')
    expect(issues[0].source).toBe('structure')
    expect(issues[0].nodeIds).toEqual(['n1'])
  })

  it('isAuthReadinessIssue：优先 code，文案前缀兜底', () => {
    expect(isAuthReadinessIssue({ code: 'RUN_READINESS_AUTH', message: 'x' })).toBe(true)
    expect(isAuthRiskMessage('AUTH_TOKEN_MISSING: 缺 token')).toBe(true)
    expect(isAuthReadinessIssue({ code: 'GRAPH_STRUCTURE', message: '边缺少 source' })).toBe(false)
  })

  it('issuesFromApiHealthWarnings 可带 code', () => {
    const issues = issuesFromApiHealthWarnings([
      { message: '绑定的接口不存在', nodeId: 'n1', code: 'API_MISSING' },
    ])
    expect(issues[0].code).toBe('API_MISSING')
  })

  it('issuesFromApiHealthWarnings + collectIssueNodeIds', () => {
    const issues = issuesFromApiHealthWarnings([
      { message: '绑定的接口不存在', nodeId: 'n1' },
      { message: '图级告警' },
    ])
    expect(issues[0].nodeIds).toEqual(['n1'])
    expect(issues[1].nodeIds).toEqual([])
    expect([...collectIssueNodeIds(issues)]).toEqual(['n1'])
  })
})
