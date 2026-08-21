/**
 * 测 stagingFocusTransform：侧栏遮挡、缩放策略与聚焦 viewport 计算。
 * 边界：纯函数，无 Vue Flow 实例。
 * 单跑：pnpm test stagingFocusTransform   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest';

import {
  computeStagingFocusViewport,
  resolveAiDockOverlapPx,
  resolveStagingFocusZoom,
} from '@/views/project/testFlow/utils/stagingFocusTransform';

describe('stagingFocusTransform', () => {
  it('resolveStagingFocusZoom 仅缩小不放大', () => {
    // 前提：当前 zoom 高于/低于/等于上限
    // 期望：只取 min(当前, 上限)，不放大
    // 0.9、1.2、0.75 为用例自定的当前/上限缩放值，不对应生产常量
    expect(resolveStagingFocusZoom(1.2, 0.9)).toBe(0.9);
    expect(resolveStagingFocusZoom(0.75, 0.9)).toBe(0.75);
    expect(resolveStagingFocusZoom(0.9, 0.9)).toBe(0.9);
  });

  it('resolveAiDockOverlapPx 不超过 AI_DOCK_WIDTH_MAX_PX（480px）', () => {
    // 前提：pane 宽度 2000、1000、0
    // 期望：遮挡宽度 capped 于 480 或按比例计算
    // 2000 * AI_DOCK_WIDTH_VW(0.38) = 760 > 480，取上限 480
    expect(resolveAiDockOverlapPx(2000)).toBe(480);
    // 1000 * AI_DOCK_WIDTH_VW(0.38) = 380 < 480，取比例值 380
    expect(resolveAiDockOverlapPx(1000)).toBe(380);
    expect(resolveAiDockOverlapPx(0)).toBe(0);
  });

  it('computeStagingFocusViewport 在扣除侧栏后的可见区居中', () => {
    // 前提：有侧栏遮挡的 pane 与节点包围盒
    // 期望：zoom 不变，x/y 使目标在可见区居中
    const vp = computeStagingFocusViewport({
      bounds: { x: 100, y: 200, width: 300, height: 108 },
      paneWidth: 1000,
      paneHeight: 800,
      dockOverlapPx: 380,
      zoom: 0.9,
    });
    const cx = 100 + 300 / 2;
    const cy = 200 + 108 / 2;
    const visibleCenterX = (1000 - 380) / 2;
    const visibleCenterY = 800 / 2;
    expect(vp.zoom).toBe(0.9);
    expect(vp.x).toBeCloseTo(visibleCenterX - cx * 0.9);
    expect(vp.y).toBeCloseTo(visibleCenterY - cy * 0.9);
  });

  it('侧栏关闭时目标落在 pane 水平中心', () => {
    // 前提：dockOverlapPx 为 0
    // 期望：目标中心对齐 pane 中心
    const vp = computeStagingFocusViewport({
      bounds: { x: 0, y: 0, width: 300, height: 108 },
      paneWidth: 1200,
      paneHeight: 600,
      dockOverlapPx: 0,
      zoom: 1,
    });
    expect(vp.x).toBeCloseTo(600 - 150);
    expect(vp.y).toBeCloseTo(300 - 54);
  });
});
