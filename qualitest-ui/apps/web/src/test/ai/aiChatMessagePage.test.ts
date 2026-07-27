/**
 * 测 aiChatMessagePage：会话分页与虚拟滚动相关常量。
 * 边界：纯常量断言，无 DOM / API。
 * 单跑：yarn test aiChatMessagePage   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest';

import { AI_CHAT_MESSAGE_PAGE_SIZE, AI_CHAT_LOAD_OLDER_TOP_PX } from '@/utils/ai/aiChatMessagePage';

describe('aiChatMessagePage', () => {
  it('分页常量与后端默认对齐', () => {
    // 前提：读取导出的分页常量
    // 期望：PAGE_SIZE 为 40，LOAD_OLDER_TOP_PX 大于 0
    expect(AI_CHAT_MESSAGE_PAGE_SIZE).toBe(40); // 与后端默认对齐
    expect(AI_CHAT_LOAD_OLDER_TOP_PX).toBeGreaterThan(0);
  });
});
