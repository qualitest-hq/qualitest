/**
 * buildStagingUnits 单元测试：patch → Staging 单元与 baseline/draft。
 *
 * 运行（apps/web 目录）：pnpm test buildStagingUnits
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
  it('add/update/delete/scenario 均生成正确 unitId', () => {
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
    const ids = units.map((u) => u.unitId).sort();

    expect(ids).toEqual(
      [
        'addEdge:8001',
        'addNode:9001',
        'addScenario:sc3',
        'deleteEdge:7001',
        'deleteNode:1002',
        'deleteScenario:sc9',
        'scenario:activeScenarioId',
        'updateNode:1001',
        'updateScenario:sc1',
      ].sort(),
    );
    expect(units.every((u) => u.status === 'pending' && u.messageId === 'msg-1')).toBe(true);
  });

  it('updateNode 携带 baseline 与 draft', () => {
    const patch: FlowDesignPatch = {
      updateNodes: [{ id: '1001', data: { name: '新名' } }],
    };
    const ctx = {
      nodes: [node('1001', '旧名')],
      edges: [] as Edge[],
    };

    const [unit] = buildStagingUnits(patch, 'msg-2', ctx);

    expect(unit.unitId).toBe('updateNode:1001');
    expect(unit.baseline?.data).toMatchObject({ name: '旧名' });
    expect(unit.draft?.data).toMatchObject({ name: '新名' });
  });
});
