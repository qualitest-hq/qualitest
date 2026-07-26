/**
 * aiDesignPreferences 单元测试：confirm 后自动保存偏好。
 */
import { beforeEach, describe, expect, it } from 'vitest';

import {
  isAutoSaveAfterConfirm,
  isAutoSaveAfterMerge,
  setAutoSaveAfterConfirm,
  setAutoSaveAfterMerge,
} from '@/views/project/testFlow/utils/aiDesignPreferences';

describe('aiDesignPreferences', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('autoSaveAfterConfirm 默认关闭', () => {
    expect(isAutoSaveAfterConfirm()).toBe(false);
    setAutoSaveAfterConfirm(true);
    expect(isAutoSaveAfterConfirm()).toBe(true);
    setAutoSaveAfterConfirm(false);
    expect(isAutoSaveAfterConfirm()).toBe(false);
  });

  it('兼容 legacy autoSaveAfterMerge key', () => {
    localStorage.setItem('qualitest.aiDesign.autoSaveAfterMerge', '1');
    expect(isAutoSaveAfterConfirm()).toBe(true);
    expect(isAutoSaveAfterMerge()).toBe(true);
  });
});
