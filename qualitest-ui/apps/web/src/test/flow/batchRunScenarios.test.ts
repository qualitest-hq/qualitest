import { describe, expect, it } from 'vitest'

import {
  buildBatchFlowTree,
  buildBatchQueueFromChecks,
  defaultCheckedKeysForTree,
  flowOnlyCheckKey,
  parseFlowScenariosFromGraphJson,
  scenarioCheckKey,
} from '@/views/project/testFlow/utils/batchRunScenarios'

describe('batchRunScenarios', () => {
  it('从 graphJson 解析场景并标记活动场景', () => {
    // 前提：meta 含两场景且指定 activeScenarioId
    // 期望：两条场景，活动标记正确
    const scenarios = parseFlowScenariosFromGraphJson(
      JSON.stringify({
        meta: {
          activeScenarioId: 'sc-2',
          scenarios: [
            { id: 'sc-1', name: '正常' },
            { id: 'sc-2', name: '异常' },
          ],
        },
      }),
    )
    expect(scenarios).toHaveLength(2)
    expect(scenarios[0].active).toBe(false)
    expect(scenarios[1].active).toBe(true)
    expect(scenarios[1].scenarioName).toBe('异常')
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
    expect(queue[0].scenarioName).toBe('默认场景')
  })

  it('勾父流全场景时队列保序含同流多场景', () => {
    // 前提：一流两场景全勾
    // 期望：队列两条，scenarioId 依次为 sc-a / sc-b
    const tree = buildBatchFlowTree([
      {
        testFlowId: '7',
        flowName: '登录',
        graphJson: JSON.stringify({
          meta: {
            scenarios: [
              { id: 'sc-a', name: 'A' },
              { id: 'sc-b', name: 'B' },
            ],
          },
        }),
      },
    ])
    const keys = [
      scenarioCheckKey('7', 'sc-a'),
      scenarioCheckKey('7', 'sc-b'),
    ]
    const queue = buildBatchQueueFromChecks(tree, keys)
    expect(queue.map((q) => q.runScenarioId)).toEqual(['sc-a', 'sc-b'])
  })
})
