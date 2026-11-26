/**
 * 测 listPendingUnitIdsSorted：列出全部 pending（依赖不挡），并稳定排序。
 * 边界：纯函数；多消息、skipUnitIds、链式 addNode+addEdge。
 * 单跑：pnpm test listPendingUnitIds   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest';

import type { FlowDesignPatch } from '@/views/project/testFlow/types/aiDesignTypes';
import { listPendingUnitIdsSorted } from '@/views/project/testFlow/utils/listPendingUnitIds';

describe('listPendingUnitIdsSorted', () => {
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

  it('未确认端点时仍含 addEdge（依赖不挡）', () => {
    const ids = listPendingUnitIdsSorted({
      pendingUnits,
      getPatchForMessage: getPatch,
    });

    expect(ids).toEqual(['addNode:9001', 'addNode:9002', 'addEdge:8001']);
  });

  it('仅剩 addEdge pending 时含该边', () => {
    const ids = listPendingUnitIdsSorted({
      pendingUnits: [{ unitId: 'addEdge:8001', messageId: 'msg-1' }],
      getPatchForMessage: getPatch,
    });

    expect(ids).toEqual(['addEdge:8001']);
  });

  it('skipUnitIds 排除指定单元', () => {
    const ids = listPendingUnitIdsSorted({
      pendingUnits,
      getPatchForMessage: getPatch,
      skipUnitIds: new Set(['addNode:9001']),
    });

    expect(ids).toEqual(['addNode:9002', 'addEdge:8001']);
  });
});
