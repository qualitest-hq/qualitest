/**
 * 测试流属性面板的 JsonPath 试算工具。
 *
 * 职责：
 * 1. 从选中 Run 的步骤里取出最近一次 HTTP 响应 body
 * 2. 从接口 responseConfig 取出首个响应 example，供尚未 Run 时软试算展示
 * 3. 按画布边回溯上游 project HTTP，解析试算用的 testProjectApiId
 * 4. 把断言左值 / 提取表达式格式化成「试算：…」展示文案，并判断是否未命中
 * 5. 保存前路径门禁：按响应 schema 校验（example 不参与硬拦）
 */
import type { Edge, Node } from '@vue-flow/core';

import {
  evalJsonPath,
  isValidJsonPath,
  normalizeAssertLeftPath,
  toAbsoluteJsonPath,
} from '@/utils/flow/placeholder';
import { parseResponseConfigInput } from '@/views/project/testProject/utils/responseConfig';

import type { RunRecord, RunStepDetail } from '../stores/runLibraryStore';

/** 当前试算 body 从哪来：正式 Run 响应，或接口上配置的响应示例 */
export type TrialBodySource = 'run' | 'api-example';

/** 取单步 HTTP 响应 body；该步无响应时返回 undefined */
export function extractHttpResponseBody(step: RunStepDetail | null | undefined): unknown {
  if (!step?.http?.response) return undefined;
  const body = step.http.response.body;
  return body === undefined ? undefined : body;
}

/** 从步骤列表末尾往前找，返回最近一条有 body 的 HTTP 响应 */
export function findLatestHttpBody(steps: RunStepDetail[] | undefined | null): unknown {
  if (!steps?.length) return undefined;
  for (let i = steps.length - 1; i >= 0; i--) {
    const body = extractHttpResponseBody(steps[i]);
    if (body !== undefined) return body;
  }
  return undefined;
}

/** 当前选中 Run 的最近 HTTP 响应 body，供属性面板试算 */
export function trialBodyFromSelectedRun(run: RunRecord | null | undefined): unknown {
  return findLatestHttpBody(run?.steps);
}

/**
 * 从接口 responseConfig 取首个 responses[].example 作为试算 body。
 * example 为 JSON 字符串时尝试 parse；没有 example 则返回 undefined（不做 schema 生成）。
 */
export function extractResponseExample(responseConfig: unknown): unknown {
  const { ok, bundle } = parseResponseConfigInput(
    typeof responseConfig === 'string' || responseConfig == null
      ? responseConfig
      : JSON.stringify(responseConfig),
  );
  if (!ok || !bundle?.responses?.length) return undefined;
  const example = bundle.responses[0]?.example;
  if (example === undefined || example === null) return undefined;
  if (typeof example === 'string') {
    const t = example.trim();
    if (!t) return undefined;
    try {
      return JSON.parse(t);
    } catch {
      return t;
    }
  }
  return example;
}

/**
 * 沿入边向上游 DFS，找最近一个「project 模式且已填 testProjectApiId」的 HTTP 节点。
 * 外联 HTTP、未绑定接口的节点会继续往更上游找；有环时停止。
 */
export function findUpstreamProjectHttpNode(
  nodeId: string,
  nodes: Node[] | null | undefined,
  edges: Edge[] | null | undefined,
): Node | null {
  if (!nodeId || !nodes?.length) return null;
  const byId = new Map(nodes.map((n) => [n.id, n]));
  const incoming = new Map<string, string[]>();
  for (const e of edges ?? []) {
    if (!e?.target || !e?.source) continue;
    const list = incoming.get(e.target) ?? [];
    list.push(e.source);
    incoming.set(e.target, list);
  }
  const visiting = new Set<string>();
  function walk(id: string): Node | null {
    if (!visiting.add(id)) return null;
    for (const predId of incoming.get(id) ?? []) {
      const pred = byId.get(predId);
      if (!pred) continue;
      const type = String(pred.type ?? '').trim().toLowerCase();
      if (type === 'http') {
        const data = (pred.data ?? {}) as Record<string, unknown>;
        const apiId = String(data.testProjectApiId ?? '').trim();
        const callMode = String(data.callMode ?? 'project').trim().toLowerCase();
        if (apiId && callMode !== 'external') return pred;
      }
      const deeper = walk(predId);
      if (deeper) return deeper;
    }
    return null;
  }
  return walk(nodeId);
}

