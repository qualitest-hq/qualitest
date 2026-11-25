import { describe, expect, it } from 'vitest'

import {
  buildBatchFlowTree,
  buildBatchQueueFromChecks,
  defaultCheckedKeysForTree,
  findScenarioCheckKey,
  flowOnlyCheckKey,
  parseFlowScenariosFromGraphJson,
  scenarioCheckKey,
} from '@/views/project/testFlow/utils/batchRunScenarios'

describe('batchRunScenarios', () => {
  it('从 graphJson 解析场景并标记活动场景', () => {
    // 前提：scenario-entry 含两场景且指定 activeScenarioId
    // 期望：两条场景，活动标记正确，带 entryNodeId
    const scenarios = parseFlowScenariosFromGraphJson(
      JSON.stringify({
        nodes: [
          {
            id: 'entry',
            type: 'scenario-entry',
            data: {
              scenarios: [
                { id: 'sc-1', name: '正常' },
                { id: 'sc-2', name: '异常' },
              ],
            },
          },
        ],
      }),
      'sc-2',
    )
    expect(scenarios).toHaveLength(2)
    expect(scenarios[0].entryNodeId).toBe('entry')
    expect(scenarios[0].active).toBe(false)
    expect(scenarios[1].active).toBe(true)
    expect(scenarios[1].scenarioName).toBe('异常')
  })

  it('多 scenario-entry 保留各入口归属而非扁平丢入口', () => {
    // 前提：两入口各一场景
    // 期望：两条均带各自 entryNodeId
    const scenarios = parseFlowScenariosFromGraphJson(
      JSON.stringify({
        nodes: [
          {
            id: 'e1',
            type: 'scenario-entry',
            data: { scenarios: [{ id: 'sc-a', name: 'A' }] },
          },
          {
            id: 'e2',
            type: 'scenario-entry',
            data: { scenarios: [{ id: 'sc-b', name: 'B' }] },
          },
        ],
      }),
    )
    expect(scenarios.map((s) => ({ id: s.scenarioId, entry: s.entryNodeId }))).toEqual([
      { id: 'sc-a', entry: 'e1' },
      { id: 'sc-b', entry: 'e2' },
    ])
  })

  it('无场景流默认勾选流本身并生成队列项', () => {
    // 前提：选中一行无 scenarios
    // 期望：defaultChecked 为 flowId；队列一项、不传 runScenarioId
    const tree = buildBatchFlowTree([
      { testFlowId: '10', flowName: '空流', graphJson: JSON.stringify({ meta: {} }) },
    ])
    const keys = defaultCheckedKeysForTree(tree)
    expect(keys).toEqual([flowOnlyCheckKey('10')])
    const queue = buildBatchQueueFromChecks(tree, keys)
    expect(queue).toHaveLength(1)
    expect(queue[0].runScenarioId).toBeUndefined()
    expect(queue[0].entryNodeId).toBeUndefined()
    expect(queue[0].scenarioName).toBe('默认场景')
  })

  it('勾父流全场景时队列保序且透传 entryNodeId', () => {
    // 前提：一流两入口各一场景全勾
    // 期望：队列两条，含 entryNodeId / scenarioId
    const tree = buildBatchFlowTree([
      {
        testFlowId: '7',
        flowName: '登录',
        graphJson: JSON.stringify({
          nodes: [
            {
              id: 'e1',
              type: 'scenario-entry',
              data: { scenarios: [{ id: 'sc-a', name: 'A' }] },
            },
            {
              id: 'e2',
              type: 'scenario-entry',
              data: { scenarios: [{ id: 'sc-b', name: 'B' }] },
            },
          ],
        }),
      },
    ])
    const keys = [
      scenarioCheckKey('7', 'e1', 'sc-a'),
      scenarioCheckKey('7', 'e2', 'sc-b'),
    ]
    const queue = buildBatchQueueFromChecks(tree, keys)
    expect(queue.map((q) => q.runScenarioId)).toEqual(['sc-a', 'sc-b'])
    expect(queue.map((q) => q.entryNodeId)).toEqual(['e1', 'e2'])
  })

  it('findScenarioCheckKey 可按场景找回含入口的勾选键', () => {
    // 前提：树含 e2/sc-b；旧套件项仅有 scenarioId
    // 期望：找回 7::e2::sc-b
    const tree = buildBatchFlowTree([
      {
        testFlowId: '7',
        flowName: '登录',
        graphJson: JSON.stringify({
          nodes: [
            {
              id: 'e2',
              type: 'scenario-entry',
              data: { scenarios: [{ id: 'sc-b', name: 'B' }] },
            },
          ],
        }),
      },
    ])
    expect(findScenarioCheckKey(tree, '7', 'sc-b')).toBe(scenarioCheckKey('7', 'e2', 'sc-b'))
  })
})
