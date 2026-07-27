/**
 * 测 requestConfirmFlowDesignUnit：mock 服务端 confirm API，含失败重试。
 * 边界：mock confirmFlowDesignUnit；Pinia 内存态，无真实后端。
 * 单跑：yarn test confirmUnit   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';

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
    runConfig: { activeScenarioId: 'sc1', scenarios: [{ id: 'sc1', name: '默认', testProjectEnvId: '', flowSeed: {} }] },
    flowOutputs: [],
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
    // 前提：testProjectId 为空
    // 期望：本地校验失败且不调用服务端
    const result = await requestConfirmFlowDesignUnit({
      ...baseInput,
      testProjectId: '',
    });
    expect(result.validation.ok).toBe(false);
    expect(mockedConfirm).not.toHaveBeenCalled();
  });

  it('调用服务端并返回 graphJson', async () => {
    // 前提：有效 testProjectId 且服务端 confirm 成功
    // 期望：返回 ok 与含新节点的 graphJson
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
      meta: { activeScenarioId: 'sc1', scenarios: [{ id: 'sc1', name: '默认', testProjectEnvId: '', flowSeed: {} }] },
    };
    mockedConfirm.mockResolvedValue({
      ok: true,
      errors: [],
      warnings: [],
      graphJson,
      baseGraphHash: 'abcd1234efgh5678',
    });

    const result = await requestConfirmFlowDesignUnit(baseInput);
    expect(mockedConfirm).toHaveBeenCalledOnce();
    expect(result.validation.ok).toBe(true);
    expect(result.graphJson?.nodes.map((n) => n.id)).toEqual(['9001']);
    expect(result.graphJson?.meta?.activeScenarioId).toBe('sc1');
  });

  it('失败 → 改 draft → 再请求可由 false 变 true', async () => {
    // 前提：首次 confirm 校验失败，修正 draft 后再次请求
    // 期望：第二次 ok 为 true，单元标记 confirmed 并清除 lastValidation
    setActivePinia(createPinia());
    const stagingStore = useAiStagingStore();
    stagingStore.hydrateStagingFromPatch('msg-1', baseInput.patch, {
      nodes: [],
      edges: [],
      runConfig: baseInput.runConfig,
    });

    mockedConfirm.mockResolvedValueOnce({
      ok: false,
      errors: ['HTTP 节点缺少 externalUrl'],
      warnings: [],
      graphJson: null,
    });

    const badDraft = {
      data: {
        name: '坏节点',
        callMode: 'external',
        externalUrl: '',
        httpMethod: 'GET',
      },
    };

    const first = await requestConfirmFlowDesignUnit({
      ...baseInput,
      draftOverride: badDraft,
    });
    expect(first.validation.ok).toBe(false);
    stagingStore.markConfirmFailed('addNode:9001', first.validation);

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
      ok: true,
      errors: [],
      warnings: [],
      graphJson,
    });

    const goodDraft = {
      data: {
        name: '下一步',
        callMode: 'external',
        externalUrl: 'https://example.com/next',
        httpMethod: 'GET',
      },
    };

    const second = await requestConfirmFlowDesignUnit({
      ...baseInput,
      draftOverride: goodDraft,
    });
    expect(second.validation.ok).toBe(true);
    expect(mockedConfirm).toHaveBeenCalledTimes(2);

    stagingStore.markConfirmed('addNode:9001');
    expect(stagingStore.getUnit('addNode:9001')?.lastValidation).toBeUndefined();
    expect(stagingStore.getUnit('addNode:9001')?.status).toBe('confirmed');
  });

  it('服务端异常向上抛出', async () => {
    // 前提：服务端 confirm 抛出 network 异常
    // 期望：异常原样向上抛出
    mockedConfirm.mockRejectedValue(new Error('network'));
    await expect(requestConfirmFlowDesignUnit(baseInput)).rejects.toThrow('network');
  });
});
