/**
 * 测 evalCompareRule：比较规则求值（与后端 CompareRuleEvaluator 对齐）。
 * 边界：纯函数；用例来自 @flow-fixtures/compare-extract-cases.json。
 * 单跑：pnpm test compareRule   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest';

import { evalCompareRule } from '@/utils/flow/compareRule';
import type { FlowRunContext } from '@/utils/flow/types';

import fixture from '@flow-fixtures/compare-extract-cases.json';

/** 夹具中的运行时上下文，各用例只读共享 */
const ctx = fixture.mockContext as FlowRunContext;

describe('evalCompareRule', () => {
  it.each(fixture.compareCases.map((c) => [c.id, c] as const))(
    'fixture %s 与 expected 一致',
    (_id, c) => {
      expect(evalCompareRule(c.rule, ctx)).toBe(c.expected);
    },
  );
});
