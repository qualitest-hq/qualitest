/**
 * 测 flowValidationIssues：文案解析节点 id、Staging/API 汇总。
 * 单跑：pnpm test flowValidationIssues --run
 */
import { describe, expect, it } from 'vitest'

import {
  collectIssueNodeIds,
  issuesFromApiHealthWarnings,
  issuesFromGraphValidation,
  issuesFromStagingConfirmFailures,
  resolveNodeIdsFromMessage,
} from '@/views/project/testFlow/utils/flowValidationIssues'

describe('flowValidationIssues', () => {
  const graph = {
    nodes: [
      { id: 'n1', type: 'http', data: { name: '探活' } },
      { id: 'n2', type: 'condition', data: { name: '探活结果' } },
      { id: 'n3', type: 'http', data: { name: '登录' } },
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
        warnings: ['条件节点「探活结果」分支 if1 的 target 无对应出边：n3'],
      },
      graph,
    )
    expect(issues).toHaveLength(2)
    expect(issues[0].nodeIds).toEqual(['n1'])
    expect(issues[1].level).toBe('warning')
    expect(issues[1].nodeIds).toEqual(['n2'])
  })

  it('issuesFromStagingConfirmFailures：挂到节点', () => {
    const issues = issuesFromStagingConfirmFailures(
      [
        {
          unitId: 'addNode:n2',
          kind: 'addNode',
          messageId: 'm1',
          status: 'pending',
          lastValidation: { ok: false, errors: ['确认失败：缺依赖'], warnings: [] },
        } as never,
      ],
      [],
    )
    expect(issues).toEqual([
      {
        level: 'error',
        message: '确认失败：缺依赖',
        nodeIds: ['n2'],
        source: 'staging',
      },
    ])
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
