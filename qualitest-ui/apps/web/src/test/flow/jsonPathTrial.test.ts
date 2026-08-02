/**
 * 属性面板 JsonPath 试算：未命中判定、响应示例提取、上游 HTTP 回溯、断言左值预览。
 * 单跑：yarn test jsonPathTrial
 */
import { describe, expect, it } from 'vitest';

import {
  extractResponseExample,
  findUpstreamProjectHttpNode,
  isTrialMissPreview,
  isTrialMissValue,
  previewAssertLeft,
  resolveTrialApiId,
} from '@/views/project/testFlow/utils/jsonPathTrial';

/** data 为数组的购物车响应体（无 items 键） */
const cartBody = {
  code: 200,
  data: [{ cartId: '5001', quantity: 3, subtotal: 147 }],
};

describe('jsonPathTrial design gate helpers', () => {
  it('isTrialMissValue 识别 undefined / null / []', () => {
    expect(isTrialMissValue(undefined)).toBe(true);
    expect(isTrialMissValue(null)).toBe(true);
    expect(isTrialMissValue([])).toBe(true);
    expect(isTrialMissValue(3)).toBe(false);
    expect(isTrialMissValue([3])).toBe(false);
  });

  it('误写 data.items 试算为空；正确过滤器能取出 quantity', () => {
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
});
