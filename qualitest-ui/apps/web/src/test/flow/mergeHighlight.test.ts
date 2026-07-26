/**
 * mergeHighlight 单元测试。
 */
import { describe, expect, it } from 'vitest';

import { collectStagingConfirmHighlightIds } from '@/views/project/testFlow/utils/mergeHighlight';

describe('collectStagingConfirmHighlightIds', () => {
  const edges = [{ id: '8001', source: '1001', target: '1002' }];

  it('updateNode 返回节点 id', () => {
    expect(collectStagingConfirmHighlightIds('updateNode:1001', 'updateNode', edges)).toEqual(['1001']);
  });

  it('updateEdge 返回端点节点 id', () => {
    expect(collectStagingConfirmHighlightIds('updateEdge:8001', 'updateEdge', edges)).toEqual([
      '1001',
      '1002',
    ]);
  });
});
