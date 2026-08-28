/**
 * 测 flowCanvasStore.ensureEdgesHydrated：灌入期间兜底不写 edges。
 * 边界：无 Vue Flow 挂载时 flush 失败；suppressDirty 守卫兜底。
 * 单跑：pnpm test flowCanvasStore.ensureEdgesHydrated   （在 qualitest-ui 或 apps/web 下）
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { setupFreshPinia } from '@/test/helpers/pinia';
import { useFlowCanvasStore } from '@/views/project/testFlow/stores/flowCanvasStore';

const pendingEdge = { id: 'e1', source: 'cond_alive', target: 'login_http' };

function stubDoubleRaf() {
  vi.stubGlobal('requestAnimationFrame', (cb: FrameRequestCallback) => {
    cb(0);
    return 0;
  });
}

describe('flowCanvasStore ensureEdgesHydrated', () => {
  beforeEach(() => {
    setupFreshPinia();
    stubDoubleRaf();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('suppressDirty 为 true 时重试失败后不写 edges 兜底', async () => {
    // 前提：灌入期间 pending 边存在且 Vue Flow 未 flush
    // 期望：ensureEdgesHydrated 结束后 edges 仍为空、pending 保留
    const store = useFlowCanvasStore();
    store.beginCanvasHydration();
    store.setPendingEdges([pendingEdge]);
    store.edges = [];

    await store.ensureEdgesHydrated();

    expect(store.edges).toEqual([]);
    expect(store.pendingEdges).toEqual([pendingEdge]);
  });

  it('suppressDirty 为 false 时重试失败后写入 edges 兜底', async () => {
    // 前提：非灌入场景 pending 边无法经 Vue Flow flush
    // 期望：兜底把 pending 写入 edges
    const store = useFlowCanvasStore();
    store.setPendingEdges([pendingEdge]);
    store.edges = [];

    await store.ensureEdgesHydrated();

    expect(store.edges).toEqual([pendingEdge]);
    expect(store.pendingEdges).toEqual([pendingEdge]);
  });

  it('edges 已有数据时直接返回', async () => {
    // 前提：edges 非空
    // 期望：不改动现有边
    const store = useFlowCanvasStore();
    const existing = [{ id: 'e0', source: 'a', target: 'b' }];
    store.edges = existing;
    store.setPendingEdges([pendingEdge]);

    await store.ensureEdgesHydrated();

    expect(store.edges).toEqual(existing);
  });
});
