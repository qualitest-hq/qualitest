/**
 * evalCompareRule 单元测试：验证断言/条件分支中的比较规则求值（与后端 CompareRuleEvaluator 对齐）。
 *
 * 被测函数读取 rule 的 left（flow.code、http.body.*、asset.* 等）、
 * operator（eq/ne/gt/lt/gte/lte/contains/exists 等 9 种）、right（支持占位符），
 * 在 FlowRunContext 上求值并返回 boolean。
 *
 * 数据驱动：用例来自 fixtures/compare-extract-cases.json 的 compareCases 数组，
 * 与后端 compare-extract-cases.json 共享。
 *
 * 运行（apps/web 目录）：yarn test compareRule
 */
import { describe, expect, it } from 'vitest';

import { evalCompareRule } from '@/utils/flow/compareRule';
import type { FlowRunContext } from '@/utils/flow/types';

import fixture from './fixtures/compare-extract-cases.json';

/** 夹具中的运行时上下文，各用例只读共享 */
const ctx = fixture.mockContext as FlowRunContext;

function quote(value: unknown): string {
  if (value == null) return 'null';
  return JSON.stringify(String(value));
}

describe('evalCompareRule', () => {
  // eslint-disable-next-line no-console
  console.log(`\n=== evalCompareRule fixture cases (${fixture.compareCases.length}) ===`);

  /** 数据驱动：遍历 compareCases，断言 evalCompareRule 返回值与 expected 一致 */
  for (const c of fixture.compareCases) {
    it(c.id, () => {
      const actual = evalCompareRule(c.rule, ctx);
      // eslint-disable-next-line no-console
      console.log(`  OK ${c.id.padEnd(22)}  ->  ${quote(actual)} (expected ${quote(c.expected)})`);
      expect(actual).toBe(c.expected);
    });
  }
});
