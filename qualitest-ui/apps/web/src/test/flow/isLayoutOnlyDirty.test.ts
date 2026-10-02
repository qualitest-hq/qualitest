/**
 * 测谁：仅布局脏判定、去掉视口与坐标后的图比较。
 * 边界：只移坐标、改节点 data、增删节点、有待确认 Staging、无已保存基线、
 *       基线缺环境 id 而当前多了环境 id、补环境后再只移坐标。
 * 单跑：pnpm test isLayoutOnlyDirty（在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest'

import type { GraphJson } from '@/utils/flow/graphTypes'

import { graphJsonToSnapshotString } from '../../views/project/testFlow/utils/graphFingerprint'
import { stripViewportForCompare } from '../../views/project/testFlow/utils/reconcileFlowDirty'
import {
  isLayoutOnlyDirty,
  stripLayoutForCompare,
} from '../../views/project/testFlow/utils/isLayoutOnlyDirty'

/** 构造最小双节点一带图，可用 overrides 覆盖顶层字段 */
function sampleGraph(overrides?: Partial<GraphJson>): GraphJson {
  return {
    nodes: [
      {
        id: 'a',
        type: 'http',
        position: { x: 10, y: 20 },
        data: { name: 'A' },
      },
      {
        id: 'b',
        type: 'http',
        position: { x: 100, y: 200 },
        data: { name: 'B' },
      },
    ],
    edges: [{ id: 'e1', source: 'a', target: 'b' }],
    meta: {
      viewport: { x: 0, y: 0, zoom: 1 },
      activeScenarioId: 's1',
      scenarios: [],
    },
    ...overrides,
  }
}

/** 生成「已保存」基线字符串：序列化前去掉视口（保存基线本身不含视口） */
function baselineOf(graph: GraphJson): string {
  return graphJsonToSnapshotString(stripViewportForCompare(graph))
}

describe('isLayoutOnlyDirty', () => {
  // 前提：相对基线只改了节点坐标与视口，无待确认 Staging。
  // 期望：判定为仅布局脏。
  it('仅移坐标与视口 → true', () => {
    const saved = sampleGraph()
    const current = sampleGraph({
      nodes: saved.nodes.map((n) => ({
        ...n,
        position: { x: n.position.x + 50, y: n.position.y + 50 },
      })),
      meta: { ...saved.meta, viewport: { x: 9, y: 9, zoom: 1.2 } },
    })
    expect(
      isLayoutOnlyDirty({
        currentGraph: current,
        savedGraphSnapshot: baselineOf(saved),
        pendingStagingCount: 0,
      }),
    ).toBe(true)
  })

  // 前提：改了节点 data。
  // 期望：不是仅布局脏。
  it('改节点 data → false', () => {
    const saved = sampleGraph()
    const current = sampleGraph({
      nodes: saved.nodes.map((n, i) =>
        i === 0 ? { ...n, data: { ...n.data, name: '改过' } } : n,
      ),
    })
    expect(
      isLayoutOnlyDirty({
        currentGraph: current,
        savedGraphSnapshot: baselineOf(saved),
        pendingStagingCount: 0,
      }),
    ).toBe(false)
  })

  // 前提：增删节点。
  // 期望：不是仅布局脏。
  it('增删节点 → false', () => {
    const saved = sampleGraph()
    const current = sampleGraph({
      nodes: [
        ...saved.nodes,
        { id: 'c', type: 'http', position: { x: 0, y: 0 }, data: { name: 'C' } },
      ],
    })
    expect(
      isLayoutOnlyDirty({
        currentGraph: current,
        savedGraphSnapshot: baselineOf(saved),
        pendingStagingCount: 0,
      }),
    ).toBe(false)
  })

  // 前提：内容与基线相同（含坐标），但有待确认 Staging。
  // 期望：不是仅布局脏。
  it('有 pending Staging → false', () => {
    const saved = sampleGraph()
    expect(
      isLayoutOnlyDirty({
        currentGraph: saved,
        savedGraphSnapshot: baselineOf(saved),
        pendingStagingCount: 1,
      }),
    ).toBe(false)
  })

  // 前提：没有已保存基线。
  // 期望：不是仅布局脏。
  it('无基线 → false', () => {
    expect(
      isLayoutOnlyDirty({
        currentGraph: sampleGraph(),
        savedGraphSnapshot: null,
        pendingStagingCount: 0,
      }),
    ).toBe(false)
  })

  // 前提：基线里场景环境 id 为空；当前图补上了环境 id，并且只移动了节点坐标。
  // 期望：不是仅布局脏（环境 id 属于内容差异）。
  it('静默补 env 未刷新基线 + 仅移坐标 → false', () => {
    const saved = sampleGraph({
      meta: {
        viewport: { x: 0, y: 0, zoom: 1 },
        activeScenarioId: 's1',
        scenarios: [
          {
            id: 's1',
            name: '默认（冒烟）',
            testProjectEnvId: '',
            flowSeed: {},
            remark: '',
          },
        ],
      },
    })
    const current = sampleGraph({
      nodes: saved.nodes.map((n) => ({
        ...n,
        position: { x: n.position.x + 40, y: n.position.y + 40 },
      })),
      meta: {
        ...saved.meta,
        scenarios: [
          {
            id: 's1',
            name: '默认（冒烟）',
            testProjectEnvId: 'env-1',
            flowSeed: {},
            remark: '',
          },
        ],
      },
    })
    expect(
      isLayoutOnlyDirty({
        currentGraph: current,
        savedGraphSnapshot: baselineOf(saved),
        pendingStagingCount: 0,
      }),
    ).toBe(false)
  })

  // 前提：基线已含环境 id；当前图只改节点坐标与视口。
  // 期望：判定为仅布局脏。
  it('补 env 后刷新基线再仅移坐标 → true', () => {
    const withEnv = sampleGraph({
      meta: {
        viewport: { x: 0, y: 0, zoom: 1 },
        activeScenarioId: 's1',
        scenarios: [
          {
            id: 's1',
            name: '默认（冒烟）',
            testProjectEnvId: 'env-1',
            flowSeed: {},
            remark: '',
          },
        ],
      },
    })
    const afterTidy = sampleGraph({
      nodes: withEnv.nodes.map((n) => ({
        ...n,
        position: { x: n.position.x + 40, y: n.position.y + 40 },
      })),
      meta: { ...withEnv.meta, viewport: { x: 5, y: 5, zoom: 1.1 } },
    })
    expect(
      isLayoutOnlyDirty({
        currentGraph: afterTidy,
        savedGraphSnapshot: baselineOf(withEnv),
        pendingStagingCount: 0,
      }),
    ).toBe(true)
  })
})

describe('stripLayoutForCompare', () => {
  // 前提：图含视口与不同坐标。
  // 期望：去掉视口、节点坐标归零，data 与边不变。
  it('去掉视口并将坐标归零', () => {
    const g = sampleGraph()
    const stripped = stripLayoutForCompare(g)
    expect(stripped.meta?.viewport).toBeUndefined()
    expect(stripped.nodes.every((n) => n.position.x === 0 && n.position.y === 0)).toBe(true)
    expect(stripped.nodes[0].data).toEqual(g.nodes[0].data)
    expect(stripped.edges).toEqual(g.edges)
  })
})
