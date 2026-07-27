/**
 * 测 resolveNextStagingFocusUnit：确认后下一聚焦单元解析。
 * 边界：纯函数，仅 fixture 单元列表。
 * 单跑：yarn test stagingFocusNavigation   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest';

import type { AiStagingUnit } from '@/views/project/testFlow/types/aiStagingTypes';
import type { FlowDesignPatch } from '@/views/project/testFlow/types/aiDesignTypes';
import { resolveNextStagingFocusUnit } from '@/views/project/testFlow/utils/stagingFocusNavigation';

function unit(
  unitId: string,
  kind: AiStagingUnit['kind'],
  status: AiStagingUnit['status'] = 'pending',
): AiStagingUnit {
  return {
    unitId,
    kind,
    status,
    messageId: 'msg-1',
    label: unitId,
    draft: {},
    patchSlice: {},
  };
}

describe('resolveNextStagingFocusUnit', () => {
  const patch: FlowDesignPatch = {
    addNodes: [
      { id: '9001', type: 'http', data: { name: '节点A' } },
      { id: '9002', type: 'http', data: { name: '节点B' } },
    ],
    addEdges: [{ id: '8001', source: '9001', target: '9002' }],
  };

  it('确认第一个节点后聚焦下一个 pending 节点', () => {
    // 前提：首节点已 confirmed，次节点仍 pending
    // 期望：下一聚焦为 addNode:9002
    const pendingUnits = [
      unit('addNode:9001', 'addNode', 'confirmed'),
      unit('addNode:9002', 'addNode'),
      unit('addEdge:8001', 'addEdge'),
    ];
    const next = resolveNextStagingFocusUnit(
      unit('addNode:9001', 'addNode', 'confirmed'),
      patch,
      pendingUnits,
      new Set(['addNode:9001']),
      [],
    );
    expect(next?.unitId).toBe('addNode:9002');
  });

  it('两个节点都确认后聚焦其间连线', () => {
    // 前提：两端节点均已 confirmed，连线仍 pending
    // 期望：下一聚焦为 addEdge:8001
    const pendingUnits = [
      unit('addNode:9001', 'addNode', 'confirmed'),
      unit('addNode:9002', 'addNode', 'confirmed'),
      unit('addEdge:8001', 'addEdge'),
    ];
    const next = resolveNextStagingFocusUnit(
      unit('addNode:9002', 'addNode', 'confirmed'),
      patch,
      pendingUnits,
      new Set(['addNode:9001', 'addNode:9002']),
      [{ id: '8001', source: '9001', target: '9002' }],
    );
    expect(next?.unitId).toBe('addEdge:8001');
  });

  it('确认连线后聚焦下一个 pending 节点', () => {
    // 前提：连线已 confirmed，后续仍有 pending 节点
    // 期望：下一聚焦为 addNode:9003
    const extendedPatch: FlowDesignPatch = {
      ...patch,
      addNodes: [
        ...(patch.addNodes ?? []),
        { id: '9003', type: 'http', data: { name: '节点C' } },
      ],
    };
    const extendedUnits = [
      unit('addNode:9001', 'addNode', 'confirmed'),
      unit('addNode:9002', 'addNode', 'confirmed'),
      unit('addEdge:8001', 'addEdge', 'confirmed'),
      unit('addNode:9003', 'addNode'),
    ];
    const next = resolveNextStagingFocusUnit(
      unit('addEdge:8001', 'addEdge', 'confirmed'),
      extendedPatch,
      extendedUnits,
      new Set(['addNode:9001', 'addNode:9002', 'addEdge:8001']),
      [{ id: '8001', source: '9001', target: '9002' }],
    );
    expect(next?.unitId).toBe('addNode:9003');
  });
});
