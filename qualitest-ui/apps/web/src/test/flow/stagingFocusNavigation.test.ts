/**
 * 测 resolveNextStagingFocusUnit：按 patch 枚举序 + 刚确认节点相连边优先。
 * 单跑：pnpm test stagingFocusNavigation --run
 */
import { describe, expect, it } from 'vitest';

import type { FlowDesignPatch } from '@/views/project/testFlow/types/aiDesignTypes';
import type { AiStagingUnit } from '@/views/project/testFlow/types/aiStagingTypes';
import { resolveNextStagingFocusUnit } from '@/views/project/testFlow/utils/stagingFocusNavigation';

function unit(
  partial: Pick<AiStagingUnit, 'unitId' | 'kind'> & Partial<AiStagingUnit>,
): AiStagingUnit {
  return {
    messageId: 'm1',
    status: 'pending',
    ...partial,
  } as AiStagingUnit;
}

describe('resolveNextStagingFocusUnit', () => {
  const patch: FlowDesignPatch = {
    addNodes: [
      { id: '9001', type: 'http', data: { name: 'A' } },
      { id: '9002', type: 'http', data: { name: 'B' } },
    ],
    addEdges: [{ id: '8001', source: '9001', target: '9002' }],
  };

  const units = [
    unit({ unitId: 'addNode:9001', kind: 'addNode', status: 'confirmed' }),
    unit({ unitId: 'addNode:9002', kind: 'addNode' }),
    unit({ unitId: 'addEdge:8001', kind: 'addEdge' }),
  ];

  it('确认 addNode 后优先聚焦相连 pending 边（不要求另一端已确认）', () => {
    const next = resolveNextStagingFocusUnit(
      units[0],
      patch,
      units,
      new Set(['addNode:9001']),
      [],
    );
    expect(next?.unitId).toBe('addEdge:8001');
  });

  it('无相连边时按 patch 枚举序取下一 pending 节点', () => {
    const noEdgePatch: FlowDesignPatch = {
      addNodes: [
        { id: '9001', type: 'http', data: { name: 'A' } },
        { id: '9002', type: 'http', data: { name: 'B' } },
      ],
    };
    const noEdgeUnits = [
      unit({ unitId: 'addNode:9001', kind: 'addNode', status: 'confirmed' }),
      unit({ unitId: 'addNode:9002', kind: 'addNode' }),
    ];
    const next = resolveNextStagingFocusUnit(
      noEdgeUnits[0],
      noEdgePatch,
      noEdgeUnits,
      new Set(['addNode:9001']),
      [],
    );
    expect(next?.unitId).toBe('addNode:9002');
  });
});
