/**
 * 测 aiChatMessagePage：分页条数、虚拟滚动阈值、触顶/贴底距离常量。
 * 边界：纯常量断言，无 DOM。
 * 单跑：pnpm test aiChatMessagePage
 */
import { describe, expect, it } from 'vitest';

import {
  AI_CHAT_MESSAGE_PAGE_SIZE,
  AI_CHAT_LOAD_OLDER_TOP_PX,
  AI_CHAT_STICK_BOTTOM_PX,
  AI_CHAT_VIRTUAL_SCROLL_THRESHOLD,
} from '@/utils/ai/aiChatMessagePage';

describe('aiChatMessagePage', () => {
  it('分页与滚动阈值常量合理', () => {
    // 前提：读取分页、虚拟滚动、触顶、贴底常量
    // 期望：PAGE_SIZE / VIRTUAL 为 40；触顶与贴底距离为正
    expect(AI_CHAT_MESSAGE_PAGE_SIZE).toBe(40);
    expect(AI_CHAT_VIRTUAL_SCROLL_THRESHOLD).toBe(40);
    expect(AI_CHAT_LOAD_OLDER_TOP_PX).toBeGreaterThan(0);
    expect(AI_CHAT_STICK_BOTTOM_PX).toBeGreaterThan(0);
  });
});
