/**
 * stagingFocusTransform 单元测试。
 *
 * 覆盖：侧栏遮挡宽度计算、缩放只缩不放策略、可见区居中 viewport 计算。
 */
import { describe, expect, it } from 'vitest';

import {
  computeStagingFocusViewport,
  resolveAiDockOverlapPx,
  resolveStagingFocusZoom,
} from '@/views/project/testFlow/utils/stagingFocusTransform';

describe('stagingFocusTransform', () => {
  it('resolveStagingFocusZoom 仅缩小不放大', () => {
    expect(resolveStagingFocusZoom(1.2, 0.9)).toBe(0.9);
    expect(resolveStagingFocusZoom(0.75, 0.9)).toBe(0.75);
    expect(resolveStagingFocusZoom(0.9, 0.9)).toBe(0.9);
  });

  it('resolveAiDockOverlapPx 不超过 480px', () => {
    expect(resolveAiDockOverlapPx(2000)).toBe(480);
    expect(resolveAiDockOverlapPx(1000)).toBe(380);
    expect(resolveAiDockOverlapPx(0)).toBe(0);
  });

  it('computeStagingFocusViewport 在扣除侧栏后的可见区居中', () => {
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
