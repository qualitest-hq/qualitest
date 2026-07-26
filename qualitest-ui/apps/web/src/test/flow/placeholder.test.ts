/**
 * resolvePlaceholderString / resolvePathSegment 单元测试：验证 {{scope.path}} 占位符解析。
 *
 * 两种模式：lenient（设计态，未定义占位符 → 空串）与 strict（正式 Run，未定义 → 抛 PlaceholderUndefinedError）。
 * 支持 env / flow / asset / http.* 快照路径、嵌套路径、混合文本。
 *
 * 数据驱动：用例来自 fixtures/placeholder-cases.json，
 * 与后端 qualitest-system/.../placeholder-cases.json 共享，保证 Java 与 TS 解析一致。
 *
 * 运行（apps/web 目录）：yarn test placeholder
 */
import { describe, expect, it } from 'vitest';

import { resolvePathSegment, resolvePlaceholderString } from '@/utils/flow/placeholder';
import { PlaceholderUndefinedError } from '@/utils/flow/types';
import type { FlowRunContext } from '@/utils/flow/types';

import fixture from './fixtures/placeholder-cases.json';

/** fixture 中的 mockContext，结构与运行时 FlowRunContext 一致，各用例只读共享 */
const ctx = fixture.mockContext as FlowRunContext;

function quote(value: unknown): string {
  if (value == null) return 'null';
  return JSON.stringify(String(value));
}

function logCase(mode: string, id: string, template: unknown, result: unknown) {
  // eslint-disable-next-line no-console
  console.log(`  OK [${mode}] ${id.padEnd(24)}  ${quote(template)}  ->  ${quote(result)}`);
}

describe('resolvePlaceholderString', () => {
  // eslint-disable-next-line no-console
  console.log(`\n=== resolvePlaceholderString fixture cases (${fixture.cases.length}) ===`);

  /** 数据驱动：按 mode 分别断言 lenient / strict 行为；strict 未定义占位符抛 errorCode */
  for (const c of fixture.cases) {
    if (c.mode === 'both' || c.mode === 'lenient') {
      it(`[lenient] ${c.id}`, () => {
        const actual = resolvePlaceholderString(c.template, ctx, 'lenient');
        logCase('lenient', c.id, c.template, actual);
        expect(actual).toBe(c.expected);
      });
    }
    if (c.mode === 'both' || c.mode === 'strict') {
      it(`[strict] ${c.id}`, () => {
        if (c.errorCode) {
          expect(() => resolvePlaceholderString(c.template, ctx, 'strict')).toThrow(
            PlaceholderUndefinedError,
          );
          try {
            resolvePlaceholderString(c.template, ctx, 'strict');
          } catch (e) {
            logCase('strict', c.id, c.template, `throw ${(e as PlaceholderUndefinedError).code}`);
            expect((e as PlaceholderUndefinedError).code).toBe(c.errorCode);
          }
        } else {
          const actual = resolvePlaceholderString(c.template, ctx, 'strict');
          logCase('strict', c.id, c.template, actual);
          expect(actual).toBe(c.expected);
        }
      });
    }
  }

  /** http.duration：上一步 HTTP 耗时（毫秒），fixture lastResponse.durationMs = 120 */
  it('resolvePathSegment http.duration', () => {
    const actual = resolvePathSegment(ctx, 'http.duration');
    logCase('path', 'http.duration', 'http.duration', actual);
    expect(actual).toBe(120);
  });

  /** http.body.*：从上一步响应 Body 按点路径取值，fixture body.data.code = 0 */
  it('resolvePathSegment http.body', () => {
    const actual = resolvePathSegment(ctx, 'http.body.data.code');
    logCase('path', 'http.body', 'http.body.data.code', actual);
    expect(actual).toBe(0);
  });

  /** strict 模式下异常应携带占位符名 flow.missing，便于前端/日志定位 */
  it('[strict] throwsWithPlaceholderName', () => {
    expect(() => resolvePlaceholderString('{{flow.missing}}', ctx, 'strict')).toThrow(
      PlaceholderUndefinedError,
    );
    try {
      resolvePlaceholderString('{{flow.missing}}', ctx, 'strict');
    } catch (e) {
      const err = e as PlaceholderUndefinedError;
      logCase('strict', 'throwsWithPlaceholderName', '{{flow.missing}}', `throw ${err.code}`);
      expect(err.code).toBe('TF_PLACEHOLDER_UNDEFINED');
      expect(err.placeholder).toBe('flow.missing');
    }
  });
});
