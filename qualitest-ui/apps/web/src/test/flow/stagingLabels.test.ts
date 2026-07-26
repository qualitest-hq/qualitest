/**
 * stagingLabels 单元测试。
 *
 * 运行（apps/web 目录）：yarn test stagingLabels
 */
import { describe, expect, it } from 'vitest';

import type { AiStagingMessageSummary } from '@/views/project/testFlow/types/aiStagingTypes';
import {
  formatMessageSummaryBreakdown,
  stagingDeleteHint,
  stagingKindTitle,
} from '@/views/project/testFlow/utils/stagingLabels';

describe('stagingLabels', () => {
  it('stagingKindTitle panel / banner 变体', () => {
    expect(stagingKindTitle('addNode', 'panel')).toBe('新增节点');
    expect(stagingKindTitle('addScenario', 'banner')).toBe('待新增场景');
    expect(stagingKindTitle('updateNode', 'banner')).toBe('修改节点');
  });

  it('stagingDeleteHint 含边计数', () => {
    expect(stagingDeleteHint('deleteNode', { edgeCount: 2 })).toContain('2');
    expect(stagingDeleteHint('deleteEdge')).toContain('连线');
  });

  it('formatMessageSummaryBreakdown 拼接摘要', () => {
    const summary: AiStagingMessageSummary = {
      messageId: 'm1',
      pending: 3,
      confirmed: 0,
      rejected: 0,
      addNodeCount: 1,
      updateNodeCount: 1,
      deleteNodeCount: 0,
      addEdgeCount: 1,
      updateEdgeCount: 0,
      deleteEdgeCount: 0,
      scenarioCount: 0,
    };
    const text = formatMessageSummaryBreakdown(summary);
    expect(text).toContain('新增节点 1');
    expect(text).toContain('修改节点 1');
    expect(text).toContain('新增连线 1');
  });

  it('formatMessageSummaryBreakdown 无变更时回退文案', () => {
    const summary: AiStagingMessageSummary = {
      messageId: 'm1',
      pending: 0,
      confirmed: 0,
      rejected: 0,
      addNodeCount: 0,
      updateNodeCount: 0,
      deleteNodeCount: 0,
      addEdgeCount: 0,
      updateEdgeCount: 0,
      deleteEdgeCount: 0,
      scenarioCount: 0,
    };
    expect(formatMessageSummaryBreakdown(summary)).toBe('无结构化变更');
  });
});
