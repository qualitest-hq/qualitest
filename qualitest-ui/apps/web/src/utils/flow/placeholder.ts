import type { FlowRunContext, PlaceholderResolveMode } from './types';
import { PlaceholderUndefinedError } from './types';

const PLACEHOLDER_RE = /\{\{([^}]+)\}\}/g;

/**
 * 单段路径求值：占位符 inner、断言左值、条件左值共用此函数。
 *
 * 持久 scope：env.* / flow.* / asset.*
 * 上一步 HTTP 快照：http.body.* / http.status / http.duration / http.header.*
 */
export function resolvePathSegment(ctx: FlowRunContext, path: string): unknown {
  const p = String(path ?? '').trim();
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

/** http.* 路径：body / status / duration / header(s) */
function resolveHttpPath(ctx: FlowRunContext, rest: string): unknown {
  if (!rest) return undefined;
  const last = ctx.lastResponse;
  if (rest === 'duration') {
    return last?.durationMs ?? undefined;
  }
  if (rest === 'status') {
    return last?.status ?? undefined;
  }
  if (rest.startsWith('body.')) {
    const body = ctx.lastResponse?.body;
    if (body == null) return undefined;
    return navigate(body, rest.slice(5));
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

function navigate(root: unknown, dotPath: string): unknown {
  if (root == null || !dotPath) return root;
  let cur: unknown = root;
  for (const part of dotPath.split('.')) {
    if (cur == null || typeof cur !== 'object') return undefined;
    cur = (cur as Record<string, unknown>)[part];
  }
  return cur;
}

/** 简化 JsonPath：$.a.b.c（用于 HTTP 响应提取 extracts[].expr） */
export function simpleJsonPath(body: unknown, expr: string): unknown {
  if (!expr?.startsWith('$.')) return undefined;
  const path = expr.slice(2);
  if (!path) return body;
  return navigate(body, path);
}

/**
 * 替换模板中全部 {{…}} 占位符
 * @param mode lenient=未定义占位符→空串；strict=未定义→抛 PlaceholderUndefinedError
 */
export function resolvePlaceholderString(
  template: string | null | undefined,
  ctx: FlowRunContext,
  mode: PlaceholderResolveMode = 'lenient',
): string {
  if (template == null) return '';
  return String(template).replace(PLACEHOLDER_RE, (_, inner: string) => {
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

/** lenient 模式：未定义占位符 → 空串 */
export function resolvePlaceholder(template: string | null | undefined, ctx: FlowRunContext): string {
  return resolvePlaceholderString(template, ctx, 'lenient');
}
