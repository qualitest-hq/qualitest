/**
 * 测 flowValidationIssues：文案解析节点 id、图 error / 节点过滤。
 * 单跑：pnpm test flowValidationIssues --run
 */
import { describe, expect, it } from 'vitest'

import {
  collectIssueNodeIds,
  issuesForNode,
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
    // 前提：文案含节点名或开始节点语义
    // 期望：能解析到对应 nodeId
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

  it('issuesFromGraphValidation：图 error 带 nodeIds', () => {
    // 前提：结构校验失败文案含节点名
    // 期望：level=error 且能定位节点
    const issues = issuesFromGraphValidation(
      {
        ok: false,
        errors: ['nodes[0] HTTP 节点「探活」缺少 callMode'],
      },
      graph,
    )
    expect(issues).toHaveLength(1)
    expect(issues[0].level).toBe('error')
    expect(issues[0].nodeIds).toEqual(['n1'])
  })

  it('isAuthReadinessIssue：优先 code，文案前缀兜底', () => {
    // 前提：鉴权码 vs 结构码
    // 期望：仅鉴权类为 true
    expect(isAuthReadinessIssue({ code: 'RUN_READINESS_AUTH', message: 'x' })).toBe(true)
    expect(isAuthReadinessIssue({ code: 'RUN_READINESS_HTTP_PARAM', message: '缺参' })).toBe(false)
    expect(isAuthRiskMessage('AUTH_LOGIN_FLOWKEY_COLLISION: 碰撞')).toBe(true)
    expect(isAuthReadinessIssue({ code: 'GRAPH_STRUCTURE', message: '边缺少 source' })).toBe(false)
  })

  it('issuesForNode + collectIssueNodeIds', () => {
    // 前提：两条问题，一条挂 n1
    // 期望：按节点过滤与汇总正确
    const issues = [
      { level: 'warning' as const, message: '绑定的接口不存在', nodeIds: ['n1'], code: 'API_MISSING' },
      { level: 'error' as const, message: '图级告警', nodeIds: [] as string[] },
    ]
    expect(issuesForNode(issues, 'n1')).toHaveLength(1)
    expect(issuesForNode(issues, 'n1')[0].code).toBe('API_MISSING')
    expect([...collectIssueNodeIds(issues)]).toEqual(['n1'])
  })

  it('collectIssueNodeIds 可按 level 过滤', () => {
    // 前提：同节点既有 ERROR 又有 WARNING，另有仅 WARNING 节点
    // 期望：按 level 分别汇总
    const issues = [
      { level: 'error' as const, message: '缺接口', nodeIds: ['n1'], code: 'API_MISSING' },
      { level: 'warning' as const, message: '孤儿参', nodeIds: ['n1'], code: 'ORPHAN_PARAM' },
      { level: 'warning' as const, message: '抽取路径', nodeIds: ['n2'], code: 'EXTRACT_PATH_MISSING' },
    ]
    expect([...collectIssueNodeIds(issues, 'error')].sort()).toEqual(['n1'])
    expect([...collectIssueNodeIds(issues, 'warning')].sort()).toEqual(['n1', 'n2'])
  })

  it('isAuthRiskMessage 只认登录键碰撞前缀', () => {
    // 前提：认票硬拦已移除；现认 AUTH_LOGIN_FLOWKEY_COLLISION
    expect(isAuthRiskMessage('AUTH_TOKEN_MISSING: 缺 token')).toBe(false)
    expect(isAuthRiskMessage('AUTH_LOGIN_FLOWKEY_COLLISION: 碰撞')).toBe(true)
    expect(isAuthRiskMessage('LOGIN_EXTRACT_MISSING: 旧前缀')).toBe(false)
    expect(isAuthRiskMessage('HTTP_REQUIRED_PARAM_MISSING: 旧前缀')).toBe(false)
  })
})
