/**
 * 测 useFlowViewport：视口写入、Flow 回写忽略与节点聚焦。
 * 边界：mock @vue-flow/core；Pinia 内存态。
 * 单跑：yarn test useFlowViewport   （在 qualitest-ui 或 apps/web 下）
 */
import { ref } from 'vue';
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { useFlowCanvasStore } from '@/views/project/testFlow/stores/flowCanvasStore';

const setViewportMock = vi.fn(async () => undefined);
const getViewportMock = vi.fn(() => ({ x: 10, y: 20, zoom: 1.1 }));
const dimensionsRef = ref({ width: 1200, height: 800 });

vi.mock('@vue-flow/core', () => ({
  useVueFlow: () => ({
    setViewport: setViewportMock,
    getViewport: getViewportMock,
    fitView: vi.fn(),
    dimensions: dimensionsRef,
  }),
}));

import { useFlowViewport } from '@/views/project/testFlow/composables/useFlowViewport';

describe('useFlowViewport', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    setViewportMock.mockClear();
    getViewportMock.mockReturnValue({ x: 10, y: 20, zoom: 1.1 });
    dimensionsRef.value = { width: 1200, height: 800 };
  });

  it('applyViewport 先更新 store 再调用 setViewport', async () => {
    // 前提：调用 applyViewport 设置新视口
    // 期望：store 先更新，setViewport 读到已写入的值
    const store = useFlowCanvasStore();
    const order: string[] = [];

    setViewportMock.mockImplementation(async () => {
      order.push(`vf:${store.viewport.x},${store.viewport.y},${store.viewport.zoom}`);
    });

    const viewport = useFlowViewport();
    await viewport.applyViewport({ x: 100, y: 200, zoom: 0.8 }, { animate: false });

    expect(store.viewport).toEqual({ x: 100, y: 200, zoom: 0.8 });
    expect(setViewportMock).toHaveBeenCalledWith(
      { x: 100, y: 200, zoom: 0.8 },
      { duration: 0 },
    );
    expect(order[0]).toBe('vf:100,200,0.8');
  });

  it('syncFromFlow 在程序化变更期间忽略 Flow 回写', async () => {
    // 前提：applyViewport 进行中收到 Flow 回写
    // 期望：忽略回写，最终保留 applyViewport 的目标视口
    const store = useFlowCanvasStore();
    store.viewport = { x: 0, y: 0, zoom: 1 };
    const viewport = useFlowViewport();

    const applyPromise = viewport.applyViewport({ x: 50, y: 60, zoom: 0.9 }, { animate: false });
    viewport.syncFromFlow({ x: 999, y: 999, zoom: 2 });
    await applyPromise;

    expect(store.viewport).toEqual({ x: 50, y: 60, zoom: 0.9 });
  });

  it('syncFromFlow 仅同步视口，不标记未保存', () => {
    // 前提：画布已 markClean，Flow 回写新视口
    // 期望：视口更新但 dirty 仍为 false
    const store = useFlowCanvasStore();
    store.viewport = { x: 0, y: 0, zoom: 1 };
    store.markClean();
    const viewport = useFlowViewport();

    viewport.syncFromFlow({ x: 40, y: -20, zoom: 1.2 });

    expect(store.viewport).toEqual({ x: 40, y: -20, zoom: 1.2 });
    expect(store.dirty).toBe(false);
  });

  it('focusNodeIds 一次算出视口并应用', async () => {
    // 前提：画布有节点且 AI 侧栏打开
    // 期望：聚焦成功，缩放只缩不放，视口偏移至节点
    const store = useFlowCanvasStore();
    store.viewport = { x: 0, y: 0, zoom: 1.2 };
    store.aiDesignPanelOpen = true;
    store.nodes = [
      { id: 'n1', position: { x: 100, y: 200 } },
    ] as never[];

    const viewport = useFlowViewport();
    const ok = await viewport.focusNodeIds(['n1']);

    expect(ok).toBe(true);
    expect(setViewportMock).toHaveBeenCalled();
    const applied = store.viewport;
    // 聚焦缩放只缩不放：上限为 STAGING_FOCUS_ZOOM（当前为 1），自 1.2 缩到 1
    expect(applied.zoom).toBe(1);
    expect(applied.x).not.toBe(0);
    expect(applied.y).not.toBe(0);
  });
});
