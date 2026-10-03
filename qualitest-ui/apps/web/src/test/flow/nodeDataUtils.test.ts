/**
 * 测谁：断言规则文案与 leftActual 精简展示。
 * 边界：exists 命中对象数组 / 空 / 标量；eq 单元素数组解包。
 * 单跑：pnpm test nodeDataUtils
 */
import { describe, expect, it } from 'vitest';

import {
  formatAssertRuleWithActual,
  formatLeftActualForDisplay,
} from '@/views/project/testFlow/utils/nodeDataUtils';

describe('formatLeftActualForDisplay / formatAssertRuleWithActual', () => {
  it('exists 命中 1 条对象：命中条数 + 短字段，不含长字段墙', () => {
    // 前提：过滤器命中一张券对象
    // 期望：实际文案含「命中 1 条」与 couponId，不含 thresholdAmount
    const hit = [
      {
        remark: 'seed',
        status: 0,
        couponId: '3002',
        couponName: '满200减30',
        totalCount: 500,
        receiveCount: 0,
        thresholdAmount: 200,
        discountAmount: 30,
        validStartTime: '2026-01-01 00:00:00',
        validEndTime: '2027-12-31 23:59:59',
      },
    ];
    const actual = formatLeftActualForDisplay('exists', hit);
    expect(actual).toContain('命中 1 条');
    expect(actual).toContain('couponId');
    expect(actual).toContain('3002');
    expect(actual).not.toContain('thresholdAmount');

    const line = formatAssertRuleWithActual({
      left: "http.body.data.rows[?(@.couponId==3002)]",
      operator: 'exists',
      leftActual: hit,
      passed: true,
    });
    expect(line).toContain('存在');
    expect(line).toContain('实际: 命中 1 条');
    expect(line).not.toContain('thresholdAmount');
  });

  it('exists 空数组 / null → 无', () => {
    expect(formatLeftActualForDisplay('exists', [])).toBe('无');
    expect(formatLeftActualForDisplay('exists', null)).toBe('无');
  });

  it('exists 命中标量数组：条数 + 标量', () => {
    expect(formatLeftActualForDisplay('exists', ['3002'])).toBe('命中 1 条 · "3002"');
  });

  it('eq 单元素数组解包后再 stringify', () => {
    expect(formatLeftActualForDisplay('eq', ['3002'])).toBe('"3002"');
    expect(formatLeftActualForDisplay('eq', [1, 2])).toBe('[1,2]');
  });
});
