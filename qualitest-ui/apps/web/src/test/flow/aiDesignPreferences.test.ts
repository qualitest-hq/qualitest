/**
 * 测 aiDesignPreferences：全自动开关读写。
 * 边界：localStorage 内存模拟，无后端。
 * 单跑：pnpm test aiDesignPreferences
 */
import { beforeEach, describe, expect, it } from 'vitest';

import {
  isAutopilotEnabled,
  setAutopilotEnabled,
} from '@/views/project/testFlow/utils/aiDesignPreferences';

describe('aiDesignPreferences', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('全自动偏好默认关闭可开关', () => {
    // 前提：localStorage 为空
    // 期望：读出 false；写入 true 后再读为 true；再写入 false 后读出 false
    expect(isAutopilotEnabled()).toBe(false);
    setAutopilotEnabled(true);
    expect(isAutopilotEnabled()).toBe(true);
    setAutopilotEnabled(false);
    expect(isAutopilotEnabled()).toBe(false);
  });
});
