/**
 * 属性面板 JsonPath 试算 + 设计期 schema 门禁。
 * 单跑：pnpm test jsonPathTrial
 */
import { describe, expect, it } from 'vitest';

import {
  collectAssertPathDesignIssues,
  extractResponseExample,
  extractResponseSchemaPaths,
  findUpstreamProjectHttpNode,
  isTrialMissPreview,
  isTrialMissValue,
  pathMatchesSchema,
  previewAssertLeft,
  resolveTrialApiId,
} from '@/views/project/testFlow/utils/jsonPathTrial';

/** data 为数组的购物车响应体（无 items 键） */
const cartBody = {
  code: 200,
  data: [{ cartId: '5001', quantity: 3, subtotal: 147 }],
};

const cartResponseConfig = JSON.stringify({
  responses: [
    {
      id: 'r1',
      schema: {
        type: 'object',
        properties: {
          code: { type: 'integer' },
          data: {
            type: 'array',
            items: {
              type: 'object',
              properties: {
                cartId: { type: 'integer' },
                quantity: { type: 'integer' },
                subtotal: { type: 'number' },
              },
            },
          },
        },
      },
      example: { code: 0, data: [{ cartId: 0, quantity: 0, subtotal: 0 }] },
    },
  ],
});

describe('jsonPathTrial design gate helpers', () => {
  it('isTrialMissValue 识别 undefined / null / []', () => {
    expect(isTrialMissValue(undefined)).toBe(true);
    expect(isTrialMissValue(null)).toBe(true);
    expect(isTrialMissValue([])).toBe(true);
    expect(isTrialMissValue(3)).toBe(false);
    expect(isTrialMissValue([3])).toBe(false);
  });

  it('软试算：误写 data.items 为空；正确过滤器能取出 quantity', () => {
    const bad = previewAssertLeft(cartBody, "http.body.data.items[?(@.cartId=='5001')].quantity");
    const good = previewAssertLeft(cartBody, "http.body.data[?(@.cartId=='5001')].quantity");
    expect(isTrialMissPreview(bad)).toBe(true);
    expect(isTrialMissPreview(good)).toBe(false);
    expect(good).toContain('3');
  });

  it('extractResponseExample 读取首个 responses[].example', () => {
    const example = extractResponseExample(
      JSON.stringify({
        responses: [{ id: 'r1', example: cartBody }],
      }),
    );
    expect(example).toEqual(cartBody);
  });

  it('extractResponseSchemaPaths / pathMatchesSchema 认过滤器', () => {
    const paths = extractResponseSchemaPaths(cartResponseConfig);
    expect(paths.some((p) => p.includes('quantity'))).toBe(true);
    expect(pathMatchesSchema("data[?(@.cartId=='5001')].quantity", paths)).toBe(true);
    expect(pathMatchesSchema('data[0].notAField', paths)).toBe(false);
  });

  it('findUpstreamProjectHttpNode / resolveTrialApiId 能回溯到上游接口 id', () => {
    const nodes = [
      { id: 'h1', type: 'http', data: { testProjectApiId: '99', callMode: 'project' } },
      { id: 'a1', type: 'assert', data: { rules: [] } },
    ] as any[];
    const edges = [{ id: 'e1', source: 'h1', target: 'a1' }] as any[];
    expect(findUpstreamProjectHttpNode('a1', nodes, edges)?.id).toBe('h1');
    expect(resolveTrialApiId(nodes[1], nodes, edges)).toBe('99');
    expect(resolveTrialApiId(nodes[0], nodes, edges)).toBe('99');
  });

  it('collectAssertPathDesignIssues：.items 硬拦；缺字段警告；无 schema 警告；过滤器通过', () => {
    const graph = {
      nodes: [
        { id: 'h1', type: 'http', data: { testProjectApiId: '99', callMode: 'project' } },
        {
          id: 'a1',
          type: 'assert',
          data: {
            name: '断言',
            rules: [{ left: "http.body.data.items[?(@.cartId=='5001')].quantity", operator: 'eq', right: '3' }],
          },
        },
        {
          id: 'a2',
          type: 'assert',
          data: {
            name: '好断言',
            rules: [{ left: "http.body.data[?(@.cartId=='5001')].quantity", operator: 'eq', right: '3' }],
          },
        },
        {
          id: 'a3',
          type: 'assert',
          data: {
            name: '缺字段',
            rules: [{ left: 'http.body.data[0].notAField', operator: 'eq', right: '3' }],
          },
        },
      ],
      edges: [
        { id: 'e1', source: 'h1', target: 'a1' },
        { id: 'e2', source: 'h1', target: 'a2' },
        { id: 'e3', source: 'h1', target: 'a3' },
      ],
    };
    const schemaPaths = extractResponseSchemaPaths(cartResponseConfig);
    const hit = collectAssertPathDesignIssues(graph, new Map([['99', schemaPaths]]));
    expect(hit.errors.some((e) => e.includes('.items'))).toBe(true);
    expect(hit.errors.some((e) => e.includes('好断言'))).toBe(false);
    expect(hit.warnings.some((w) => w.includes('缺字段') && w.includes('schema'))).toBe(true);

    const goodOnly = {
      nodes: [graph.nodes[0], graph.nodes[2]],
      edges: [graph.edges[1]],
    };
    expect(collectAssertPathDesignIssues(goodOnly, new Map([['99', schemaPaths]]))).toEqual({
      errors: [],
      warnings: [],
    });
    const noSchema = collectAssertPathDesignIssues(graph, new Map([['99', []]]));
    expect(noSchema.errors).toHaveLength(0);
    expect(noSchema.warnings.some((w) => w.includes('无响应 schema'))).toBe(true);
  });
});
