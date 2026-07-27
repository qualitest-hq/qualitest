/**
 * 测 mentionComposer：文档序列化、去重与发送 payload 构建。
 * 边界：纯函数；happy-dom 环境（文件头标注）。
 * 单跑：yarn test mentionSerialize   （在 qualitest-ui 或 apps/web 下）
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
    // 前提：文档含 text 与 mention 节点
    // 期望：线性化为 @api:label 格式
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
    // 前提：文档含两个相同 mention
    // 期望：docToMentions 仅返回一条
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
    // 前提：mention 为 var 且含 subtype
    // 期望：输出保留 subtype=env
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
    // 前提：同 id 不同 subtype 的 var mention
    // 期望：dedupeKey 不同
    expect(mentionDedupeKey({ type: 'var', subtype: 'env', id: 'k' })).not.toBe(
      mentionDedupeKey({ type: 'var', subtype: 'flow', id: 'k' }),
    );
  });
});

describe('buildComposerSendPayload', () => {
  it('空文档抛出错误', () => {
    // 前提：文档 nodes 为空
    // 期望：buildComposerSendPayload 抛出异常
    expect(() =>
      buildComposerSendPayload({ version: COMPOSER_DOC_VERSION, nodes: [] }),
    ).toThrow();
  });

  it('返回 prompt 与 mentions', () => {
    // 前提：文档含 text 与 run mention
    // 期望：返回拼接 prompt 与 mentions 数组
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
    // 前提：MENTION_TABS 与 MENTION_CATEGORY_TAGS
    // 期望：id 集合一致且 label 匹配
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
    // 前提：候选 var 含 subtype 与 envId
    // 期望：mention 节点保留两字段
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
