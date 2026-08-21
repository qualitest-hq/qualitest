/**
 * 前后端共用夹具中的 JsonPath 用例（下标、过滤器、通配、length、缺叶）。
 * 单跑：pnpm test jsonPathContract
 */
import { describe, expect, it } from 'vitest';

import { evalJsonPath, isValidJsonPath } from '@/utils/flow/placeholder';

import fixture from '@flow-fixtures/compare-extract-cases.json';

const body = fixture.mockContext.lastResponse.body;

describe('jsonPathContract', () => {
  it.each(
    (fixture as { jsonPathContractCases: Array<{ id: string; expr: string; expected: unknown }> })
      .jsonPathContractCases.map((c) => [c.id, c] as const),
  )('%s', (_id, c) => {
    const actual = evalJsonPath(body, c.expr);
    // 后端缺叶 → null，前端 → undefined，此处按「未命中」统一断言
    if (c.expected === null) {
      expect(actual == null).toBe(true);
    } else {
      expect(actual).toEqual(c.expected);
    }
  });

  it('isValidJsonPath 拒绝残缺括号', () => {
    expect(isValidJsonPath('$.data.items[0]')).toBe(true);
    expect(isValidJsonPath('$.data[')).toBe(false);
    expect(isValidJsonPath('data.token')).toBe(false);
  });
});
