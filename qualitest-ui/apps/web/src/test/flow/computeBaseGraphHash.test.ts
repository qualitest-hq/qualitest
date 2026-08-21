/**
 * 测 computeBaseGraphHash：稳定序列化与 merge 门禁。
 * 边界：纯函数 / 异步 hash，无网络。
 * 单跑：pnpm test computeBaseGraphHash   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest';

import type { GraphJson } from '@/utils/flow/graphTypes';
import {
  canonicalGraphJsonString,
  canonicalizeGraphJsonForHash,
  computeBaseGraphHashFromGraphJson,
  shouldBlockConfirmByBaseGraphHash,
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
    // 前提：节点输入顺序为 1002、1001
    // 期望：canonical 后按 id 升序
    const canonical = canonicalizeGraphJsonForHash(sampleGraph);
    expect(canonical.nodes.map((n) => n.id)).toEqual(['1001', '1002']);
    expect(canonical.edges.map((e) => e.id)).toEqual(['8001']);
  });

  it('节点顺序不同产生相同 canonical 字符串', () => {
    // 前提：同一图节点数组顺序相反
    // 期望：canonical 字符串相同
    const reversed: GraphJson = {
      ...sampleGraph,
      nodes: [...sampleGraph.nodes].reverse(),
    };
    expect(canonicalGraphJsonString(sampleGraph)).toBe(canonicalGraphJsonString(reversed));
  });

  it('修改边 target 会改变 hash', async () => {
    // 前提：仅修改一条边的 target
    // 期望：两次 hash 均为 16 位且不相等
    const hashA = await computeBaseGraphHashFromGraphJson(sampleGraph);
    const changed: GraphJson = {
      ...sampleGraph,
      edges: [{ id: '8001', source: '1001', target: '9999' }],
    };
    const hashB = await computeBaseGraphHashFromGraphJson(changed);
    // SHA-256 hex 取前 16 位（见 computeBaseGraphHash.ts 的 sha256Prefix）
    expect(hashA).toHaveLength(16);
    expect(hashB).toHaveLength(16);
    expect(hashA).not.toBe(hashB);
  });

  it('同一图两次 hash 一致', async () => {
    // 前提：同一图深拷贝后再 hash
    // 期望：两次 hash 相同
    const hash1 = await computeBaseGraphHashFromGraphJson(sampleGraph);
    const hash2 = await computeBaseGraphHashFromGraphJson(JSON.parse(JSON.stringify(sampleGraph)));
    expect(hash1).toBe(hash2);
  });

  it('stableStringify 对对象键排序', () => {
    // 前提：键顺序为 b、a 的对象
    // 期望：输出按 a、b 排序的 JSON
    expect(stableStringify({ b: 1, a: 2 })).toBe('{"a":2,"b":1}');
  });
});

describe('shouldBlockConfirmByBaseGraphHash', () => {
  it('无 preview hash 时不阻断', () => {
    // 前提：preview hash 为 undefined 或空
    // 期望：不阻断 confirm
    expect(shouldBlockConfirmByBaseGraphHash(undefined, 'abc')).toBe(false);
    expect(shouldBlockConfirmByBaseGraphHash('', 'abc')).toBe(false);
  });

  it('hash 不一致时阻断', () => {
    // 前提：preview 与 current hash 不同
    // 期望：阻断 confirm
    expect(shouldBlockConfirmByBaseGraphHash('aaa', 'bbb')).toBe(true);
  });

  it('hash 一致时不阻断', () => {
    // 前提：preview 与 current hash 相同
    // 期望：不阻断 confirm
    expect(shouldBlockConfirmByBaseGraphHash('samehashprefix12', 'samehashprefix12')).toBe(false);
  });
});
