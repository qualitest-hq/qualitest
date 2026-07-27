/**
 * 测 stagingDependencyHints：Staging 确认依赖提示与阻断判定。
 * 边界：纯函数，无 Pinia / 画布。
 * 单跑：yarn test stagingDependencyHints   （在 qualitest-ui 或 apps/web 下）
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
    // 前提：addEdge 对应 addNode 尚未 confirm
    // 期望：返回依赖提示且 confirm 被阻断
    const hints = getStagingDependencyHints('addEdge:8001', patch, new Set());
    expect(hints).toEqual(['请先确认「下一步」']);
    expect(isStagingConfirmBlockedByDependencies('addEdge:8001', patch, new Set())).toBe(true);
  });

  it('先 confirm addNode 后 addEdge 无依赖阻断', () => {
    // 前提：addNode 已在 confirmedUnitIds 中
    // 期望：无依赖提示且 confirm 不被阻断
    const confirmed = new Set(['addNode:9001']);
    expect(getStagingDependencyHints('addEdge:8001', patch, confirmed)).toEqual([]);
    expect(isStagingConfirmBlockedByDependencies('addEdge:8001', patch, confirmed)).toBe(false);
  });
});
