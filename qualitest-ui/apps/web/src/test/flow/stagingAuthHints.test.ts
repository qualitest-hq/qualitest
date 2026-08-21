/**
 * 测 stagingAuthHints：托管头识别、Diff 标签、鉴权机器码解析与展示剥前缀。
 * 边界：无 headers / 显式头 / profileManaged；只认 AUTH_* CODE；TOKEN_MISSING 不进 soft warnings。
 * 单跑：pnpm test stagingAuthHints   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest';

import {
  AUTH_WARNING_CODES,
  collectAuthManagedHeaderHints,
  displayAuthCodedMessage,
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
    expect(collectAuthManagedHeaderHints(draft)[0]).toContain('flow.token');
  });

  it('按 AUTH_* 筛 soft 提示，并剥掉 CODE 前缀', () => {
    // 前提：混有普通文案、HEADER_MANAGED、TOKEN_MISSING
    // 期望：只留下 HEADER_MANAGED 的人类文案；TOKEN_MISSING 不出现在 soft 列表
    expect(filterAuthRelatedWarnings([
      '边缺少 source',
      `${AUTH_WARNING_CODES.HEADER_MANAGED}: HTTP 节点「资料」已按项目鉴权补全 Authorization（托管头，Run 时随项目配置刷新）`,
      `${AUTH_WARNING_CODES.TOKEN_MISSING}: 图中使用了客户端 Bearer（flow.token），但未找到该变量来源`,
      '按项目鉴权 但无 code 前缀应忽略',
    ])).toEqual([
      'HTTP 节点「资料」已按项目鉴权补全 Authorization（托管头，Run 时随项目配置刷新）',
    ]);
  });

  it('displayAuthCodedMessage 去掉鉴权 CODE 前缀', () => {
    // 前提：AUTH_TOKEN_MISSING 完整串
    // 期望：只返回冒号后的可读文案；普通错误原样返回
    expect(displayAuthCodedMessage(
      `${AUTH_WARNING_CODES.TOKEN_MISSING}: 图中使用了客户端 Bearer（flow.token），但未找到该变量来源`,
    )).toBe('图中使用了客户端 Bearer（flow.token），但未找到该变量来源');
    expect(displayAuthCodedMessage('普通错误')).toBe('普通错误');
  });

  it('parseAuthWarning 只认已知 CODE', () => {
    // 前提：合法 HEADER_MANAGED 与未知 CODE
    // 期望：前者解析成功，后者 null
    expect(parseAuthWarning(`${AUTH_WARNING_CODES.HEADER_MANAGED}: 已补全`)).toEqual({
      code: AUTH_WARNING_CODES.HEADER_MANAGED,
      message: '已补全',
    });
    expect(parseAuthWarning('UNKNOWN: x')).toBeNull();
    expect(parseAuthWarning('无分隔符')).toBeNull();
    expect(parseAuthWarning(`${AUTH_WARNING_CODES.LOGIN_FLOWKEY_COLLISION}: 两端都抽 token`)).toEqual({
      code: AUTH_WARNING_CODES.LOGIN_FLOWKEY_COLLISION,
      message: '两端都抽 token',
    });
  });
});
