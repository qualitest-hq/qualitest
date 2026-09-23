/**
 * 占位符与运行时路径解析。
 *
 * 模板：{{scope.path}}
 * 持久变量：env.* / flow.* / asset.*
 * 上一步 HTTP：http.body（整段）/ http.body.<相对 JsonPath>、http.status、http.duration、http.header.*
 * body 上的路径支持下标、过滤器、通配、length()。
 * lenient：未定义占位符 → 空串；strict：未定义 → 抛错。
 */
import type { FlowRunContext, PlaceholderResolveMode } from './types';
import { PlaceholderUndefinedError } from './types';
import { JSONPath } from 'jsonpath-plus';
import { replaceMustache } from './mustacheScan';

/**
 * 把以 $ 开头的断言左值改成带 scope 的写法。
 * $ → http.body；$ .data.x → http.body.data.x；$[0] → http.body[0]。
 */
export function normalizeAssertLeftPath(path: string): string {
  const p = String(path ?? '').trim();
  if (!p) return '';
  if (p === '$') return 'http.body';
  if (p.startsWith('$.')) return 'http.body.' + p.slice(2);
  if (p.startsWith('$[')) return 'http.body' + p.slice(1);
  return p;
}

/**
 * 单段路径求值：占位符内部、断言左值、条件左值共用。
 * 纯 $… 会先改成 http.body… 再解析。
 */
export function resolvePathSegment(ctx: FlowRunContext, path: string): unknown {
  const p = normalizeAssertLeftPath(String(path ?? '').trim());
  if (!p) return undefined;

  if (p.startsWith('flow.')) {
    return ctx.flow[p.slice(5)];
  }
  if (p.startsWith('env.')) {
    return ctx.env[p.slice(4)];
  }
  if (p.startsWith('asset.')) {
    return resolveAssetPath(ctx.asset, p.slice(6));
  }
  if (p.startsWith('http.')) {
    return resolveHttpPath(ctx, p.slice(5));
  }
  return undefined;
}

/** 解析 http.*：整 body、body JsonPath、状态码、耗时、响应头。http.body.$.… 返回 undefined。 */
function resolveHttpPath(ctx: FlowRunContext, rest: string): unknown {
  if (!rest) return undefined;
  const last = ctx.lastResponse;
  if (rest === 'duration') {
    return last?.durationMs ?? undefined;
  }
  if (rest === 'status') {
    return last?.status ?? undefined;
  }
  if (rest === 'body') {
    return last?.body ?? undefined;
  }
  if (rest.startsWith('body.')) {
    const body = ctx.lastResponse?.body;
    if (body == null) return undefined;
    const relative = rest.slice(5);
    if (relative.startsWith('$')) return undefined;
    return evalJsonPath(body, toAbsoluteJsonPath(relative));
  }
  if (rest.startsWith('header.')) {
    return resolveHttpHeader(ctx, rest.slice(7));
  }
  if (rest.startsWith('headers.')) {
    return resolveHttpHeader(ctx, rest.slice(8));
  }
  return undefined;
}

function resolveHttpHeader(ctx: FlowRunContext, name: string): unknown {
  if (!name) return undefined;
  return ctx.lastResponse?.headers?.[name];
}

/** asset.key 或 asset.key.a.b：先取条目，再按点分键下钻。 */
function resolveAssetPath(asset: Record<string, unknown>, rest: string): unknown {
  if (!rest) return undefined;
  const dot = rest.indexOf('.');
  const key = dot >= 0 ? rest.slice(0, dot) : rest;
  const sub = dot >= 0 ? rest.slice(dot + 1) : '';
  let val = asset[key];
  if (sub && val != null) {
    for (const part of sub.split('.')) {
      if (val == null || typeof val !== 'object') return undefined;
      val = (val as Record<string, unknown>)[part];
    }
  }
  return val;
}

/**
 * 把 http.body 后面的相对路径补成绝对 JsonPath。
 * 例：data.items[0] → $.data.items[0]；空 → $。
 */
