/**
 * HTTP 响应提取：从 body/header/status/setCookie 取值并写入 flow / env / asset。
 * http 节点 data.extracts[] 的配置与执行均经此模块。
 */
import type { FlowRunContext, HttpResponseSnapshot } from './types';
import { simpleJsonPath } from './placeholder';

/** 单条提取配置（对应 http 节点 data.extracts[] 元素） */
export interface ExtractTarget {
  /** 提取来源：body | header | status | setCookie | regex（regex 暂未实现） */
  from?: string;
  /** body 时为 $. 开头的 JsonPath；header 时为头名称；setCookie 时为 Cookie 名 */
  expr?: string;
  /** 写入作用域：flow | env | asset */
  scope?: string;
  /** flow/env 下的变量名 */
  name?: string;
  /** scope=asset 时的素材 key */
  entryKey?: string;
  /** scope=asset 时写入素材条目内的字段名 */
  fieldPath?: string;
}

/** applyExtracts 返回的已应用项（步骤报告、UI 展示用） */
export interface AppliedExtract {
  name: string;
  scope: string;
  value: unknown;
}

/** 提取目标作用域选项（属性面板下拉） */
export const EXTRACT_SCOPES = [
  { value: 'flow', label: '流程运行', hint: '{{flow.name}}' },
  { value: 'env', label: '环境变量', hint: '{{env.key.field}}' },
  { value: 'asset', label: '项目素材', hint: '{{asset.key.field}}' },
] as const;

/** 提取来源选项（属性面板下拉） */
export const EXTRACT_FROM_OPTIONS = [
  { value: 'body', label: 'Body · JsonPath' },
  { value: 'header', label: 'Header' },
  { value: 'setCookie', label: 'Set-Cookie' },
  { value: 'regex', label: 'Regex' },
  { value: 'status', label: 'Status 状态码' },
] as const;

/** 空白提取行模板 */
export function emptyExtractTarget(): ExtractTarget {
  return { from: 'body', expr: '', scope: 'flow', name: '', entryKey: '', fieldPath: '' };
}

/** 补全缺省字段 */
export function normalizeExtractTargetsArray(
  targets: Array<Record<string, unknown>>,
): ExtractTarget[] {
  return targets.map((t) => {
    const row: ExtractTarget = {
      from: 'body',
      expr: '',
      scope: 'flow',
      name: '',
      entryKey: '',
      fieldPath: '',
      ...t,
    };
    if (!row.scope) row.scope = 'flow';
    return row;
  });
}

/** 判断提取行是否具备执行所需的最小字段 */
export function isFilledExtractTarget(t: ExtractTarget | Record<string, unknown>): boolean {
  if (!String(t.expr ?? '').trim()) return false;
  const scope = String(t.scope ?? 'flow');
  if (scope === 'flow') return !!String(t.name ?? '').trim();
  return !!String(t.entryKey ?? '').trim();
}

/** 过滤掉未填完整的提取行 */
export function filterFilledExtracts(arr: ExtractTarget[]): ExtractTarget[] {
  return arr.filter(isFilledExtractTarget);
}

/** 按名称取响应头，大小写不敏感 */
function resolveHeaderValue(
  headers: Record<string, unknown> | undefined,
  expr: string,
): unknown {
  if (!headers || !expr) return undefined;
  const direct = headers[expr];
  if (direct !== undefined) return direct;
  const lower = String(expr).toLowerCase();
  for (const [k, v] of Object.entries(headers)) {
    if (k.toLowerCase() === lower) return v;
  }
  return undefined;
}

/** 从 Set-Cookie 按 Cookie 名取值（忽略 Path/HttpOnly 等属性） */
function resolveSetCookieValue(
  headers: Record<string, unknown> | undefined,
  cookieName: string,
): unknown {
  if (!headers || !cookieName) return undefined;
  for (const [k, v] of Object.entries(headers)) {
    if (!k || k.toLowerCase() !== 'set-cookie') continue;
    const raws = Array.isArray(v) ? v : [v];
    for (const raw of raws) {
      if (raw == null) continue;
      const first = String(raw).split(';', 2)[0].trim();
      const eq = first.indexOf('=');
      if (eq <= 0) continue;
      const name = first.slice(0, eq).trim();
      if (name === cookieName) {
        return first.slice(eq + 1).trim();
      }
    }
  }
  return undefined;
}

/** 按 scope 将提取值写入 ctx；未取到值时存 null */
function writeExtractValue(
  ctx: FlowRunContext,
  ex: ExtractTarget,
  val: unknown,
): void {
  const scope = ex.scope || 'flow';
  const name = ex.name;
  if (!name && scope === 'flow') return;

  const stored = val ?? null;
  if (scope === 'flow') {
    ctx.flow[name!] = stored;
    return;
  }
  if (scope === 'env') {
    ctx.env[name!] = stored;
    return;
  }
  if (scope === 'asset' && ex.entryKey) {
    if (!ctx.asset[ex.entryKey]) ctx.asset[ex.entryKey] = {};
    const root = ctx.asset[ex.entryKey] as Record<string, unknown>;
    const fp = ex.fieldPath || name || '';
    root[fp] = stored;
  }
}

/**
 * 执行提取列表：从 response 取值并写入 ctx，返回本步实际应用项。
 * 调用方通常在 http 步骤成功后更新 ctx.lastResponse 再调用本函数。
 */
export function applyExtracts(
  extracts: ExtractTarget[] | null | undefined,
  ctx: FlowRunContext,
  response: HttpResponseSnapshot,
): AppliedExtract[] {
  const applied: AppliedExtract[] = [];
  for (const ex of extracts ?? []) {
    let val: unknown;
    if (ex.from === 'body') {
      val = simpleJsonPath(response.body, ex.expr ?? '');
    } else if (ex.from === 'header') {
      val = resolveHeaderValue(response.headers, ex.expr ?? '');
    } else if (ex.from === 'setCookie') {
      val = resolveSetCookieValue(response.headers, ex.expr ?? '');
    } else if (ex.from === 'status') {
      val = response.status;
    } else {
      val = undefined;
    }

    const scope = ex.scope || 'flow';
    const name = ex.name ?? '';
    if (!name && scope === 'flow') continue;

    writeExtractValue(ctx, ex, val);
    applied.push({ name: name || ex.entryKey || '', scope, value: val ?? null });
  }
  return applied;
}
