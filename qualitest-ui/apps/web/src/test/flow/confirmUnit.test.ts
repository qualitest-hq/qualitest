/**
 * 测 requestConfirmFlowDesignUnit：mock 服务端 confirm API。
 * 边界：mock confirmFlowDesignUnit；Pinia 内存态，无真实后端。
 * 单跑：pnpm test confirmUnit   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it, vi, beforeEach } from 'vitest';

import { setupFreshPinia } from '@/test/helpers/pinia';
import type { GraphJson } from '@/utils/flow/graphTypes';
import { requestConfirmFlowDesignUnit } from '@/views/project/testFlow/utils/confirmFlowDesignUnit';
import { useAiStagingStore } from '@/views/project/testFlow/stores/aiStagingStore';

vi.mock('@/api/project/testFlowAi', () => ({
  confirmFlowDesignUnit: vi.fn(),
}));

import { confirmFlowDesignUnit } from '@/api/project/testFlowAi';

const mockedConfirm = vi.mocked(confirmFlowDesignUnit);

describe('requestConfirmFlowDesignUnit', () => {
  beforeEach(() => {
    mockedConfirm.mockReset();
  });

  const baseInput = {
    nodes: [],
    edges: [],
    runConfig: { activeScenarioId: 'sc1', scenarios: [{ id: 'sc1', name: '默认', envId: '', inSeed: {} }] },
    viewport: { x: 0, y: 0, zoom: 1 },
    patch: {
      addNodes: [
        {
          id: '9001',
          type: 'http',
          position: { x: 40, y: 80 },
          data: {
            name: '下一步',
            callMode: 'external',
            externalUrl: 'https://example.com/next',
            httpMethod: 'GET',
          },
        },
      ],
    },
    unitId: 'addNode:9001',
    confirmedUnitIds: [] as string[],
    testProjectId: '10',
  };

  it('缺少 testProjectId 时本地返回错误', async () => {
    const result = await requestConfirmFlowDesignUnit({
      ...baseInput,
      testProjectId: '',
    });
    expect(result.graphJson).toBeNull();
    expect(result.error).toContain('testProjectId');
    expect(mockedConfirm).not.toHaveBeenCalled();
  });

  it('调用服务端并返回 graphJson', async () => {
    const graphJson: GraphJson = {
      nodes: [
        {
          id: '9001',
          type: 'http',
          position: { x: 40, y: 80 },
          data: {
            name: '下一步',
            callMode: 'external',
            externalUrl: 'https://example.com/next',
            httpMethod: 'GET',
          },
        },
      ],
      edges: [],
      meta: {},
    };
    mockedConfirm.mockResolvedValue({
      graphJson,
      baseGraphHash: 'abcd1234efgh5678',
    });

    const result = await requestConfirmFlowDesignUnit(baseInput);
    expect(mockedConfirm).toHaveBeenCalledOnce();
    expect(result.graphJson?.nodes.map((n) => n.id)).toEqual(['9001']);
    expect(result.graphJson?.meta?.activeScenarioId).toBeUndefined();
    expect(result.graphJson?.meta?.scenarios).toBeUndefined();
  });

  it('无图 → 有图再请求并可 markConfirmed', async () => {
    setupFreshPinia();
    const stagingStore = useAiStagingStore();
    stagingStore.hydrateStagingFromPatch('msg-1', baseInput.patch, {
      nodes: [],
      edges: [],
      runConfig: baseInput.runConfig,
    });

    mockedConfirm.mockRejectedValueOnce(new Error('确认请求失败'));

    await expect(
      requestConfirmFlowDesignUnit({
        ...baseInput,
        draftOverride: {
          data: {
            name: '坏节点',
            callMode: 'external',
            externalUrl: '',
            httpMethod: 'GET',
          },
        },
      }),
    ).rejects.toThrow('确认请求失败');

    const graphJson: GraphJson = {
      nodes: [
        {
          id: '9001',
          type: 'http',
          position: { x: 40, y: 80 },
          data: {
            name: '下一步',
            callMode: 'external',
            externalUrl: 'https://example.com/next',
            httpMethod: 'GET',
          },
        },
      ],
      edges: [],
      meta: {},
    };
    mockedConfirm.mockResolvedValueOnce({
      graphJson,
      baseGraphHash: 'abcd1234efgh5678',
    });

    const second = await requestConfirmFlowDesignUnit({
      ...baseInput,
      draftOverride: {
        data: {
          name: '下一步',
          callMode: 'external',
          externalUrl: 'https://example.com/next',
          httpMethod: 'GET',
        },
      },
    });
    expect(second.graphJson).not.toBeNull();
    expect(mockedConfirm).toHaveBeenCalledTimes(2);

    stagingStore.markConfirmed('addNode:9001');
    expect(stagingStore.getUnit('addNode:9001')?.status).toBe('confirmed');
  });

  it('服务端异常向上抛出', async () => {
    mockedConfirm.mockRejectedValue(new Error('network'));
    await expect(requestConfirmFlowDesignUnit(baseInput)).rejects.toThrow('network');
  });
});
