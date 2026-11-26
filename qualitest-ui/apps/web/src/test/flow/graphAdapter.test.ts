/**
 * 测 graphAdapter：GraphJson 与 Vue Flow 画布状态双向转换。
 * 边界：纯函数；demo-graph fixture，无 Pinia。
 * 单跑：pnpm test graphAdapter   （在 qualitest-ui 或 apps/web 下）
 */
import type { Node } from '@vue-flow/core';
import { describe, expect, it } from 'vitest';

import {
  createEmptyGraph,
  fromGraphJson,
  toGraphJson,
} from '@/views/project/testFlow/graphAdapter';

import demoGraph from '@flow-fixtures/demo-graph.json';

describe('createEmptyGraph', () => {
  it('空 nodes/edges 与默认 viewport、schemaVersion 2', () => {
    // 前提：调用 createEmptyGraph
    // 期望：无 scenario-entry；仅默认 viewport 与 schemaVersion
    const graph = createEmptyGraph();
    expect(graph.nodes).toEqual([]);
    expect(graph.edges).toEqual([]);
    expect(graph.meta?.viewport).toEqual({ x: 40, y: 40, zoom: 1 });
    expect(graph.meta?.layout).toBe('default');
    expect(graph.meta?.schemaVersion).toBe(2);
    expect(graph.meta?.scenarios).toBeUndefined();
    expect(graph.meta?.activeScenarioId).toBeUndefined();
  });
});

