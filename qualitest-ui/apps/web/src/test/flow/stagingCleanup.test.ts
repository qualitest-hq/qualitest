/**
 * 测 stagingCleanup：会话级 pending 检测、回滚与全量清理。
 * 边界：mock revertStagingUnitOnCanvas；Pinia 内存态。
 * 单跑：yarn test stagingCleanup   （在 qualitest-ui 或 apps/web 下）
 */
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { FlowDesignPatch } from '@/views/project/testFlow/types/aiDesignTypes';
import { useAiStagingStore } from '@/views/project/testFlow/stores/aiStagingStore';
import { useFlowCanvasStore } from '@/views/project/testFlow/stores/flowCanvasStore';

const revertMock = vi.fn();

vi.mock('@/views/project/testFlow/utils/stagingCanvasRevert', () => ({
  revertStagingUnitOnCanvas: (unit: { unitId: string }) => revertMock(unit.unitId),
}));

import {
  clearAllStagingState,
  hasPendingStagingFromMessageIndex,
  revertPendingStagingForMessageIds,
} from '@/views/project/testFlow/utils/stagingCleanup';

describe('stagingCleanup', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    revertMock.mockReset();
  });

  function hydratePending(messageId: string) {
    const stagingStore = useAiStagingStore();
    const patch: FlowDesignPatch = {
      addNodes: [{ id: '9001', type: 'http', data: { name: '暂存' } }],
    };
    stagingStore.hydrateStagingFromPatch(messageId, patch, {
      nodes: [],
      edges: [],
      runConfig: {
        activeScenarioId: 'sc1',
        scenarios: [{ id: 'sc1', name: '默认', testProjectEnvId: '', flowSeed: {} }],
      },
    });
  }

  it('hasPendingStagingFromMessageIndex 检测 fromIndex 后的 pending', () => {
    // 前提：两条 message 均有 pending 单元
    // 期望：fromIndex 0/1 为 true，2 为 false
    hydratePending('msg-a');
    hydratePending('msg-b');

    const messages = [{ id: 'msg-a' }, { id: 'msg-b' }];

    expect(hasPendingStagingFromMessageIndex(messages, 0)).toBe(true);
    expect(hasPendingStagingFromMessageIndex(messages, 1)).toBe(true);
    expect(hasPendingStagingFromMessageIndex(messages, 2)).toBe(false);
  });

  it('revertPendingStagingForMessageIds 仅回滚指定 message 的 pending', () => {
    // 前提：两条 message 各有 pending，仅指定 msg-a
    // 期望：只 revert 一条 addNode 单元
    hydratePending('msg-a');
    hydratePending('msg-b');

    revertPendingStagingForMessageIds(new Set(['msg-a']));

    expect(revertMock).toHaveBeenCalledTimes(1);
    expect(revertMock).toHaveBeenCalledWith('addNode:9001');
  });

  it('clearAllStagingState 回滚画布并重置 store', () => {
    // 前提：存在 pending 单元且画布已写入节点
    // 期望：revert 被调用，store 清空且 pendingCount 为 0
    hydratePending('msg-a');
    const stagingStore = useAiStagingStore();
    const canvasStore = useFlowCanvasStore();
    canvasStore.nodes = [
      { id: '9001', type: 'http', position: { x: 0, y: 0 }, data: { name: '暂存' } },
    ];

    clearAllStagingState();

    expect(revertMock).toHaveBeenCalled();
    expect(Object.keys(stagingStore.unitsById)).toHaveLength(0);
    expect(stagingStore.pendingCount).toBe(0);
  });
});
