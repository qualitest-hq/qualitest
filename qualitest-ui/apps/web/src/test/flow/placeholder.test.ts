/**
 * 测 resolvePlaceholderString / resolvePathSegment：{{scope.path}} 占位符解析。
 * 边界：纯函数；用例来自 @flow-fixtures/placeholder-cases.json（与后端同源）。
 * 单跑：yarn test placeholder   （在 qualitest-ui 或 apps/web 下）
 * @vitest-environment happy-dom
 */
import { describe, expect, it } from 'vitest';

import { resolvePathSegment, resolvePlaceholderString } from '@/utils/flow/placeholder';
import { PlaceholderUndefinedError } from '@/utils/flow/types';
import type { FlowRunContext } from '@/utils/flow/types';

import fixture from '@flow-fixtures/placeholder-cases.json';

/** fixture 中的 mockContext，结构与运行时 FlowRunContext 一致，各用例只读共享 */
const ctx = fixture.mockContext as FlowRunContext;

function caseById(id: string) {
  const c = fixture.cases.find((x) => x.id === id);
  if (!c) throw new Error(`missing placeholder case: ${id}`);
  return c;
}

describe('resolvePlaceholderString', () => {
  it('env.baseUrl 占位符解析为环境变量值（lenient）', () => {
    // 前提：模板 {{env.baseUrl}}/api，lenient 模式
    // 期望：解析为 https://dev.example.com/api
    const c = caseById('env-base-url');
    expect(resolvePlaceholderString(c.template, ctx, 'lenient')).toBe(c.expected);
  });

  it('env.baseUrl 占位符解析为环境变量值（strict）', () => {
    // 前提：模板 {{env.baseUrl}}/api，strict 模式
    // 期望：解析为 https://dev.example.com/api
    const c = caseById('env-base-url');
    expect(resolvePlaceholderString(c.template, ctx, 'strict')).toBe(c.expected);
  });

  it('flow.token 占位符解析为 flow 变量值（lenient）', () => {
    // 前提：模板 {{flow.token}}
    // 期望：解析为 abc
    const c = caseById('flow-token');
    expect(resolvePlaceholderString(c.template, ctx, 'lenient')).toBe(c.expected);
  });

  it('flow.token 占位符解析为 flow 变量值（strict）', () => {
    // 前提：模板 {{flow.token}}，strict 模式
    // 期望：解析为 abc
    const c = caseById('flow-token');
    expect(resolvePlaceholderString(c.template, ctx, 'strict')).toBe(c.expected);
  });

  it('asset 标量字段占位符解析（lenient）', () => {
    // 前提：模板 {{asset.defaults.apiKey}}
    // 期望：解析为 sk-demo
    const c = caseById('asset-scalar');
    expect(resolvePlaceholderString(c.template, ctx, 'lenient')).toBe(c.expected);
  });

  it('asset 标量字段占位符解析（strict）', () => {
    // 前提：模板 {{asset.defaults.apiKey}}，strict 模式
    // 期望：解析为 sk-demo
    const c = caseById('asset-scalar');
    expect(resolvePlaceholderString(c.template, ctx, 'strict')).toBe(c.expected);
  });

  it('asset 嵌套字段占位符解析（lenient）', () => {
    // 前提：模板 {{asset.defaults.nested.x}}
    // 期望：解析为 1
    const c = caseById('asset-nested');
    expect(resolvePlaceholderString(c.template, ctx, 'lenient')).toBe(c.expected);
  });

  it('asset 嵌套字段占位符解析（strict）', () => {
    // 前提：模板 {{asset.defaults.nested.x}}，strict 模式
    // 期望：解析为 1
    const c = caseById('asset-nested');
    expect(resolvePlaceholderString(c.template, ctx, 'strict')).toBe(c.expected);
  });

  it('asset 多层嵌套字段占位符解析（lenient）', () => {
    // 前提：模板 {{asset.reporter_01.account.password}}
    // 期望：解析为 secret
    const c = caseById('asset-deep');
    expect(resolvePlaceholderString(c.template, ctx, 'lenient')).toBe(c.expected);
  });

  it('asset 多层嵌套字段占位符解析（strict）', () => {
    // 前提：模板 {{asset.reporter_01.account.password}}，strict 模式
    // 期望：解析为 secret
    const c = caseById('asset-deep');
    expect(resolvePlaceholderString(c.template, ctx, 'strict')).toBe(c.expected);
  });

  it('占位符与普通文本混排时正确拼接（lenient）', () => {
    // 前提：模板 Bearer {{flow.token}}!
    // 期望：解析为 Bearer abc!
    const c = caseById('mixed-template');
    expect(resolvePlaceholderString(c.template, ctx, 'lenient')).toBe(c.expected);
  });

  it('占位符与普通文本混排时正确拼接（strict）', () => {
    // 前提：模板 Bearer {{flow.token}}!，strict 模式
    // 期望：解析为 Bearer abc!
    const c = caseById('mixed-template');
    expect(resolvePlaceholderString(c.template, ctx, 'strict')).toBe(c.expected);
  });

  it('http.body.* 按路径从上一步响应体取值（lenient）', () => {
    // 前提：模板 {{http.body.data.code}}
    // 期望：解析为 0
    const c = caseById('http-body-code');
    expect(resolvePlaceholderString(c.template, ctx, 'lenient')).toBe(c.expected);
  });

  it('http.body.* 按路径从上一步响应体取值（strict）', () => {
    // 前提：模板 {{http.body.data.code}}，strict 模式
    // 期望：解析为 0
    const c = caseById('http-body-code');
    expect(resolvePlaceholderString(c.template, ctx, 'strict')).toBe(c.expected);
  });

  it('http.status 取上一步响应状态码（lenient）', () => {
    // 前提：模板 {{http.status}}
    // 期望：解析为 200
    const c = caseById('http-status');
    expect(resolvePlaceholderString(c.template, ctx, 'lenient')).toBe(c.expected);
  });

  it('http.status 取上一步响应状态码（strict）', () => {
    // 前提：模板 {{http.status}}，strict 模式
    // 期望：解析为 200
    const c = caseById('http-status');
    expect(resolvePlaceholderString(c.template, ctx, 'strict')).toBe(c.expected);
  });

  it('http.duration 取上一步请求耗时（lenient）', () => {
    // 前提：模板 {{http.duration}}
    // 期望：解析为 120
    const c = caseById('http-duration');
    expect(resolvePlaceholderString(c.template, ctx, 'lenient')).toBe(c.expected);
  });

  it('http.duration 取上一步请求耗时（strict）', () => {
    // 前提：模板 {{http.duration}}，strict 模式
    // 期望：解析为 120
    const c = caseById('http-duration');
    expect(resolvePlaceholderString(c.template, ctx, 'strict')).toBe(c.expected);
  });

  it('http.header.* 取上一步响应头（lenient）', () => {
    // 前提：模板 {{http.header.Authorization}}
    // 期望：解析为 Bearer body-token
    const c = caseById('http-header');
    expect(resolvePlaceholderString(c.template, ctx, 'lenient')).toBe(c.expected);
  });

  it('http.header.* 取上一步响应头（strict）', () => {
    // 前提：模板 {{http.header.Authorization}}，strict 模式
    // 期望：解析为 Bearer body-token
    const c = caseById('http-header');
    expect(resolvePlaceholderString(c.template, ctx, 'strict')).toBe(c.expected);
  });

  it('模板为 null 时返回空串（lenient）', () => {
    // 前提：template 为 null
    // 期望：返回空串
    const c = caseById('null-template');
    expect(resolvePlaceholderString(c.template, ctx, 'lenient')).toBe(c.expected);
  });

  it('模板为 null 时返回空串（strict）', () => {
    // 前提：template 为 null，strict 模式
    // 期望：返回空串
    const c = caseById('null-template');
    expect(resolvePlaceholderString(c.template, ctx, 'strict')).toBe(c.expected);
  });

  it('未定义占位符替换为空串（lenient）', () => {
    // 前提：模板 x{{flow.missing}}y，lenient 模式
    // 期望：解析为 xy
    const c = caseById('missing-lenient');
    expect(resolvePlaceholderString(c.template, ctx, 'lenient')).toBe(c.expected);
  });

  it('未定义占位符抛出异常（strict）', () => {
    // 前提：模板 {{flow.missing}}，strict 模式
    // 期望：抛出 PlaceholderUndefinedError
    const c = caseById('missing-strict');
    expect(() => resolvePlaceholderString(c.template, ctx, 'strict')).toThrow(
      PlaceholderUndefinedError,
    );
    try {
      resolvePlaceholderString(c.template, ctx, 'strict');
    } catch (e) {
      expect((e as PlaceholderUndefinedError).code).toBe(c.errorCode);
    }
  });

  it('混合文本中未定义占位符仍抛出异常（strict）', () => {
    // 前提：模板 Bearer {{flow.missing}}，strict 模式
    // 期望：抛出 PlaceholderUndefinedError
    const c = caseById('missing-strict-partial');
    expect(() => resolvePlaceholderString(c.template, ctx, 'strict')).toThrow(
      PlaceholderUndefinedError,
    );
    try {
      resolvePlaceholderString(c.template, ctx, 'strict');
    } catch (e) {
      expect((e as PlaceholderUndefinedError).code).toBe(c.errorCode);
    }
  });

  /** http.duration：上一步 HTTP 耗时（毫秒），fixture lastResponse.durationMs = 120 */
  it('按路径解析 http.duration（上一步请求耗时）', () => {
    // 前提：路径 http.duration，ctx 含 lastResponse.durationMs=120
    // 期望：返回 120
    const actual = resolvePathSegment(ctx, 'http.duration');
    expect(actual).toBe(120);
  });

  /** http.body.*：从上一步响应 Body 按点路径取值，fixture body.data.code = 0 */
  it('按路径解析 http.body.data.code（上一步响应体嵌套字段）', () => {
    // 前提：路径 http.body.data.code
    // 期望：返回 0
    const actual = resolvePathSegment(ctx, 'http.body.data.code');
    expect(actual).toBe(0);
  });

  /** strict 模式下异常应携带占位符名 flow.missing，便于前端/日志定位 */
  it('strict 模式下异常携带未定义占位符名称，便于定位', () => {
    // 前提：模板 {{flow.missing}}，strict 模式
    // 期望：异常 code 为 TF_PLACEHOLDER_UNDEFINED，placeholder 为 flow.missing
    expect(() => resolvePlaceholderString('{{flow.missing}}', ctx, 'strict')).toThrow(
      PlaceholderUndefinedError,
    );
    try {
      resolvePlaceholderString('{{flow.missing}}', ctx, 'strict');
    } catch (e) {
      const err = e as PlaceholderUndefinedError;
      expect(err.code).toBe('TF_PLACEHOLDER_UNDEFINED');
      expect(err.placeholder).toBe('flow.missing');
    }
  });
});
