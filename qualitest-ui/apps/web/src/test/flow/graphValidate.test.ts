/**
 * 测本地地板 validateGraphJson / validateStartNodes。
 * 业务结构规则以服务端 POST /project/testFlow/validate 为准；本文件只断言可离线即时检查。
 * 单跑：pnpm test graphValidate
 */
import { describe, expect, it } from 'vitest';

import {
  validateGraphJson,
  validateStartNodes,
} from '@/utils/flow/graphValidate';

import demoGraph from '@flow-fixtures/demo-graph.json';
import duplicateNodeId from '@flow-fixtures/invalid-duplicate-node-id.json';
import multiStart from '@flow-fixtures/invalid-multi-start.json';

describe('validateGraphJson（本地地板）', () => {
  it('标准 demo-graph 地板通过', () => {
    const result = validateGraphJson(demoGraph);
    expect(result.ok).toBe(true);
    expect(result.errors).toEqual([]);
  });

  it('根非对象报错', () => {
    expect(validateGraphJson(null).errors).toContain('根对象必须是 JSON 对象');
    expect(validateGraphJson([]).errors).toContain('根对象必须是 JSON 对象');
  });

  it('缺少 nodes / edges 数组报错', () => {
    expect(validateGraphJson({}).errors).toEqual(
      expect.arrayContaining(['缺少 nodes 数组', '缺少 edges 数组']),
    );
  });

  it('节点缺 id / 重复 id 报错', () => {
    const missing = validateGraphJson({
      nodes: [{ type: 'http' }],
      edges: [],
    });
    expect(missing.ok).toBe(false);
    expect(missing.errors.some((e) => e.includes('缺少 id'))).toBe(true);

    const dup = validateGraphJson(duplicateNodeId);
    expect(dup.ok).toBe(false);
    expect(dup.errors.some((e) => e.includes('重复 id'))).toBe(true);
  });

  it('边缺 source / target 报错', () => {
    const result = validateGraphJson({
      nodes: [{ id: 'a', type: 'http' }],
      edges: [{ id: 'e1' }, { id: 'e2', source: 'a' }],
    });
    expect(result.errors).toEqual(
      expect.arrayContaining(['edges[0] 缺少 source', 'edges[0] 缺少 target', 'edges[1] 缺少 target']),
    );
  });

  it('业务半成品（缺 callMode / 多开始）地板仍通过', () => {
    const draft = validateGraphJson(
      {
        nodes: [
          {
            id: 'h1',
            type: 'http',
            name: '半成品',
            position: { x: 0, y: 0 },
            data: {},
          },
        ],
        edges: [],
      },
      { persistMinimalOnly: true },
    );
    expect(draft.ok).toBe(true);

    // 完整业务规则由服务端负责；本地地板不拦多开始
    const multi = validateGraphJson(multiStart, { persistMinimalOnly: true });
    expect(multi.ok).toBe(true);
  });

  it('deferTopology / persistMinimalOnly 选项不改变地板结果', () => {
    const base = validateGraphJson(demoGraph);
    const deferred = validateGraphJson(demoGraph, { deferTopologyStructureRules: true });
    const floor = validateGraphJson(demoGraph, { persistMinimalOnly: false });
    expect(deferred).toEqual(base);
    expect(floor).toEqual(base);
  });
});

describe('validateStartNodes（文案定位辅助）', () => {
  it('空白图通过', () => {
    const result = validateStartNodes({ nodes: [], edges: [] });
    expect(result.ok).toBe(true);
    expect(result.ids).toEqual([]);
  });

  it('有入口且无入边时通过', () => {
    const result = validateStartNodes({
      nodes: [
        {
          id: 'se',
          type: 'scenario-entry',
          name: '入口',
          data: { scenarios: [{ id: 's1', name: '默认', envId: '', inSeed: {} }] },
        },
      ],
      edges: [],
    });
    expect(result.ok).toBe(true);
    expect(result.ids).toEqual(['se']);
  });

  it('无入口时报错', () => {
    const result = validateStartNodes({
      nodes: [{ id: 'h1', type: 'http', name: 'HTTP', data: { callMode: 'external' } }],
      edges: [],
    });
    expect(result.ok).toBe(false);
    expect(result.message).toMatch(/入口/);
  });
});