/**
 * 解析属性面板试算应拉取的接口 id。
 * HTTP 节点：用自身 testProjectApiId；
 * assert / condition：用上游 project HTTP 的 testProjectApiId；
 * 其它类型或找不到时返回空串。
 */
export function resolveTrialApiId(
  node: { id?: string; type?: string; data?: Record<string, unknown> } | null | undefined,
  nodes: Node[] | null | undefined,
  edges: Edge[] | null | undefined,
): string {
  if (!node?.id) return '';
  const type = String(node.type ?? '').trim().toLowerCase();
  if (type === 'http') {
    return String(node.data?.testProjectApiId ?? '').trim();
  }
  if (type === 'assert' || type === 'condition') {
    const upstream = findUpstreamProjectHttpNode(node.id, nodes, edges);
    return String((upstream?.data as Record<string, unknown> | undefined)?.testProjectApiId ?? '').trim();
  }
  return '';
}

/**
 * 判断试算原始值是否未命中：undefined、null、或空数组。
 * 用于门禁语义判断（属性面板标红时通常看展示文案）。
 */
export function isTrialMissValue(value: unknown): boolean {
  if (value === undefined || value === null) return true;
  return Array.isArray(value) && value.length === 0;
}

/**
 * 判断「试算：…」展示文案是否表示失败或未命中。
 * 命中：试算失败前缀、路径未命中、空数组 []。
 */
export function isTrialMissPreview(text: string): boolean {
  const t = String(text ?? '').trim();
  if (!t) return false;
  if (t.startsWith('试算失败：')) return true;
  if (t.includes('路径未命中')) return true;
  if (t === '试算：[]') return true;
  return false;
}

/** 把求值结果格式化为面板展示文案；undefined 标明路径未命中 */
export function formatTrialResult(value: unknown): string {
  if (value === undefined) return '试算：undefined（路径未命中）';
  try {
    return `试算：${JSON.stringify(value)}`;
  } catch {
    return `试算：${String(value)}`;
  }
}

/**
 * 提取节点 body 表达式试算。
 * 无 trialBody / 空表达式 → 空串；必须以 $ 开头且路径可解析。
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
 * 断言 / 条件左值试算结果（内部共用）。
 * out_of_scope：非 http.body，面板不展示；bad_*：展示「试算失败」；ok：可 format。
 */
type AssertLeftEval =
  | { ok: true; value: unknown }
  | { ok: false; reason: 'out_of_scope' | 'bad_body_dollar' | 'bad_jsonpath'; detail?: string };

function evalAssertLeftDetailed(trialBody: unknown, left: string): AssertLeftEval {
  const normalized = normalizeAssertLeftPath(String(left ?? '').trim());
  if (!normalized) return { ok: false, reason: 'out_of_scope' };
  if (normalized === 'http.body') return { ok: true, value: trialBody };
  if (!normalized.startsWith('http.body.')) return { ok: false, reason: 'out_of_scope' };
  const relative = normalized.slice('http.body.'.length);
  if (relative.startsWith('$')) return { ok: false, reason: 'bad_body_dollar' };
  const abs = toAbsoluteJsonPath(relative);
  if (!isValidJsonPath(abs)) return { ok: false, reason: 'bad_jsonpath', detail: abs };
  return { ok: true, value: evalJsonPath(trialBody, abs) };
}

/**
 * 断言 / 条件左值试算。
 * 仅处理 http.body 与规范化后的 $ → http.body 方言；
 * 禁止写成 http.body.$.…；flow.* / env.* 等其它 scope 返回空串（不展示试算）。
 */
