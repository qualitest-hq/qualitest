/**
 * 测 stagingAuthHints：托管头识别、Diff 标签、鉴权 warning code 解析。
 * 边界：无 headers / 显式头 / profileManaged；按 AUTH_* code 筛选而非中文关键词。
 * 单跑：yarn test stagingAuthHints   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest';

import {
  AUTH_WARNING_CODES,
  collectAuthManagedHeaderHints,
  filterAuthRelatedWarnings,
  hasProfileManagedHeaders,
  parseAuthWarning,
  stagingHeadersFieldLabel,
} from '@/views/project/testFlow/utils/stagingAuthHints';

describe('stagingAuthHints', () => {
  it('无 headers 时不算托管', () => {
    // 前提：draft 无 headers
    // 期望：hasProfileManagedHeaders=false，标签为「请求头」
    expect(hasProfileManagedHeaders({ data: { name: 'x' } })).toBe(false);
    expect(stagingHeadersFieldLabel({ data: {} })).toBe('请求头');
    expect(collectAuthManagedHeaderHints({ data: {} })).toEqual([]);
  });

  it('显式头无 profileManaged 不算托管', () => {
    // 前提：Authorization 存在但无 profileManaged
    // 期望：不算托管
    const draft = {
      data: {
        headers: [{ name: 'Authorization', value: 'Bearer {{flow.token}}' }],
      },
    };
    expect(hasProfileManagedHeaders(draft)).toBe(false);
    expect(stagingHeadersFieldLabel(draft)).toBe('请求头');
  });

  it('profileManaged 头生成提示与标签', () => {
    // 前提：含 profileManaged Authorization
    // 期望：标签带「按项目鉴权补全」，提示含 Authorization
    const draft = {
      data: {
        headers: [
          {
            name: 'Authorization',
            value: 'Bearer {{flow.token}}',
            profileManaged: true,
          },
        ],
      },
    };
    expect(hasProfileManagedHeaders(draft)).toBe(true);
    expect(stagingHeadersFieldLabel(draft)).toBe('请求头（按项目鉴权补全）');
    expect(collectAuthManagedHeaderHints(draft)[0]).toContain('Authorization');
  });

  it('按 AUTH_* code 筛选 warning，展示剥离 code 后的文案', () => {
    // 前提：混有普通校验与带稳定 code 的鉴权 warnings
    // 期望：仅鉴权相关保留，且不含 code 前缀
    expect(filterAuthRelatedWarnings([
      '边缺少 source',
      `${AUTH_WARNING_CODES.HEADER_MANAGED}: HTTP 节点「资料」已按项目鉴权补全 Authorization（托管头，Run 时随项目配置刷新）`,
      `${AUTH_WARNING_CODES.TOKEN_MISSING}: 图中使用了客户端 Bearer（flow.token），但未找到该变量来源`,
      '按项目鉴权 但无 code 前缀应忽略',
    ])).toEqual([
      'HTTP 节点「资料」已按项目鉴权补全 Authorization（托管头，Run 时随项目配置刷新）',
      '图中使用了客户端 Bearer（flow.token），但未找到该变量来源',
    ]);
  });

  it('parseAuthWarning 只认已知 code', () => {
    // 前提：合法 AUTH_HEADER_MANAGED 与未知 code
    // 期望：前者解析成功，后者 null
    expect(parseAuthWarning(`${AUTH_WARNING_CODES.HEADER_MANAGED}: 已补全`)).toEqual({
      code: AUTH_WARNING_CODES.HEADER_MANAGED,
      message: '已补全',
    });
    expect(parseAuthWarning('UNKNOWN: x')).toBeNull();
    expect(parseAuthWarning('无分隔符')).toBeNull();
  });
});
