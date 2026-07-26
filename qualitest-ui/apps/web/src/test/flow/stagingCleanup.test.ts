/**
 * Staging 会话级清理与截断检测。
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
    hydratePending('msg-a');
    hydratePending('msg-b');

    const messages = [{ id: 'msg-a' }, { id: 'msg-b' }];

    expect(hasPendingStagingFromMessageIndex(messages, 0)).toBe(true);
    expect(hasPendingStagingFromMessageIndex(messages, 1)).toBe(true);
    expect(hasPendingStagingFromMessageIndex(messages, 2)).toBe(false);
  });

  it('revertPendingStagingForMessageIds 仅回滚指定 message 的 pending', () => {
    hydratePending('msg-a');
    hydratePending('msg-b');

    revertPendingStagingForMessageIds(new Set(['msg-a']));

    expect(revertMock).toHaveBeenCalledTimes(1);
    expect(revertMock).toHaveBeenCalledWith('addNode:9001');
  });

  it('clearAllStagingState 回滚画布并重置 store', () => {
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
