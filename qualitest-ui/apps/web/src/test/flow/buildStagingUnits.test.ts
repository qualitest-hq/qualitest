/**
 * 测 buildStagingUnits：patch 转 Staging 单元与 baseline/draft。
 * 边界：纯函数，fixture patch 与 ctx。
 * 单跑：pnpm test buildStagingUnits   （在 qualitest-ui 或 apps/web 下）
 */
import type { Edge, Node } from '@vue-flow/core';
import { describe, expect, it } from 'vitest';

import type { FlowDesignPatch } from '@/views/project/testFlow/types/aiDesignTypes';
import { buildStagingUnits } from '@/views/project/testFlow/utils/buildStagingUnits';

function node(id: string, name: string): Node {
  return {
    id,
    type: 'http',
    position: { x: 40, y: 80 },
    data: { name, callMode: 'external', externalUrl: 'https://example.com', httpMethod: 'GET' },
  };
}

describe('buildStagingUnits', () => {
  const patch: FlowDesignPatch = {
    addNodes: [{ id: '9001', type: 'http', data: { name: '新节点' } }],
    updateNodes: [{ id: '1001', data: { name: '改名' } }],
    addEdges: [{ id: '8001', source: '1001', target: '9001' }],
    suggestedDeletes: { nodeIds: ['1002'], edgeIds: ['7001'] },
    scenarioPatch: {
      activeScenarioId: 'sc2',
      addScenarios: [{ id: 'sc3', name: '回归', testProjectEnvId: '', flowSeed: {} }],
      updateScenarios: [{ id: 'sc1', name: '默认V2' }],
      deleteScenarioIds: ['sc9'],
    },
  };
  const ctx = {
    nodes: [node('1001', '旧名'), node('1002', '待删')],
    edges: [{ id: '7001', source: '1001', target: '1002' } as Edge],
    runConfig: {
      activeScenarioId: 'sc1',
      scenarios: [
        { id: 'sc1', name: '默认', testProjectEnvId: '', flowSeed: {} },
        { id: 'sc2', name: '预发', testProjectEnvId: '', flowSeed: {} },
        { id: 'sc9', name: '废弃', testProjectEnvId: '', flowSeed: {} },
      ],
    },
  };
  const units = buildStagingUnits(patch, 'msg-1', ctx);
  const unitIdsByKind = (kind: string) => units.filter((u) => u.kind === kind).map((u) => u.unitId);

  it('节点增删改各生成对应 unitId', () => {
    // 前提：patch 含 add/update/delete 节点
    // 期望：各 kind 对应唯一 unitId
    expect(unitIdsByKind('addNode')).toEqual(['addNode:9001']);
    expect(unitIdsByKind('updateNode')).toEqual(['updateNode:1001']);
    expect(unitIdsByKind('deleteNode')).toEqual(['deleteNode:1002']);
  });

  it('连线增删各生成对应 unitId', () => {
    // 前提：patch 含 add/delete 边
    // 期望：addEdge 与 deleteEdge 各一条
    expect(unitIdsByKind('addEdge')).toEqual(['addEdge:8001']);
    expect(unitIdsByKind('deleteEdge')).toEqual(['deleteEdge:7001']);
  });

  it('场景增删改与切换默认场景各生成对应 unitId', () => {
    // 前提：patch 含完整 scenarioPatch
    // 期望：四类场景单元 unitId 正确
    expect(unitIdsByKind('addScenario')).toEqual(['addScenario:sc3']);
    expect(unitIdsByKind('updateScenario')).toEqual(['updateScenario:sc1']);
    expect(unitIdsByKind('deleteScenario')).toEqual(['deleteScenario:sc9']);
    expect(unitIdsByKind('setActiveScenario')).toEqual(['scenario:activeScenarioId']);
  });

  it('所有单元默认 status=pending 且归属同一 messageId', () => {
    // 前提：buildStagingUnits 一次性生成
    // 期望：全部 pending 且 messageId 为 msg-1
    expect(units.every((u) => u.status === 'pending' && u.messageId === 'msg-1')).toBe(true);
  });

  it('updateNode 携带 baseline 与 draft', () => {
    // 前提：单独 updateNodes patch，ctx 含旧名节点
    // 期望：baseline 为旧名，draft 为新名
    const soloPatch: FlowDesignPatch = {
      updateNodes: [{ id: '1001', data: { name: '新名' } }],
    };
    const soloCtx = {
      nodes: [node('1001', '旧名')],
      edges: [] as Edge[],
    };

    const [unit] = buildStagingUnits(soloPatch, 'msg-2', soloCtx);

    expect(unit.unitId).toBe('updateNode:1001');
    expect(unit.baseline?.data).toMatchObject({ name: '旧名' });
    expect(unit.draft?.data).toMatchObject({ name: '新名' });
  });
});
