/**
 * 测 stagingMessage：Staging 摘要显隐。
 * 单跑：pnpm test stagingMessage
 */
import { describe, expect, it } from 'vitest';

import type { AiDesignMessageView } from '@/views/project/testFlow/types/aiDesignTypes';
import { shouldShowStagingSummary } from '@/views/project/testFlow/utils/stagingMessage';

function assistant(partial: Partial<AiDesignMessageView>): AiDesignMessageView {
  return {
    id: 'm1',
    role: 'assistant',
    content: 'ok',
    ...partial,
  };
}

describe('shouldShowStagingSummary', () => {
  it('有 patch 且非 explainOnly 时展示', () => {
    // 前提：助手消息带 patch，且非纯答疑
    // 期望：展示 Staging 摘要
    expect(
      shouldShowStagingSummary(
        assistant({ patch: { summary: '改抽取' } as AiDesignMessageView['patch'], explainOnly: false }),
      ),
    ).toBe(true);
  });

  it('explainOnly 时不展示 Staging 摘要', () => {
    // 前提：纯答疑轮次
    // 期望：不展示 Staging 摘要
    expect(shouldShowStagingSummary(assistant({ explainOnly: true }))).toBe(false);
  });
});
