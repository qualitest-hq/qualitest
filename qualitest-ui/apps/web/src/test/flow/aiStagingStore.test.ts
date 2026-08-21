/**
 * 测 aiStagingStore：CRUD、摘要、保存过滤与 message 迁移。
 * 边界：Pinia 内存态，无 API 依赖。
 * 单跑：pnpm test aiStagingStore   （在 qualitest-ui 或 apps/web 下）
 */
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it } from 'vitest';

import type { FlowDesignPatch } from '@/views/project/testFlow/types/aiDesignTypes';
import { useAiStagingStore } from '@/views/project/testFlow/stores/aiStagingStore';

describe('aiStagingStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  const patch: FlowDesignPatch = {
    addNodes: [{ id: '9001', type: 'http', data: { name: '新节点' } }],
    updateNodes: [{ id: '1001', data: { name: '改名' } }],
  };

  const ctx = {
    nodes: [{ id: '1001', type: 'http', position: { x: 0, y: 0 }, data: { name: '旧名' } }],
    edges: [],
    runConfig: { activeScenarioId: 'sc1', scenarios: [{ id: 'sc1', name: '默认', testProjectEnvId: '', flowSeed: {} }] },
  };

  it('hydrateStagingFromPatch 创建 pending 单元', () => {
    // 前提：patch 含 addNode 与 updateNode
    // 期望：两单元 pending，staging map 与摘要 pending=2
    const store = useAiStagingStore();
    store.hydrateStagingFromPatch('msg-1', patch, ctx);

    expect(store.listUnitsForMessage('msg-1')).toHaveLength(2);
    expect(store.stagingByNodeId['9001']?.mode).toBe('add');
    expect(store.stagingByNodeId['1001']?.mode).toBe('update');
    expect(store.buildMessageSummary('msg-1').pending).toBe(2);
  });

  it('markConfirmFailed 保持 pending 并写入 lastValidation', () => {
    // 前提：confirm 校验失败
    // 期望：status 仍 pending，lastValidation 含错误
    const store = useAiStagingStore();
    store.hydrateStagingFromPatch('msg-1', patch, ctx);
    store.markConfirmFailed('addNode:9001', { ok: false, errors: ['缺少 externalUrl'], warnings: [] });

    const unit = store.getUnit('addNode:9001');
    expect(unit?.status).toBe('pending');
    expect(unit?.lastValidation?.errors[0]).toContain('externalUrl');
    expect(unit?.confirmInFlight).toBe(false);
  });

  it('markConfirmed 清除 lastValidation', () => {
    // 前提：先 markConfirmFailed 再 markConfirmed
    // 期望：status 为 confirmed，lastValidation 清除，staging map 移除
    const store = useAiStagingStore();
    store.hydrateStagingFromPatch('msg-1', patch, ctx);
    store.markConfirmFailed('addNode:9001', { ok: false, errors: ['err'], warnings: [] });
    store.markConfirmed('addNode:9001');

    const unit = store.getUnit('addNode:9001');
    expect(unit?.status).toBe('confirmed');
    expect(unit?.lastValidation).toBeUndefined();
    expect(store.stagingByNodeId['9001']).toBeUndefined();
  });

  it('buildPersistFilter 排除 pending add 并回滚 pending update', () => {
    // 前提：含 pending add 与 update
    // 期望：excludeNodeIds 含 add，nodeBaselines 含 update 基线
    const store = useAiStagingStore();
    store.hydrateStagingFromPatch('msg-1', patch, ctx);
    const filter = store.buildPersistFilter();

    expect(filter.excludeNodeIds.has('9001')).toBe(true);
    expect(filter.nodeBaselines.has('1001')).toBe(true);
    expect(filter.nodeBaselines.get('1001')?.data).toMatchObject({ name: '旧名' });
  });

  it('markRejected 后不再出现在 staging map', () => {
    // 前提：reject addNode 单元
    // 期望：status rejected，stagingByNodeId 无该节点
    const store = useAiStagingStore();
    store.hydrateStagingFromPatch('msg-1', patch, ctx);
    store.markRejected('addNode:9001');

    expect(store.getUnit('addNode:9001')?.status).toBe('rejected');
    expect(store.stagingByNodeId['9001']).toBeUndefined();
  });

  it('buildConfirmPersistFilter 排除其它 pending，保留本次确认单元', () => {
    // 前提：confirm addNode:9001
    // 期望：9001 不在 exclude，1001 基线仍保留
    const store = useAiStagingStore();
    store.hydrateStagingFromPatch('msg-1', patch, ctx);

    const filter = store.buildConfirmPersistFilter('addNode:9001');

    expect(filter.excludeNodeIds.has('9001')).toBe(false);
    expect(filter.excludeNodeIds.has('1001')).toBe(false);
    expect(filter.nodeBaselines.has('1001')).toBe(true);
  });

  it('rehydrate 不覆盖已 confirmed 的单元', () => {
    // 前提：单元已 confirmed 后再次 hydrate 同 patch
    // 期望：status 仍为 confirmed
    const store = useAiStagingStore();
    store.hydrateStagingFromPatch('msg-1', patch, ctx);
    store.markConfirmed('addNode:9001');
    store.hydrateStagingFromPatch('msg-1', patch, ctx);

    expect(store.getUnit('addNode:9001')?.status).toBe('confirmed');
    expect(store.stagingByNodeId['9001']).toBeUndefined();
  });

  it('migrateMessageIds 迁移单元与 patch 缓存', () => {
    // 前提：client-msg 映射为 server-msg
    // 期望：单元与 patch 归属 server-msg
    const store = useAiStagingStore();
    store.hydrateStagingFromPatch('client-msg', patch, ctx);

    store.migrateMessageIds(new Map([['client-msg', 'server-msg']]));

    expect(store.listUnitsForMessage('client-msg')).toHaveLength(0);
    expect(store.listUnitsForMessage('server-msg')).toHaveLength(2);
    expect(store.getPatchForMessage('server-msg')).toEqual(patch);
  });

  it('buildMessageSummary 摘要计数与 listUnitsForMessage 按状态过滤一致', () => {
    // 前提：混合 confirmed/rejected/pending 单元
    // 期望：摘要各计数与 listUnitsForMessage 一致
    const store = useAiStagingStore();
    const summaryPatch: FlowDesignPatch = {
      addNodes: [{ id: 'a1', type: 'http', data: { name: 'A' } }],
      addEdges: [{ id: 'e1', source: 'a1', target: 'b1' }],
      scenarioPatch: {
        addScenarios: [{ id: 'sc-new', name: '新场景', testProjectEnvId: '', flowSeed: {} }],
      },
    };
    const summaryCtx = {
      nodes: [],
      edges: [],
      runConfig: {
        activeScenarioId: 'sc1',
        scenarios: [{ id: 'sc1', name: '默认', testProjectEnvId: '', flowSeed: {} }],
      },
    };

    store.hydrateStagingFromPatch('msg-sum', summaryPatch, summaryCtx);
    store.markConfirmed('addNode:a1');
    store.markRejected('addEdge:e1');

    const units = store.listUnitsForMessage('msg-sum');
    const summary = store.buildMessageSummary('msg-sum');

    expect(summary.pending).toBe(units.filter((u) => u.status === 'pending').length);
    expect(summary.confirmed).toBe(units.filter((u) => u.status === 'confirmed').length);
    expect(summary.rejected).toBe(units.filter((u) => u.status === 'rejected').length);
    expect(summary.addNodeCount).toBe(1);
    expect(summary.addEdgeCount).toBe(1);
    expect(summary.scenarioCount).toBe(1);
  });
});
