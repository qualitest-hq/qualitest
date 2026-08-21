/**
 * 测 collectStagingConfirmHighlightIds：confirm 后高亮节点 id 收集。
 * 边界：纯函数，仅 fixture 边列表。
 * 单跑：pnpm test mergeHighlight   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest';

import { collectStagingConfirmHighlightIds } from '@/views/project/testFlow/utils/mergeHighlight';

describe('collectStagingConfirmHighlightIds', () => {
  const edges = [{ id: '8001', source: '1001', target: '1002' }];

  it('updateNode 返回节点 id', () => {
    // 前提：单元为 updateNode:1001
    // 期望：高亮列表仅含节点 1001
    expect(collectStagingConfirmHighlightIds('updateNode:1001', 'updateNode', edges)).toEqual(['1001']);
  });

  it('updateEdge 返回端点节点 id', () => {
    // 前提：单元为 updateEdge:8001
    // 期望：高亮列表含 source 与 target 节点
    expect(collectStagingConfirmHighlightIds('updateEdge:8001', 'updateEdge', edges)).toEqual([
      '1001',
      '1002',
    ]);
  });
});
