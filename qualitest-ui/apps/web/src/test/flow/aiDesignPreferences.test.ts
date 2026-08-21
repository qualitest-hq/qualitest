/**
 * 测 aiDesignPreferences：confirm 后自动保存 / pending 保存强挡偏好读写。
 * 边界：localStorage 内存模拟，无后端。
 * 单跑：pnpm test aiDesignPreferences   （在 qualitest-ui 或 apps/web 下）
 */
import { beforeEach, describe, expect, it } from 'vitest';

import {
  isAutoSaveAfterConfirm,
  isBlockWhenStagingPending,
  setAutoSaveAfterConfirm,
  setBlockWhenStagingPending,
} from '@/views/project/testFlow/utils/aiDesignPreferences';

describe('aiDesignPreferences', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('autoSaveAfterConfirm 默认关闭', () => {
    // 前提：localStorage 为空，依次开关 autoSaveAfterConfirm
    // 期望：默认 false，开启 true，关闭回 false
    expect(isAutoSaveAfterConfirm()).toBe(false);
    setAutoSaveAfterConfirm(true);
    expect(isAutoSaveAfterConfirm()).toBe(true);
    setAutoSaveAfterConfirm(false);
    expect(isAutoSaveAfterConfirm()).toBe(false);
  });

  it('blockWhenStagingPending 默认关闭可开关', () => {
    // 前提：localStorage 为空
    // 期望：默认 false；set true/false 生效
    expect(isBlockWhenStagingPending()).toBe(false);
    setBlockWhenStagingPending(true);
    expect(isBlockWhenStagingPending()).toBe(true);
    setBlockWhenStagingPending(false);
    expect(isBlockWhenStagingPending()).toBe(false);
  });
});
