/**
 * 测 useConditionHandleLayout：行布局变化后刷新 Vue Flow internals。
 * 边界：mock @vue-flow/core；jsdom + stub rAF / ResizeObserver。
 * 单跑：pnpm test useConditionHandleLayout   （在 qualitest-ui 或 apps/web 下）
 * @vitest-environment jsdom
 */
import { createApp, defineComponent, nextTick, ref } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const updateNodeInternalsMock = vi.fn();

vi.mock('@vue-flow/core', () => ({
  useVueFlow: () => ({
    updateNodeInternals: updateNodeInternalsMock,
  }),
}));

import { useConditionHandleLayout } from '@/views/project/testFlow/composables/useConditionHandleLayout';

describe('useConditionHandleLayout', () => {
  beforeEach(() => {
    updateNodeInternalsMock.mockClear();
    vi.stubGlobal('requestAnimationFrame', (cb: FrameRequestCallback) => {
      cb(0);
      return 0;
    });
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe() {}
        unobserve() {}
        disconnect() {}
      },
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('挂载与 remeasure 时调用 updateNodeInternals', async () => {
    // 前提：condition 节点有 branches 容器
    // 期望：挂载后与手动 remeasure 均刷新该节点 internals
    const root = document.createElement('div');
    const branchesEl = document.createElement('div');
    branchesEl.className = 'cond-node__branches';
    root.appendChild(branchesEl);
    document.body.appendChild(root);

    let remeasure: (() => Promise<void>) | null = null;
    const Host = defineComponent({
      setup() {
        const rootEl = ref(root);
        const branches = ref([{ id: 'b_else', kind: 'else' as const }]);
        const api = useConditionHandleLayout('cond_alive', branches, rootEl);
        remeasure = api.remeasure;
        return () => null;
      },
    });

    const mountEl = document.createElement('div');
    const app = createApp(Host);
    app.mount(mountEl);
    await nextTick();
    await Promise.resolve();

    expect(updateNodeInternalsMock).toHaveBeenCalledWith(['cond_alive']);

    updateNodeInternalsMock.mockClear();
    await remeasure!();
    expect(updateNodeInternalsMock).toHaveBeenCalledWith(['cond_alive']);

    app.unmount();
    document.body.removeChild(root);
  });
});