export function toAbsoluteJsonPath(relative: string): string {
  const r = String(relative ?? '').trim();
  if (!r) return '$';
  if (r.startsWith('$')) return r;
  if (r.startsWith('[')) return '$' + r;
  return '$.' + r;
}

/** 含过滤器 / 通配 / 切片时视为不定路径，结果保持数组。 */
function isIndefiniteJsonPath(path: string): boolean {
  return /\.\.|\[\s*\?\(|\[\s*\*|\[\s*\d*\s*:\s*\d*/.test(path);
}

/**
 * 对 body 求 JsonPath。
 * 须以 $ 开头；路径不存在或非法 → undefined；不定路径空匹配 → []。
 * 路径以 .length() 结尾时，对父路径结果取长度（数组/字符串/对象键数）。
 */
export function evalJsonPath(body: unknown, expr: string): unknown {
  if (body == null || expr == null) return undefined;
  const path = String(expr).trim();
  if (!path || path[0] !== '$') return undefined;
  try {
    if (path === '$') return body;
    const lengthMatch = /^(.*)\.length\(\)$/.exec(path);
    if (lengthMatch) {
      const parent = evalJsonPath(body, lengthMatch[1]);
      if (Array.isArray(parent)) return parent.length;
      if (typeof parent === 'string') return parent.length;
      if (parent && typeof parent === 'object') return Object.keys(parent as object).length;
      return undefined;
    }
    const result = JSONPath({ path, json: body as object, wrap: true });
    if (!Array.isArray(result)) return result;
    if (result.length === 0) {
      return isIndefiniteJsonPath(path) ? [] : undefined;
    }
    if (result.length === 1 && !isIndefiniteJsonPath(path)) {
      return result[0];
    }
    return result;
  } catch {
    return undefined;
  }
}

/**
 * 检查 JsonPath 是否可用：须以 $ 开头、括号成对，且能在空对象上试算。
 * 用于保存前校验与设计态试算。
 */
export function isValidJsonPath(expr: string): boolean {
  const path = String(expr ?? '').trim();
  if (!path || path[0] !== '$') return false;
  if (!hasBalancedBrackets(path)) return false;
  try {
    JSONPath({ path, json: {}, wrap: true });
    return true;
  } catch {
    return false;
  }
}

/** [] / () 是否成对且无越界闭合。 */
function hasBalancedBrackets(path: string): boolean {
  let square = 0;
  let paren = 0;
  for (const c of path) {
    if (c === '[') square++;
    else if (c === ']') square--;
    else if (c === '(') paren++;
    else if (c === ')') paren--;
    if (square < 0 || paren < 0) return false;
  }
  return square === 0 && paren === 0;
}

/** 对响应 body 求 JsonPath（extracts[].expr 等）。 */
export function simpleJsonPath(body: unknown, expr: string): unknown {
  return evalJsonPath(body, expr);
}

/**
 * 替换模板中全部合法路径占位 {{flow.|env.|asset.|http.…}}。
 * 非路径形态的花括号保留为字面量；未定义路径在 lenient 下变空串，strict 下抛错。
 * @param mode lenient=未定义→空串；strict=未定义→抛错
 */
export function resolvePlaceholderString(
  template: string | null | undefined,
  ctx: FlowRunContext,
  mode: PlaceholderResolveMode = 'lenient',
): string {
  if (template == null) return '';
  return replaceMustache(String(template), (inner) => {
    const key = inner.trim();
    const value = resolvePathSegment(ctx, key);
    if (value == null) {
      if (mode === 'strict') {
        throw new PlaceholderUndefinedError(key);
      }
      return '';
    }
    return String(value);
  });
}

/** 宽松模式：未定义占位符 → 空串。 */
export function resolvePlaceholder(template: string | null | undefined, ctx: FlowRunContext): string {
  return resolvePlaceholderString(template, ctx, 'lenient');
}
