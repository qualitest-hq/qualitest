import { describe, expect, it } from 'vitest';

import { AI_CHAT_MESSAGE_PAGE_SIZE, AI_CHAT_LOAD_OLDER_TOP_PX } from '@/utils/ai/aiChatMessagePage';

describe('aiChatMessagePage', () => {
  it('分页常量与后端默认对齐', () => {
    expect(AI_CHAT_MESSAGE_PAGE_SIZE).toBe(40);
    expect(AI_CHAT_LOAD_OLDER_TOP_PX).toBeGreaterThan(0);
  });
});