describe('fromGraphJson / toGraphJson', () => {
  it('demo-graph 往返不丢失 viewport 与 run', () => {
    // 前提：demo-graph 经 fromGraphJson → toGraphJson 往返
    // 期望：viewport、场景、边数量不丢失
    const adapted = fromGraphJson(demoGraph);
    expect(adapted.viewport).toEqual({ x: 0, y: 0, zoom: 1 });
    expect(adapted.runConfig.activeScenarioId).toBe('2042000000000000101');
    expect(adapted.runConfig.scenarios).toHaveLength(3);

    const roundtrip = toGraphJson(adapted);
    expect(roundtrip.meta?.viewport).toEqual({ x: 0, y: 0, zoom: 1 });
    expect(roundtrip.meta?.schemaVersion).toBe(2);
    const entry = roundtrip.nodes.find((n) => n.type === 'scenario-entry');
    expect((entry?.data?.scenarios as unknown[])?.length).toBe(3);
    expect(roundtrip.meta).not.toHaveProperty('startNodeId');
    expect(roundtrip.edges).toHaveLength(adapted.edges.length);
  });

  it('剥离 vue-flow UI 字段', () => {
    // 前提：节点带 selected/dragging/computedPosition
    // 期望：序列化 JSON 不含 UI 字段
    const adapted = fromGraphJson(demoGraph);
    const uiNode: Node = {
      ...adapted.nodes[0],
      selected: true,
      dragging: true,
      computedPosition: { x: 1, y: 2 },
    };
    const graph = toGraphJson({
      ...adapted,
      nodes: [uiNode],
    });
    const serialized = JSON.stringify(graph);
    expect(serialized).not.toContain('"selected"');
    expect(serialized).not.toContain('"dragging"');
    expect(serialized).not.toContain('"computedPosition"');
  });

  it('condition 出边加载时还原 sourceHandle 与 edge type', () => {
    // 前提：demo-graph 含 condition 节点两条出边
    // 期望：IF/ELSE 边 type 为 condition 且 sourceHandle 正确
    const adapted = fromGraphJson(demoGraph);
    const condId = '2040354743931883503';
    const condEdges = adapted.edges.filter((e) => e.source === condId);
    expect(condEdges).toHaveLength(2);
    const ifEdge = condEdges.find((e) => e.target === '2040354743931883504');
    const elseEdge = condEdges.find((e) => e.target === '2040354743931883505');
    expect(ifEdge?.type).toBe('condition');
    expect(ifEdge?.sourceHandle).toBe('out-b_if');
    expect(elseEdge?.type).toBe('condition');
    expect(elseEdge?.sourceHandle).toBe('out-b_else');
  });

  it('condition 出边保存时不持久化 sourceHandle', () => {
    const adapted = fromGraphJson(demoGraph);
    const saved = toGraphJson(adapted);
    const condId = '2040354743931883503';
    saved.edges
      .filter((e) => e.source === condId)
      .forEach((e) => {
        expect(Object.prototype.hasOwnProperty.call(e, 'sourceHandle')).toBe(false);
      });
  });

  it('缺省排版读成默认，横向分层能写回', () => {
    // 前提：一份没写 layout 的图，以及一份横向分层的图
    // 期望：缺省收成 default；横向分层往返后仍是 horizontal
    const raw = JSON.parse(JSON.stringify(demoGraph)) as { meta: { layout?: string } }
    delete raw.meta.layout
    expect(fromGraphJson(raw).layoutPreset).toBe('default')
    expect(toGraphJson(fromGraphJson(raw)).meta?.layout).toBe('default')

    raw.meta.layout = 'horizontal'
    const adapted = fromGraphJson(raw)
    expect(adapted.layoutPreset).toBe('horizontal')
    expect(toGraphJson(adapted).meta?.layout).toBe('horizontal')
  })

  it('未指定 scenarioEntryNodeId 时不覆盖任一场景入口的 scenarios', () => {
    // 前提：两场景入口各有自己的场景；toGraphJson 不传 scenarioEntryNodeId
    // 期望：两个入口的 scenarios 均保持原样
    const graph = {
      nodes: [
        {
          id: 'e-a',
          type: 'scenario-entry',
          name: 'A',
          position: { x: 0, y: 0 },
          data: { scenarios: [{ id: 'sa', name: '入口A', envId: '1', inSeed: { a: 1 } }] },
        },
        {
          id: 'e-b',
          type: 'scenario-entry',
          name: 'B',
          position: { x: 0, y: 80 },
          data: { scenarios: [{ id: 'sb', name: '入口B', envId: '2', inSeed: { b: 2 } }] },
        },
      ],
      edges: [],
      meta: {},
    };
    const adapted = fromGraphJson(graph);
    const saved = toGraphJson({
      ...adapted,
      runConfig: {
        activeScenarioId: 'hijack',
        scenarios: [{ id: 'hijack', name: '串写', envId: '9', inSeed: {} }],
      },
      scenarioEntryNodeId: null,
    });
    const a = saved.nodes.find((n) => n.id === 'e-a');
    const b = saved.nodes.find((n) => n.id === 'e-b');
    expect(a?.data?.scenarios?.[0]?.id).toBe('sa');
    expect(b?.data?.scenarios?.[0]?.id).toBe('sb');
  });

  it('指定 scenarioEntryNodeId 时只写回该入口', () => {
    // 前提：两场景入口；只指定写回 e-b
    // 期望：e-b 场景被替换，e-a 不变
    const graph = {
      nodes: [
        {
          id: 'e-a',
          type: 'scenario-entry',
          name: 'A',
          position: { x: 0, y: 0 },
          data: { scenarios: [{ id: 'sa', name: '入口A', envId: '1', inSeed: {} }] },
        },
        {
          id: 'e-b',
          type: 'scenario-entry',
          name: 'B',
          position: { x: 0, y: 80 },
          data: { scenarios: [{ id: 'sb', name: '入口B', envId: '2', inSeed: {} }] },
        },
      ],
      edges: [],
      meta: {},
    };
    const adapted = fromGraphJson(graph);
    const saved = toGraphJson({
      ...adapted,
      runConfig: {
        activeScenarioId: 'nb',
        scenarios: [{ id: 'nb', name: '新B', envId: '3', inSeed: { x: 1 } }],
      },
      scenarioEntryNodeId: 'e-b',
    });
    expect(saved.nodes.find((n) => n.id === 'e-a')?.data?.scenarios?.[0]?.id).toBe('sa');
    expect(saved.nodes.find((n) => n.id === 'e-b')?.data?.scenarios?.[0]?.id).toBe('nb');
  });

  it('空图往返保留 viewport', () => {
    // 前提：空白空图改 viewport 后经 fromGraphJson → toGraphJson 往返
    // 期望：meta viewport 保留；createEmptyGraph 不再用 envId 种子场景
    const empty = createEmptyGraph();
    const adapted = fromGraphJson(empty);
    adapted.viewport = { x: 120, y: 80, zoom: 1.25 };
    const saved = toGraphJson(adapted);
    expect(saved.nodes).toEqual([]);
    expect(saved.edges).toEqual([]);
    expect(saved.meta?.viewport).toEqual({ x: 120, y: 80, zoom: 1.25 });
    expect(saved.nodes.some((n) => n.type === 'scenario-entry')).toBe(false);
  });

  it('toGraphJson 给缺 from 的返回节点补 flow.out', () => {
    // 前提：http-return / subflow-return 的 data.from 为空
    // 期望：落盘后 from 为 flow.out
    const saved = toGraphJson({
      nodes: [
        {
          id: 'r1',
          type: 'http-return',
          name: '回',
          position: { x: 0, y: 0 },
          data: { status: 200 },
        },
        {
          id: 'r2',
          type: 'subflow-return',
          name: '挂父',
          position: { x: 80, y: 0 },
          data: { from: '  ' },
        },
      ] as never,
      edges: [],
      runConfig: { scenarios: [], activeScenarioId: '' },
      viewport: { x: 0, y: 0, zoom: 1 },
      layoutPreset: 'default',
    });
    expect(saved.nodes.find((n) => n.id === 'r1')?.data?.from).toBe('flow.out');
    expect(saved.nodes.find((n) => n.id === 'r2')?.data?.from).toBe('flow.out');
  });

  it('toGraphJson 排除未 confirm 的 Staging add 并回滚 pending update', () => {
    // 前提：stagingFilter 排除 add、回滚 update 基线
    // 期望：输出无 staging 新增节点，update 节点恢复基线名称
    const adapted = fromGraphJson(demoGraph);
    const baseNode = adapted.nodes[0];
    const stagingNode: Node & { name?: string } = {
      id: 'staging-add-1',
      type: 'http',
      name: 'Staging 新增',
      position: { x: 100, y: 100 },
      data: {},
    };
    const canvasNode = {
      ...baseNode,
      name: '画布已改',
    };
    const baselineData = JSON.parse(JSON.stringify(baseNode.data)) as Record<string, unknown>;

    const graph = toGraphJson({
      ...adapted,
      nodes: [canvasNode, ...adapted.nodes.slice(1), stagingNode],
      stagingFilter: {
        excludeNodeIds: new Set(['staging-add-1']),
        excludeEdgeIds: new Set(),
        nodeBaselines: new Map([
          [
            baseNode.id,
            { type: baseNode.type, name: '基线名称', position: baseNode.position, data: baselineData },
          ],
        ]),
        edgeBaselines: new Map(),
      },
    });

    expect(graph.nodes.some((n) => n.id === 'staging-add-1')).toBe(false);
    expect(graph.nodes.find((n) => n.id === baseNode.id)?.name).toBe('基线名称');
  });
});
