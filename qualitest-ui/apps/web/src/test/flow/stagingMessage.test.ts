/**
 * 测 stagingMessage：Staging 摘要 / explainOnly 提示显隐。
 * 单跑：yarn test stagingMessage --run
 */
import { describe, expect, it } from 'vitest';

import type { AiDesignMessageView } from '@/views/project/testFlow/types/aiDesignTypes';
import {
  shouldShowExplainOnlyHint,
  shouldShowStagingSummary,
} from '@/views/project/testFlow/utils/stagingMessage';

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
    expect(
      shouldShowStagingSummary(
        assistant({ patch: { summary: '改抽取' } as AiDesignMessageView['patch'], explainOnly: false }),
      ),
    ).toBe(true);
  });

  it('explainOnly 时不展示 Staging 摘要', () => {
    expect(shouldShowStagingSummary(assistant({ explainOnly: true }))).toBe(false);
  });
});

describe('shouldShowExplainOnlyHint', () => {
  it('explainOnly 助手消息展示提示', () => {
    expect(shouldShowExplainOnlyHint(assistant({ explainOnly: true }))).toBe(true);
  });

  it('有 Staging 的改图轮次不展示 explainOnly 提示', () => {
    expect(
      shouldShowExplainOnlyHint(
        assistant({ patch: { summary: '改抽取' } as AiDesignMessageView['patch'], explainOnly: false }),
      ),
    ).toBe(false);
  });

  it('流式中的同 id 消息不展示，避免闪一下', () => {
    expect(shouldShowExplainOnlyHint(assistant({ id: 'm1', explainOnly: true }), 'm1')).toBe(false);
    expect(shouldShowExplainOnlyHint(assistant({ id: 'm1', explainOnly: true }), 'm2')).toBe(true);
  });
});
