/**
 * 测 evalCompareRule：比较规则求值（与后端 CompareRuleEvaluator 对齐）。
 * 边界：纯函数；用例来自 fixtures/compare-extract-cases.json。
 * 单跑：yarn test compareRule   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest';

import { evalCompareRule } from '@/utils/flow/compareRule';
import type { FlowRunContext } from '@/utils/flow/types';

import fixture from './fixtures/compare-extract-cases.json';

/** 夹具中的运行时上下文，各用例只读共享 */
const ctx = fixture.mockContext as FlowRunContext;

function caseById(id: string) {
  const c = fixture.compareCases.find((x) => x.id === id);
  if (!c) throw new Error(`missing compare case: ${id}`);
  return c;
}

describe('evalCompareRule', () => {
  it('code=0 与期望值相等时 eq 为 true', () => {
    // 前提：rule 比较 http.body.data.code 与 0
    // 期望：eq 为 true
    const c = caseById('eq-code-zero');
    expect(evalCompareRule(c.rule, ctx)).toBe(c.expected);
  });

  it('code=0 与不相等的期望值比较时 eq 为 false', () => {
    // 前提：code=0 与期望值 1 比较
    // 期望：eq 为 false
    const c = caseById('eq-code-fail');
    expect(evalCompareRule(c.rule, ctx)).toBe(c.expected);
  });

  it('token 与不同值比较时 ne 为 true', () => {
    // 前提：flow.token 与 xyz 比较
    // 期望：ne 为 true
    const c = caseById('ne-token');
    expect(evalCompareRule(c.rule, ctx)).toBe(c.expected);
  });

  it('count=5 大于 3 时 gt 为 true', () => {
    // 前提：flow.count=5 与 3 比较
    // 期望：gt 为 true
    const c = caseById('gt-count');
    expect(evalCompareRule(c.rule, ctx)).toBe(c.expected);
  });

  it('count=5 小于 10 时 lt 为 true', () => {
    // 前提：flow.count=5 与 10 比较
    // 期望：lt 为 true
    const c = caseById('lt-count');
    expect(evalCompareRule(c.rule, ctx)).toBe(c.expected);
  });

  it('count=5 大于等于 5 时 gte 为 true', () => {
    // 前提：flow.count=5 与 5 比较
    // 期望：gte 为 true
    const c = caseById('gte-count');
    expect(evalCompareRule(c.rule, ctx)).toBe(c.expected);
  });

  it('count=5 小于等于 5 时 lte 为 true', () => {
    // 前提：flow.count=5 与 5 比较
    // 期望：lte 为 true
    const c = caseById('lte-count');
    expect(evalCompareRule(c.rule, ctx)).toBe(c.expected);
  });

  it('loginUser 包含子串时 contains 为 true', () => {
    // 前提：loginUser 含子串 min
    // 期望：contains 为 true
    const c = caseById('contains-user');
    expect(evalCompareRule(c.rule, ctx)).toBe(c.expected);
  });

  it('loginUser 不包含子串时 not_contains 为 true', () => {
    // 前提：loginUser 不含 xyz
    // 期望：not_contains 为 true
    const c = caseById('not-contains');
    expect(evalCompareRule(c.rule, ctx)).toBe(c.expected);
  });

  it('字段存在时 exists 为 true', () => {
    // 前提：flow.token 存在
    // 期望：exists 为 true
    const c = caseById('exists-token');
    expect(evalCompareRule(c.rule, ctx)).toBe(c.expected);
  });

  it('字段不存在时 exists 为 false', () => {
    // 前提：flow.missing 不存在
    // 期望：exists 为 false
    const c = caseById('exists-missing');
    expect(evalCompareRule(c.rule, ctx)).toBe(c.expected);
  });

  it('right 侧占位符解析后与实际值不同时 eq 为 false', () => {
    // 前提：right 为 {{flow.token}}，与 body token 不同
    // 期望：eq 为 false
    const c = caseById('right-placeholder');
    expect(evalCompareRule(c.rule, ctx)).toBe(c.expected);
  });

  it('请求耗时大于阈值时 gt 为 true', () => {
    // 前提：http.duration=120 与 100 比较
    // 期望：gt 为 true
    const c = caseById('duration-gt');
    expect(evalCompareRule(c.rule, ctx)).toBe(c.expected);
  });

  it('asset 嵌套字段比较 eq 为 true', () => {
    // 前提：asset.defaults.nested.x 与 1 比较
    // 期望：eq 为 true
    const c = caseById('asset-nested');
    expect(evalCompareRule(c.rule, ctx)).toBe(c.expected);
  });
});
