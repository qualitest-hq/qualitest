/**
 * 测谁：isLayoutOnlyDirty / stripLayoutForCompare。
 * 边界：仅移坐标、改 data、增删节点、pending Staging、无基线。
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

function baselineOf(graph: GraphJson): string {
  // 已保存快照序列化时不含视口字段
  return graphJsonToSnapshotString(stripViewportForCompare(graph))
}

describe('isLayoutOnlyDirty', () => {
  // 前提：相对基线只改了节点坐标与视口，无 Staging。
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

  // 前提：内容与基线相同（含坐标），但有 pending Staging。
  // 期望：false。
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

  // 前提：无已保存基线。
  // 期望：false。
  it('无基线 → false', () => {
    expect(
      isLayoutOnlyDirty({
        currentGraph: sampleGraph(),
        savedGraphSnapshot: null,
        pendingStagingCount: 0,
      }),
    ).toBe(false)
  })
})

describe('stripLayoutForCompare', () => {
  // 前提：图含视口与不同坐标。
  // 期望：视口去掉、坐标归零，其它字段保留。
  it('去掉视口并将坐标归零', () => {
    const g = sampleGraph()
    const stripped = stripLayoutForCompare(g)
    expect(stripped.meta?.viewport).toBeUndefined()
    expect(stripped.nodes.every((n) => n.position.x === 0 && n.position.y === 0)).toBe(true)
    expect(stripped.nodes[0].data).toEqual(g.nodes[0].data)
    expect(stripped.edges).toEqual(g.edges)
  })
})
