/**
 * 测 graphAdapter：GraphJson 与 Vue Flow 画布状态双向转换。
 * 边界：纯函数；demo-graph fixture，无 Pinia。
 * 单跑：yarn test graphAdapter   （在 qualitest-ui 或 apps/web 下）
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
  it('包含默认 viewport 与至少一条 run 场景', () => {
    // 前提：调用 createEmptyGraph
    // 期望：空 nodes/edges，meta 含默认 viewport 与至少一条场景
    const graph = createEmptyGraph();
    expect(graph.nodes).toEqual([]);
    expect(graph.edges).toEqual([]);
    expect(graph.meta?.viewport).toEqual({ x: 40, y: 40, zoom: 1 });
    expect(graph.meta?.layout).toBe('manual');
    expect(graph.meta?.schemaVersion).toBe(1);
    expect(graph.meta?.scenarios?.length).toBeGreaterThanOrEqual(1);
    expect(graph.meta?.activeScenarioId).toMatch(/^\d+$/);
    expect(graph.meta?.scenarios?.[0]?.id).toBe(graph.meta?.activeScenarioId);
    expect(graph.meta?.flowOutputs).toEqual([]);
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
    expect(roundtrip.meta?.schemaVersion).toBe(1);
    expect(roundtrip.meta?.activeScenarioId).toBe('2042000000000000101');
    expect(roundtrip.meta?.scenarios).toHaveLength(3);
    expect(roundtrip.meta?.startNodeId).toBe('2040354743931883501');
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

  it('空图往返保留 meta 场景', () => {
    // 前提：空图设置 viewport 与 testProjectEnvId 后往返
    // 期望：meta viewport 与 envId 保留
    const empty = createEmptyGraph({ testProjectEnvId: '1001' });
    const adapted = fromGraphJson(empty);
    adapted.viewport = { x: 120, y: 80, zoom: 1.25 };
    const saved = toGraphJson(adapted);
    expect(saved.meta?.viewport).toEqual({ x: 120, y: 80, zoom: 1.25 });
    expect(saved.meta?.scenarios?.[0]?.testProjectEnvId).toBe('1001');
  });

  it('toGraphJson 排除未 confirm 的 Staging add 并回滚 pending update', () => {
    // 前提：stagingFilter 排除 add、回滚 update 基线
    // 期望：输出无 staging 新增节点，update 节点恢复基线名称
    const adapted = fromGraphJson(demoGraph);
    const baseNode = adapted.nodes[0];
    const stagingNode: Node = {
      id: 'staging-add-1',
      type: 'http',
      position: { x: 100, y: 100 },
      data: { name: 'Staging 新增' },
    };
    const canvasNode = {
      ...baseNode,
      data: { ...baseNode.data, name: '画布已改' },
    };
    const baselineData = JSON.parse(JSON.stringify(baseNode.data)) as Record<string, unknown>;
    baselineData.name = '基线名称';

    const graph = toGraphJson({
      ...adapted,
      nodes: [canvasNode, ...adapted.nodes.slice(1), stagingNode],
      stagingFilter: {
        excludeNodeIds: new Set(['staging-add-1']),
        excludeEdgeIds: new Set(),
        nodeBaselines: new Map([
          [baseNode.id, { type: baseNode.type, position: baseNode.position, data: baselineData }],
        ]),
        edgeBaselines: new Map(),
      },
    });

    expect(graph.nodes.some((n) => n.id === 'staging-add-1')).toBe(false);
    expect(graph.nodes.find((n) => n.id === baseNode.id)?.data?.name).toBe('基线名称');
  });
});
