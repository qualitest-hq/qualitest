/**
 * runStepDisplay 单元测试：节点类型中文、URL 解码、失败分类、审计判定、耗时与时间线摘要、关联变量。
 * 边界：关联变量只取本步相关键；无键或 flowAfter 无对应项时为空。
 * 单跑：pnpm test runStepDisplay（在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest';

import {
  decodeUrlForDisplay,
  failureCategoryLabel,
  flowKeyFromPath,
  formatDurationMs,
  formatRelatedVarValue,
  isAuditStep,
  isBizCodeFailure,
  nodeTypeLabelZh,
  pickRelatedFlowVars,
  resolveFailureCategory,
  summarizeStep,
} from '@/views/project/testFlow/utils/runStepDisplay';

describe('runStepDisplay', () => {
  it('nodeTypeLabelZh 返回常见类型的中文名', () => {
    expect(nodeTypeLabelZh('http')).toBe('HTTP');
    expect(nodeTypeLabelZh('assert')).toBe('断言');
    expect(nodeTypeLabelZh('condition')).toBe('分支');
    expect(nodeTypeLabelZh('input')).toBe('等待输入');
    expect(nodeTypeLabelZh('runConfig')).toBe('场景加载');
    expect(nodeTypeLabelZh('')).toBe('-');
  });

  it('decodeUrlForDisplay 解码百分号中文，非法编码保留原文', () => {
    const encoded =
      'http://127.0.0.1:8887/list?classroomName=' + encodeURIComponent('质衡教室后台');
    expect(decodeUrlForDisplay(encoded)).toContain('质衡教室后台');
    expect(decodeUrlForDisplay('/plain')).toBe('/plain');
    expect(decodeUrlForDisplay('%E0%A4%A')).toBe('%E0%A4%A');
  });

  it('resolveFailureCategory 区分业务码、断言与其它失败', () => {
    expect(resolveFailureCategory({ nodeType: 'assert' })).toBe('assert');
    expect(
      resolveFailureCategory({
        nodeType: 'http',
        error: { code: 'TF_BIZ_CODE' },
      }),
    ).toBe('bizCode');
    expect(
      resolveFailureCategory({
        nodeType: 'http',
        http: { bizCheck: { passed: false } },
      }),
    ).toBe('bizCode');
    expect(
      resolveFailureCategory({
        nodeType: 'http',
        error: { code: 'TF_ASSERT_FAILED' },
      }),
    ).toBe('assert');
    expect(resolveFailureCategory({ nodeType: 'http', error: { code: 'X' } })).toBe('other');
    expect(failureCategoryLabel('bizCode')).toBe('业务码');
    expect(failureCategoryLabel('assert')).toBe('断言');
    expect(failureCategoryLabel('other')).toBe('其他');
  });

  it('isAuditStep / isBizCodeFailure / formatDurationMs 边界', () => {
    expect(isAuditStep('runConfig')).toBe(true);
    expect(isAuditStep('snapshot')).toBe(true);
    expect(isAuditStep('http')).toBe(false);
    expect(isBizCodeFailure({ error: { code: 'TF_BIZ_CODE' } })).toBe(true);
    expect(isBizCodeFailure({ http: { bizCheck: { passed: false } } })).toBe(true);
    expect(isBizCodeFailure({ error: { code: 'TF_ASSERT_FAILED' } })).toBe(false);
    expect(formatDurationMs(145)).toBe('145 ms');
    expect(formatDurationMs(1500)).toBe('1.50 s');
    expect(formatDurationMs(null)).toBe('-');
  });

  it('summarizeStep 赋值步骤只列键名与条数', () => {
    expect(
      summarizeStep({
        nodeType: 'assign',
        assigns: [
          { name: 'classroomName' },
          { name: 'maxCapacity' },
          { name: 'classroomNameEdited' },
          { name: 'maxCapacityEdited' },
        ],
      }),
    ).toBe('写入 classroomName 等 4 项');
  });

  it('pickRelatedFlowVars 用结构化键与 flow. 路径，断言优先 leftActual', () => {
    // 前提：写入 token/amount；断言 left=flow.amount 且有 leftActual；right 含占位符也不扫
    // 期望：token 来自 flowAfter，amount 用 leftActual；不扫 right；无相关键为空
    const vars = pickRelatedFlowVars({
      extracts: [{ name: 'token', scope: 'flow' }],
      assigns: [{ name: 'amount' }],
      script: { writes: [{ key: 'token' }] },
      assert: {
        rules: [{ left: 'flow.amount', right: '{{flow.noise}}', passed: false, leftActual: 0 }],
      },
      flowAfter: { amount: 9, token: 'abc', noise: 1 },
    });
    expect(vars.map((v) => v.key)).toEqual(['token', 'amount']);
    expect(vars.find((v) => v.key === 'amount')?.value).toBe(0);
    expect(vars.find((v) => v.key === 'noise')).toBeUndefined();
    expect(
      pickRelatedFlowVars({
        assert: { rules: [{ left: 'flow.amount', leftActual: 7 }] },
      }),
    ).toEqual([{ key: 'amount', value: 7 }]);
    expect(pickRelatedFlowVars({ flowAfter: { a: 1 } })).toEqual([]);
    expect(pickRelatedFlowVars({ assigns: [{ name: 'x' }], flowAfter: {} })).toEqual([]);
  });

  it('flowKeyFromPath 只认 flow. 前缀', () => {
    expect(flowKeyFromPath('flow.amount')).toBe('amount');
    expect(flowKeyFromPath(' flow.code ')).toBe('code');
    expect(flowKeyFromPath('http.body.code')).toBeNull();
    expect(flowKeyFromPath('{{flow.amount}}')).toBeNull();
    expect(flowKeyFromPath('flow.')).toBeNull();
  });

  it('formatRelatedVarValue 截断对象 JSON', () => {
    expect(formatRelatedVarValue('hi')).toBe('hi');
    expect(formatRelatedVarValue(null)).toBe('null');
    const long = formatRelatedVarValue({ a: 'x'.repeat(100) }, 40);
    expect(long.length).toBeLessThanOrEqual(40);
    expect(long.endsWith('…')).toBe(true);
  });
});
