/**
 * aiStagingStore 单元测试：CRUD、摘要、保存过滤。
 *
 * 运行（apps/web 目录）：pnpm test aiStagingStore
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
    const store = useAiStagingStore();
    store.hydrateStagingFromPatch('msg-1', patch, ctx);

    expect(store.listUnitsForMessage('msg-1')).toHaveLength(2);
    expect(store.stagingByNodeId['9001']?.mode).toBe('add');
    expect(store.stagingByNodeId['1001']?.mode).toBe('update');
    expect(store.buildMessageSummary('msg-1').pending).toBe(2);
  });

  it('markConfirmFailed 保持 pending 并写入 lastValidation', () => {
    const store = useAiStagingStore();
    store.hydrateStagingFromPatch('msg-1', patch, ctx);
    store.markConfirmFailed('addNode:9001', { ok: false, errors: ['缺少 externalUrl'], warnings: [] });

    const unit = store.getUnit('addNode:9001');
    expect(unit?.status).toBe('pending');
    expect(unit?.lastValidation?.errors[0]).toContain('externalUrl');
    expect(unit?.confirmInFlight).toBe(false);
  });

  it('markConfirmed 清除 lastValidation', () => {
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
    const store = useAiStagingStore();
    store.hydrateStagingFromPatch('msg-1', patch, ctx);
    const filter = store.buildPersistFilter();

    expect(filter.excludeNodeIds.has('9001')).toBe(true);
    expect(filter.nodeBaselines.has('1001')).toBe(true);
    expect(filter.nodeBaselines.get('1001')?.data).toMatchObject({ name: '旧名' });
  });

  it('markRejected 后不再出现在 staging map', () => {
    const store = useAiStagingStore();
    store.hydrateStagingFromPatch('msg-1', patch, ctx);
    store.markRejected('addNode:9001');

    expect(store.getUnit('addNode:9001')?.status).toBe('rejected');
    expect(store.stagingByNodeId['9001']).toBeUndefined();
  });

  it('buildConfirmPersistFilter 排除其它 pending，保留本次确认单元', () => {
    const store = useAiStagingStore();
    store.hydrateStagingFromPatch('msg-1', patch, ctx);

    const filter = store.buildConfirmPersistFilter('addNode:9001');

    expect(filter.excludeNodeIds.has('9001')).toBe(false);
    expect(filter.excludeNodeIds.has('1001')).toBe(false);
    expect(filter.nodeBaselines.has('1001')).toBe(true);
  });

  it('rehydrate 不覆盖已 confirmed 的单元', () => {
    const store = useAiStagingStore();
    store.hydrateStagingFromPatch('msg-1', patch, ctx);
    store.markConfirmed('addNode:9001');
    store.hydrateStagingFromPatch('msg-1', patch, ctx);

    expect(store.getUnit('addNode:9001')?.status).toBe('confirmed');
    expect(store.stagingByNodeId['9001']).toBeUndefined();
  });

  it('migrateMessageIds 迁移单元与 patch 缓存', () => {
    const store = useAiStagingStore();
    store.hydrateStagingFromPatch('client-msg', patch, ctx);

    store.migrateMessageIds(new Map([['client-msg', 'server-msg']]));

    expect(store.listUnitsForMessage('client-msg')).toHaveLength(0);
    expect(store.listUnitsForMessage('server-msg')).toHaveLength(2);
    expect(store.getPatchForMessage('server-msg')).toEqual(patch);
  });
});
