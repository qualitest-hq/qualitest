/**
 * stagingDependencyHints 单元测试。
 *
 * 运行（apps/web 目录）：pnpm test stagingDependencyHints
 */
import { describe, expect, it } from 'vitest';

import type { FlowDesignPatch } from '@/views/project/testFlow/types/aiDesignTypes';
import {
  getStagingDependencyHints,
  isStagingConfirmBlockedByDependencies,
} from '@/views/project/testFlow/utils/stagingDependencyHints';

describe('stagingDependencyHints', () => {
  const patch: FlowDesignPatch = {
    addNodes: [{ id: '9001', type: 'http', data: { name: '下一步' } }],
    addEdges: [{ id: '8001', source: '1001', target: '9001' }],
  };

  it('confirm addEdge 缺少 addNode 时返回 dependencyHints', () => {
    const hints = getStagingDependencyHints('addEdge:8001', patch, new Set());
    expect(hints).toEqual(['请先确认「下一步」']);
    expect(isStagingConfirmBlockedByDependencies('addEdge:8001', patch, new Set())).toBe(true);
  });

  it('先 confirm addNode 后 addEdge 无依赖阻断', () => {
    const confirmed = new Set(['addNode:9001']);
    expect(getStagingDependencyHints('addEdge:8001', patch, confirmed)).toEqual([]);
    expect(isStagingConfirmBlockedByDependencies('addEdge:8001', patch, confirmed)).toBe(false);
  });
});
