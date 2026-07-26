/**
 * mentionComposer 序列化单元测试。
 *
 * 运行（apps/web 目录）：yarn test mentionSerialize
 * @vitest-environment happy-dom
 */
import { describe, expect, it } from 'vitest';

import {
  COMPOSER_DOC_VERSION,
  MENTION_CATEGORY_TAGS,
  MENTION_TABS,
  buildComposerSendPayload,
  candidateToMentionNode,
  docToMentions,
  docToPrompt,
  mentionDedupeKey,
} from '@/views/project/testFlow/composables/mentionComposer';

describe('docToPrompt', () => {
  it('文本与 chip 线性化为 @label', () => {
    const doc = {
      version: COMPOSER_DOC_VERSION,
      nodes: [
        { type: 'text' as const, text: '请根据 ' },
        { type: 'mention' as const, mentionType: 'api' as const, id: '1', label: 'POST 登录' },
        { type: 'text' as const, text: ' 修复' },
      ],
    };
    expect(docToPrompt(doc)).toBe('请根据 @api:POST 登录 修复');
  });
});

describe('docToMentions', () => {
  it('去重相同 mention', () => {
    const doc = {
      version: COMPOSER_DOC_VERSION,
      nodes: [
        { type: 'mention' as const, mentionType: 'api' as const, id: '99', label: 'A' },
        { type: 'mention' as const, mentionType: 'api' as const, id: '99', label: 'A' },
      ],
    };
    expect(docToMentions(doc)).toHaveLength(1);
    expect(docToMentions(doc)[0]).toMatchObject({ type: 'api', id: '99' });
  });

  it('var 保留 subtype', () => {
    const doc = {
      version: COMPOSER_DOC_VERSION,
      nodes: [
        { type: 'mention' as const, mentionType: 'var' as const, id: 'baseUrl', label: 'env.baseUrl', subtype: 'env' as const },
      ],
    };
    expect(docToMentions(doc)[0]?.subtype).toBe('env');
  });
});

describe('mentionDedupeKey', () => {
  it('区分 subtype', () => {
    expect(mentionDedupeKey({ type: 'var', subtype: 'env', id: 'k' })).not.toBe(
      mentionDedupeKey({ type: 'var', subtype: 'flow', id: 'k' }),
    );
  });
});

describe('buildComposerSendPayload', () => {
  it('空文档抛出错误', () => {
    expect(() =>
      buildComposerSendPayload({ version: COMPOSER_DOC_VERSION, nodes: [] }),
    ).toThrow();
  });

  it('返回 prompt 与 mentions', () => {
    const payload = buildComposerSendPayload({
      version: COMPOSER_DOC_VERSION,
      nodes: [
        { type: 'text' as const, text: 'hi' },
        { type: 'mention' as const, mentionType: 'run' as const, id: '128', label: 'Run#128' },
      ],
    });
    expect(payload.prompt).toBe('hi@run:Run#128');
    expect(payload.mentions).toHaveLength(1);
  });
});

describe('MENTION_TABS', () => {
  it('与 MENTION_CATEGORY_TAGS 键一一对应', () => {
    const tabIds = MENTION_TABS.map((t) => t.id).sort();
    const tagIds = Object.keys(MENTION_CATEGORY_TAGS).sort();
    expect(tabIds).toEqual(tagIds);
    for (const tab of MENTION_TABS) {
      expect(tab.label).toBe(MENTION_CATEGORY_TAGS[tab.id]);
    }
  });
});

describe('candidateToMentionNode', () => {
  it('保留 subtype 与 envId', () => {
    const node = candidateToMentionNode({
      type: 'var',
      id: 'token',
      label: 'env.token',
      subtype: 'env',
      envId: '42',
    });
    expect(node).toMatchObject({
      type: 'mention',
      mentionType: 'var',
      id: 'token',
      subtype: 'env',
      envId: '42',
    });
  });
});
