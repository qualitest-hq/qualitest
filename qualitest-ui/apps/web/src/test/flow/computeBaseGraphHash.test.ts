/**
 * computeBaseGraphHash 单元测试：稳定序列化与 merge 门禁。
 */
import { describe, expect, it } from 'vitest';

import type { GraphJson } from '@/utils/flow/graphTypes';
import {
  canonicalGraphJsonString,
  canonicalizeGraphJsonForHash,
  computeBaseGraphHashFromGraphJson,
  shouldBlockMergeByBaseGraphHash,
  stableStringify,
} from '@/views/project/testFlow/utils/computeBaseGraphHash';

describe('computeBaseGraphHash', () => {
  const sampleGraph: GraphJson = {
    nodes: [
      { id: '1002', type: 'http', position: { x: 1, y: 2 }, data: { name: 'B' } },
      { id: '1001', type: 'http', position: { x: 0, y: 0 }, data: { name: 'A' } },
    ],
    edges: [{ id: '8001', source: '1001', target: '1002' }],
    meta: {
      viewport: { x: 0, y: 0, zoom: 1 },
      layout: 'manual',
      activeScenarioId: 'sc1',
      scenarios: [],
      flowOutputs: [],
    },
  };

  it('canonicalizeGraphJsonForHash 按 id 排序节点与边', () => {
    const canonical = canonicalizeGraphJsonForHash(sampleGraph);
    expect(canonical.nodes.map((n) => n.id)).toEqual(['1001', '1002']);
    expect(canonical.edges.map((e) => e.id)).toEqual(['8001']);
  });

  it('节点顺序不同产生相同 canonical 字符串', () => {
    const reversed: GraphJson = {
      ...sampleGraph,
      nodes: [...sampleGraph.nodes].reverse(),
    };
    expect(canonicalGraphJsonString(sampleGraph)).toBe(canonicalGraphJsonString(reversed));
  });

  it('修改边 target 会改变 hash', async () => {
    const hashA = await computeBaseGraphHashFromGraphJson(sampleGraph);
    const changed: GraphJson = {
      ...sampleGraph,
      edges: [{ id: '8001', source: '1001', target: '9999' }],
    };
    const hashB = await computeBaseGraphHashFromGraphJson(changed);
    expect(hashA).toHaveLength(16);
    expect(hashB).toHaveLength(16);
    expect(hashA).not.toBe(hashB);
  });

  it('同一图两次 hash 一致', async () => {
    const hash1 = await computeBaseGraphHashFromGraphJson(sampleGraph);
    const hash2 = await computeBaseGraphHashFromGraphJson(JSON.parse(JSON.stringify(sampleGraph)));
    expect(hash1).toBe(hash2);
  });

  it('stableStringify 对对象键排序', () => {
    expect(stableStringify({ b: 1, a: 2 })).toBe('{"a":2,"b":1}');
  });
});

describe('shouldBlockMergeByBaseGraphHash', () => {
  it('无 preview hash 时不阻断', () => {
    expect(shouldBlockMergeByBaseGraphHash(undefined, 'abc')).toBe(false);
    expect(shouldBlockMergeByBaseGraphHash('', 'abc')).toBe(false);
  });

  it('hash 不一致时阻断', () => {
    expect(shouldBlockMergeByBaseGraphHash('aaa', 'bbb')).toBe(true);
  });

  it('hash 一致时不阻断', () => {
    expect(shouldBlockMergeByBaseGraphHash('samehashprefix12', 'samehashprefix12')).toBe(false);
  });
});