export function previewAssertLeft(trialBody: unknown, left: string): string {
  if (trialBody === undefined || trialBody === null) return '';
  const raw = String(left ?? '').trim();
  if (!raw) return '';
  const result = evalAssertLeftDetailed(trialBody, raw);
  if (!result.ok) {
    if (result.reason === 'out_of_scope') return '';
    if (result.reason === 'bad_body_dollar') return '试算失败：不可写成 http.body.$.…';
    return `试算失败：JsonPath 无法解析 ${result.detail ?? ''}`;
  }
  return formatTrialResult(result.value);
}

/** 路径段 items：JSON Schema 关键字，断言左值不应出现（与后端 AssertPathDesignGate 一致） */
const SCHEMA_ITEMS_SEGMENT = /(^|\.)items(\.|\[|$)/;

/**
 * 从 responseConfig 收集首个响应 schema 的叶路径（如 data[*].quantity）。
 * 无 schema 时返回空数组。
 */
export function extractResponseSchemaPaths(responseConfig: unknown): string[] {
  const { ok, bundle } = parseResponseConfigInput(
    typeof responseConfig === 'string' || responseConfig == null
      ? responseConfig
      : JSON.stringify(responseConfig),
  );
  if (!ok || !bundle?.responses?.length) return [];
  const schema = bundle.responses[0]?.schema;
  if (!schema || typeof schema !== 'object') return [];
  const out: string[] = [];
  collectSchemaLeaves(schema as Record<string, unknown>, '', out);
  return out;
}

function collectSchemaLeaves(schema: Record<string, unknown>, prefix: string, out: string[]): void {
  const type = String(schema.type ?? '').toLowerCase();
  if (type === 'object' || schema.properties) {
    const props = schema.properties as Record<string, unknown> | undefined;
    if (!props || typeof props !== 'object') {
      if (prefix) out.push(prefix);
      return;
    }
    for (const [key, child] of Object.entries(props)) {
      if (!child || typeof child !== 'object') continue;
      const next = prefix ? `${prefix}.${key}` : key;
      collectSchemaLeaves(child as Record<string, unknown>, next, out);
    }
    return;
  }
  if (type === 'array' || schema.items) {
    const items = schema.items;
    const next = prefix ? `${prefix}[*]` : '[*]';
    if (items && typeof items === 'object' && !Array.isArray(items)) {
      collectSchemaLeaves(items as Record<string, unknown>, next, out);
    } else if (prefix) {
      out.push(prefix);
    }
    return;
  }
  if (prefix) out.push(prefix);
}

/** 与后端 HttpNodeApiHealthChecker.normalizeStructuralPath 对齐 */
export function normalizeStructuralPath(path: string): string {
  let p = String(path ?? '').trim();
  if (p.startsWith('$.')) p = p.slice(2);
  else if (p.startsWith('$')) {
    p = p.slice(1);
    if (p.startsWith('.')) p = p.slice(1);
  }
  if (p.startsWith('http.body.')) p = p.slice('http.body.'.length);
  else if (p === 'http.body') return '';
  let out = '';
  for (let i = 0; i < p.length; ) {
    if (p[i] === '[') {
      const close = p.indexOf(']', i);
      if (close < 0) {
        out += p[i];
        i++;
        continue;
      }
      i = close + 1;
      continue;
    }
    out += p[i];
    i++;
  }
  p = out.replace(/(?<=^|\.)items(?=\.|$)/g, '');
  p = p.replace(/\.{2,}/g, '.');
  if (p.startsWith('.')) p = p.slice(1);
  if (p.endsWith('.')) p = p.slice(0, -1);
  return p;
}

export function pathMatchesSchema(path: string, schemaPaths: Iterable<string>): boolean {
  const p = normalizeStructuralPath(path);
  if (!p) return false;
  for (const schemaPath of schemaPaths) {
    if (!schemaPath) continue;
    const s = normalizeStructuralPath(schemaPath);
    if (!s) continue;
    if (s === p || p.startsWith(`${s}.`) || s.startsWith(`${p}.`)) return true;
  }
  return false;
}

export function containsJsonSchemaItemsSegment(relativePath: string): boolean {
  return SCHEMA_ITEMS_SEGMENT.test(String(relativePath ?? '').trim());
}

/**
 * 保存前设计期断言路径门禁（与后端 AssertPathDesignGate 对齐：schema + 禁 .items）。
 * schemaPathsByApiId 无条目或空数组时跳过该节点（与后端「无 schema 不报错」一致）。
 * 响应 example 不参与硬拦。
 */
export function collectAssertPathDesignErrors(
  graph: { nodes?: Array<Record<string, unknown>>; edges?: Array<Record<string, unknown>> } | null | undefined,
  schemaPathsByApiId: Map<string, string[]> | Record<string, string[]>,
): string[] {
  const errors: string[] = [];
  const nodes = (graph?.nodes ?? []) as Array<{
    id?: string;
    type?: string;
    data?: Record<string, unknown>;
  }>;
  const edges = (graph?.edges ?? []) as Edge[];
  const schemaMap =
    schemaPathsByApiId instanceof Map
      ? schemaPathsByApiId
      : new Map(Object.entries(schemaPathsByApiId ?? {}));

  for (const node of nodes) {
    const type = String(node.type ?? '').trim().toLowerCase();
    if (type !== 'assert' && type !== 'condition') continue;
    const apiId = resolveTrialApiId(node, nodes as Node[], edges);
    if (!apiId || !schemaMap.has(apiId)) continue;
    const schemaPaths = schemaMap.get(apiId) ?? [];
    if (!schemaPaths.length) continue;

    const nodeName =
      node.data?.name != null && String(node.data.name).trim()
        ? String(node.data.name)
        : node.id ?? '?';
    const kindLabel = type === 'assert' ? '断言' : '条件';

    if (type === 'assert') {
      const rules = Array.isArray(node.data?.rules) ? (node.data!.rules as Array<Record<string, unknown>>) : [];
      appendSchemaPathErrors(errors, kindLabel, nodeName, 'rules', rules, schemaPaths);
    } else {
      const branches = Array.isArray(node.data?.branches)
        ? (node.data!.branches as Array<Record<string, unknown>>)
        : [];
      branches.forEach((branch, bi) => {
        const conditions = Array.isArray(branch?.conditions)
          ? (branch.conditions as Array<Record<string, unknown>>)
          : [];
        appendSchemaPathErrors(
          errors,
          kindLabel,
          nodeName,
          `branches[${bi}].conditions`,
          conditions,
          schemaPaths,
        );
      });
    }
  }
  return errors;
}

function appendSchemaPathErrors(
  errors: string[],
  kindLabel: string,
  nodeName: string,
  fieldName: string,
  rules: Array<Record<string, unknown>>,
  schemaPaths: string[],
): void {
  rules.forEach((rule, i) => {
    const rawLeft = rule?.left == null ? '' : String(rule.left).trim();
    if (!rawLeft) return;
    const left = normalizeAssertLeftPath(rawLeft);
    if (!left.startsWith('http.body')) return;
    if (left === 'http.body') return;
    if (!left.startsWith('http.body.')) return;
    const relative = left.slice('http.body.'.length);
    if (!relative) return;
    if (relative.startsWith('$')) {
      errors.push(
        `${kindLabel}节点「${nodeName}」${fieldName}[${i}] 左值「${left}」不可写成 http.body.$.…`,
      );
      return;
    }
    if (containsJsonSchemaItemsSegment(relative)) {
      errors.push(
        `${kindLabel}节点「${nodeName}」${fieldName}[${i}] 左值「${left}」误含 JSON Schema 关键字 .items；数组请用 data[0]、data[*] 或 data[?(@.field==…)]`,
      );
      return;
    }
    if (!pathMatchesSchema(relative, schemaPaths)) {
      errors.push(
        `${kindLabel}节点「${nodeName}」${fieldName}[${i}] 左值「${left}」在上游接口响应 schema 中未找到对应字段`,
      );
    }
  });
}
