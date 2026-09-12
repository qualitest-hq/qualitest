/**
 * 测 apiAiPreferences：API 助手全自动偏好读写。
 * 边界：localStorage 内存模拟；与造流 autopilot key 隔离。
 * 单跑：pnpm test apiAiPreferences
 */
import { beforeEach, describe, expect, it } from 'vitest';

import {
  isApiAiAutopilotEnabled,
  setApiAiAutopilotEnabled,
} from '@/views/project/testProject/utils/apiAiPreferences';
import {
  isAutopilotEnabled,
  setAutopilotEnabled,
} from '@/views/project/testFlow/utils/aiDesignPreferences';

describe('apiAiPreferences', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('全自动偏好默认关闭可开关', () => {
    expect(isApiAiAutopilotEnabled()).toBe(false);
    setApiAiAutopilotEnabled(true);
    expect(isApiAiAutopilotEnabled()).toBe(true);
    setApiAiAutopilotEnabled(false);
    expect(isApiAiAutopilotEnabled()).toBe(false);
  });

  it('与造流全自动偏好互不影响', () => {
    setApiAiAutopilotEnabled(true);
    expect(isAutopilotEnabled()).toBe(false);
    setAutopilotEnabled(true);
    setApiAiAutopilotEnabled(false);
    expect(isAutopilotEnabled()).toBe(true);
  });
});
