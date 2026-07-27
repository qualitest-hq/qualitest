/**
 * 测 stagingLabels：Staging 种类标题、删除提示与摘要拼接。
 * 边界：纯函数，fixture 摘要对象。
 * 单跑：yarn test stagingLabels   （在 qualitest-ui 或 apps/web 下）
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
    // 前提：addNode/addScenario/updateNode 各 kind 与变体
    // 期望：panel/banner 返回对应中文标题
    expect(stagingKindTitle('addNode', 'panel')).toBe('新增节点');
    expect(stagingKindTitle('addScenario', 'banner')).toBe('待新增场景');
    expect(stagingKindTitle('updateNode', 'banner')).toBe('修改节点');
  });

  it('stagingDeleteHint 含边计数', () => {
    // 前提：deleteNode 含 edgeCount / deleteEdge 无计数
    // 期望：提示含边数或「连线」
    expect(stagingDeleteHint('deleteNode', { edgeCount: 2 })).toContain('2');
    expect(stagingDeleteHint('deleteEdge')).toContain('连线');
  });

  it('formatMessageSummaryBreakdown 拼接摘要', () => {
    // 前提：摘要含新增/修改节点与新增连线
    // 期望：文本含各项计数
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
    // 前提：摘要各项计数均为 0
    // 期望：返回「无结构化变更」
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
