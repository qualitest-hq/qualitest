/**
 * 从正式 Run 步骤取最近一次 HTTP 响应 body，并生成 JsonPath 试算文案。
 */
import {
  evalJsonPath,
  isValidJsonPath,
  normalizeAssertLeftPath,
  toAbsoluteJsonPath,
} from '@/utils/flow/placeholder';

import type { RunRecord, RunStepDetail } from '../stores/runLibraryStore';

/** 取出某步 http.response.body；没有则 undefined。 */
export function extractHttpResponseBody(step: RunStepDetail | null | undefined): unknown {
  if (!step?.http?.response) return undefined;
  const body = step.http.response.body;
  return body === undefined ? undefined : body;
}

/** 从后往前找最近一条带响应 body 的 HTTP 步。 */
export function findLatestHttpBody(steps: RunStepDetail[] | undefined | null): unknown {
  if (!steps?.length) return undefined;
  for (let i = steps.length - 1; i >= 0; i--) {
    const body = extractHttpResponseBody(steps[i]);
    if (body !== undefined) return body;
  }
  return undefined;
}

/** 当前选中 Run 的最近 HTTP 响应 body。 */
export function trialBodyFromSelectedRun(run: RunRecord | null | undefined): unknown {
  return findLatestHttpBody(run?.steps);
}

/** 把试算结果格式化成展示文案。 */
export function formatTrialResult(value: unknown): string {
  if (value === undefined) return '试算：undefined（路径未命中）';
  try {
    return `试算：${JSON.stringify(value)}`;
  } catch {
    return `试算：${String(value)}`;
  }
}

/**
 * extracts body 表达式试算。
 * 无 body / 空 expr → 空串；须以 $ 开头且路径可解析。
 */
export function previewExtractExpr(trialBody: unknown, expr: string): string {
  if (trialBody === undefined || trialBody === null) return '';
  const path = String(expr ?? '').trim();
  if (!path) return '';
  if (!path.startsWith('$')) return '试算失败：body 表达式须以 $ 开头';
  if (!isValidJsonPath(path)) return `试算失败：JsonPath 无法解析 ${path}`;
  return formatTrialResult(evalJsonPath(trialBody, path));
}

/**
 * 断言左值试算（仅 http.body / $ 方言有结果）。
 * 禁止 http.body.$.…；其它 scope 返回空串。
 */
export function previewAssertLeft(trialBody: unknown, left: string): string {
  if (trialBody === undefined || trialBody === null) return '';
  const raw = String(left ?? '').trim();
  if (!raw) return '';
  const normalized = normalizeAssertLeftPath(raw);
  if (normalized === 'http.body') {
    return formatTrialResult(trialBody);
  }
  if (!normalized.startsWith('http.body.')) {
    return '';
  }
  const relative = normalized.slice('http.body.'.length);
  if (relative.startsWith('$')) {
    return '试算失败：不可写成 http.body.$.…';
  }
  const abs = toAbsoluteJsonPath(relative);
  if (!isValidJsonPath(abs)) {
    return `试算失败：JsonPath 无法解析 ${abs}`;
  }
  return formatTrialResult(evalJsonPath(trialBody, abs));
}
