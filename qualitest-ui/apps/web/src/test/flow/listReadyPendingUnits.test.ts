/**
 * 测 listReadyPendingUnitIds：按依赖筛可确认 pending，并稳定排序。
 * 边界：纯函数；多消息、skipUnitIds、链式 addNode+addEdge。
 * 单跑：yarn test listReadyPendingUnits   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest';

import type { FlowDesignPatch } from '@/views/project/testFlow/types/aiDesignTypes';
import { listReadyPendingUnitIds } from '@/views/project/testFlow/utils/listReadyPendingUnits';

describe('listReadyPendingUnitIds', () => {
  const patch: FlowDesignPatch = {
    addNodes: [
      { id: '9001', type: 'http', data: { name: 'A' } },
      { id: '9002', type: 'http', data: { name: 'B' } },
    ],
    addEdges: [{ id: '8001', source: '9001', target: '9002' }],
  };

  const pendingUnits = [
    { unitId: 'addNode:9001', messageId: 'msg-1' },
    { unitId: 'addNode:9002', messageId: 'msg-1' },
    { unitId: 'addEdge:8001', messageId: 'msg-1' },
  ];

  function getPatch(messageId: string) {
    return messageId === 'msg-1' ? patch : undefined;
  }

  it('未确认端点节点时 ready 不含 addEdge', () => {
    // 前提：两节点与边均 pending，confirmed 为空
    // 期望：ready 仅为两个 addNode，且按 patch 枚举序
    const ready = listReadyPendingUnitIds({
      pendingUnits,
      getPatchForMessage: getPatch,
      confirmedUnitIds: new Set(),
    });

    expect(ready).toEqual(['addNode:9001', 'addNode:9002']);
  });

  it('两端 addNode 已确认后 ready 含 addEdge', () => {
    // 前提：两节点已 confirmed，边仍 pending
    // 期望：ready 仅含该边
    const ready = listReadyPendingUnitIds({
      pendingUnits: [{ unitId: 'addEdge:8001', messageId: 'msg-1' }],
      getPatchForMessage: getPatch,
      confirmedUnitIds: new Set(['addNode:9001', 'addNode:9002']),
    });

    expect(ready).toEqual(['addEdge:8001']);
  });

  it('skipUnitIds 排除指定单元', () => {
    // 前提：两节点可确认，但 9001 在 skip 集
    // 期望：ready 仅含 9002
    const ready = listReadyPendingUnitIds({
      pendingUnits,
      getPatchForMessage: getPatch,
      confirmedUnitIds: new Set(),
      skipUnitIds: new Set(['addNode:9001']),
    });

    expect(ready).toEqual(['addNode:9002']);
  });
});
