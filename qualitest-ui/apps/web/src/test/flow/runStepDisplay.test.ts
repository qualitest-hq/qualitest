/**
 * runStepDisplay 单元测试：节点类型中文、URL 解码、失败分类、审计判定、耗时与时间线摘要。
 * 单跑：pnpm test runStepDisplay（在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest';

import {
  decodeUrlForDisplay,
  failureCategoryLabel,
  formatDurationMs,
  isAuditStep,
  isBizCodeFailure,
  nodeTypeLabelZh,
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
});
