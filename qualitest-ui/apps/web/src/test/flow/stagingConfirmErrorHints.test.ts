/**
 * stagingConfirmErrorHints 单元测试。
 */
import { describe, expect, it } from 'vitest';

import {
  classifyStagingConfirmErrors,
  formatStagingConfirmErrorLine,
  groupStagingConfirmErrors,
} from '@/views/project/testFlow/utils/stagingConfirmErrorHints';

describe('classifyStagingConfirmErrors', () => {
  it('dependencyHints 标记为 dependency', () => {
    const lines = classifyStagingConfirmErrors(
      ['确认 addEdge:8001 需要先确认 addNode:9001'],
      [],
    );
    expect(lines[0].source).toBe('dependency');
  });

  it('开始节点错误标记为 canvas', () => {
    const lines = classifyStagingConfirmErrors([], ['流程只能有一个开始节点，当前有 2 个：A、B']);
    expect(lines[0].source).toBe('canvas');
  });

  it('HTTP 字段错误标记为 unit', () => {
    const lines = classifyStagingConfirmErrors([], ['nodes[0] HTTP 节点「登录」外联模式缺少 externalUrl']);
    expect(lines[0].source).toBe('unit');
  });
});

describe('groupStagingConfirmErrors', () => {
  it('按 dependency → canvas → unit 顺序分组', () => {
    const grouped = groupStagingConfirmErrors([
      { source: 'unit', message: '缺少 url' },
      { source: 'dependency', message: '先 confirm 节点' },
      { source: 'canvas', message: '无开始节点' },
    ]);
    expect(grouped.map((g) => g.source)).toEqual(['dependency', 'canvas', 'unit']);
  });
});

describe('formatStagingConfirmErrorLine', () => {
  it('带中文来源前缀', () => {
    expect(
      formatStagingConfirmErrorLine({ source: 'dependency', message: '需要先确认 addNode' }),
    ).toContain('依赖未满足');
  });
});
