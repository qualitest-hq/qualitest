/**
 * Assign 节点赋值：运算符、作用域、结构化 value、写入 flow/out、卡片摘要。
 * 画布属性面板编辑、节点卡片文案、前端 Mock 跑图共用本模块。
 */
import { resolvePlaceholderString } from './placeholder';
import type { FlowRunContext } from './types';

/** 赋值运算符选项：value 类直接设值，step 类按步长累加 */
export const ASSIGN_OPS = [
  { value: 'set', label: '=', category: 'value' as const },
  { value: 'add', label: '+=', category: 'step' as const },
  { value: 'sub', label: '-=', category: 'step' as const },
  { value: 'mul', label: '*=', category: 'step' as const },
  { value: 'div', label: '/=', category: 'step' as const },
];

/** 需要步长与缺省值的运算符 */
export const ASSIGN_STEP_OPS = new Set(['add', 'sub', 'mul', 'div']);

/** 直接设值的运算符 */
export const ASSIGN_VALUE_OPS = new Set(['set']);

/** 可写作用域：flow=中间量；out=出参（开放入站回包） */
export const ASSIGN_SCOPES = [
  { value: 'flow', label: 'flow' },
  { value: 'out', label: 'out' },
];

export interface AssignOpResult {
  scope: string;
  name: string;
  op: string;
  before: unknown;
  after: unknown;
}

/** 规范化赋值运算符；非法值回退 set */
export function normalizeAssignOp(op: unknown): string {
  const s = String(op || 'set');
  return ASSIGN_OPS.some((o) => o.value === s) ? s : 'set';
}

/** 规范化作用域：仅识别 out，其余一律视为 flow */
export function normalizeAssignScope(scope: unknown): 'flow' | 'out' {
  return String(scope || 'flow').trim().toLowerCase() === 'out' ? 'out' : 'flow';
}

/** 运算符对应的展示符号 */
export function assignOpSymbol(op: string): string {
  const normalized = normalizeAssignOp(op);
  const map: Record<string, string> = { set: '←', add: '+=', sub: '-=', mul: '*=', div: '/=' };
  return map[normalized] || normalized;
}

/** 运算符所属类别：value 或 step */
export function assignOpCategory(op: unknown): 'value' | 'step' {
  const normalized = normalizeAssignOp(op);
  return ASSIGN_OPS.find((o) => o.value === normalized)?.category || 'value';
}

/** 空赋值行，供编辑器新增行使用 */
export function emptyAssignment(): Record<string, unknown> {
  return { scope: 'flow', name: '', op: 'set', value: '', step: 1, ifMissing: 0 };
}

/** 新 assign 节点的默认赋值列表 */
export function defaultAssignments(): Array<Record<string, unknown>> {
  return [{ scope: 'flow', name: 'pollAttempt', op: 'set', value: '0' }];
}

/** 规范化单行赋值（纯函数，不写回节点 data） */
export function normalizeAssignmentRow(a: Record<string, unknown>): Record<string, unknown> {
  const op = normalizeAssignOp(a.op);
  const scope = normalizeAssignScope(a.scope);
  const row: Record<string, unknown> = {
    scope,
    name: String(a.name || '').trim(),
    op: scope === 'out' && ASSIGN_STEP_OPS.has(op) ? 'set' : op,
  };
  const effectiveOp = String(row.op);
  if (ASSIGN_STEP_OPS.has(effectiveOp)) {
    row.step = Number(a.step) || 1;
    row.ifMissing = a.ifMissing != null && a.ifMissing !== '' ? Number(a.ifMissing) : 0;
  } else if (a.value != null && typeof a.value === 'object') {
    row.value = a.value;
  } else {
    row.value = a.value != null ? String(a.value) : '';
  }
  return row;
}

/** 原行与规范化结果是否一致（字段级），一致则不必替换 assignments 数组 */
function assignmentRowAlreadyNormalized(a: Record<string, unknown>, normalized: Record<string, unknown>): boolean {
  if (a.scope !== normalized.scope || a.name !== normalized.name || a.op !== normalized.op) return false;
  if (ASSIGN_STEP_OPS.has(String(normalized.op))) {
    return a.step === normalized.step && a.ifMissing === normalized.ifMissing;
  }
  if (normalized.value != null && typeof normalized.value === 'object') {
    return a.value === normalized.value;
  }
  return a.value === normalized.value;
}

/**
 * 规范化节点 data.assignments：补全 scope、trim 变量名；
 * 四则保留 step/ifMissing，set 保留 value（对象/数组原样留下）。
 * 写 out 时若误配四则，静默改成 set。就地改 data；已规范则不写，避免卡片 computed 死循环。
 */
export function normalizeAssignNodeData(data: Record<string, unknown> | undefined): void {
  if (!data || typeof data !== 'object') return;
  if (Array.isArray(data.assignments) && data.assignments.length) {
    const prev = data.assignments as Array<Record<string, unknown>>;
    const next = prev.map(normalizeAssignmentRow);
    const unchanged =
      prev.length === next.length
      && prev.every((row, i) => assignmentRowAlreadyNormalized(row, next[i]));
    if (!unchanged) {
      data.assignments = next;
    }
    return;
  }
  data.assignments = defaultAssignments();
}

/**
 * 读取规范化后的 assignments（纯函数，不写回 data）。
 * 画布卡片 computed / 摘要展示必须用这个，禁止在渲染路径里 normalizeAssignNodeData。
 */
export function getAssignAssignments(data: Record<string, unknown> | undefined): Array<Record<string, unknown>> {
  if (!data) return [];
  if (!Array.isArray(data.assignments) || !data.assignments.length) {
    return defaultAssignments();
  }
  return (data.assignments as Array<Record<string, unknown>>).map(normalizeAssignmentRow);
}

/** 单条赋值的目标路径，变量名为空时返回空串 */
export function formatAssignDest(a: Record<string, unknown>): string {
  const name = String(a.name || '').trim();
  if (!name) return '';
  const scope = normalizeAssignScope(a.scope);
  return `${scope}.${name}`;
}

/** 单条赋值的可读摘要 */
export function formatAssignAssignment(a: Record<string, unknown>): string {
  const dest = formatAssignDest(a);
  if (!dest) return '未配置赋值';
  const op = normalizeAssignOp(a.op);
  if (ASSIGN_STEP_OPS.has(op)) {
    const step = a.step != null && a.step !== 1 ? a.step : 1;
    return `${dest} ${assignOpSymbol(op)} ${step}`;
  }
  if (a.value != null && typeof a.value === 'object') {
    return `${dest} ${assignOpSymbol(op)} {…}`;
  }
  const val = a.value != null && String(a.value) !== '' ? a.value : '?';
  return `${dest} ${assignOpSymbol(op)} ${val}`;
}

/** assign 节点卡片副标题：多条赋值用 · 拼接 */
export function formatAssignSummary(data: Record<string, unknown>): string {
  const items = getAssignAssignments(data).filter((a) => formatAssignDest(a));
  if (!items.length) return '点击配置赋值';
  return items.map(formatAssignAssignment).join(' · ');
}

/**
 * 解析 set 的 value：字符串做占位替换并尝试转布尔/数字；
 * 对象与数组递归处理叶子字符串，结构本身原样保留。
 */
function resolveStructuredValue(raw: unknown, ctx: FlowRunContext): unknown {
  if (raw == null) return null;
  if (typeof raw === 'string') {
    let after: unknown = resolvePlaceholderString(raw, ctx);
    if (after === 'true') after = true;
    if (after === 'false') after = false;
    const num = Number(after);
    if (!Number.isNaN(num) && String(num) === String(after).trim()) after = num;
    return after;
  }
  if (Array.isArray(raw)) {
    return raw.map((item) => resolveStructuredValue(item, ctx));
  }
  if (typeof raw === 'object') {
    const out: Record<string, unknown> = {};
    for (const [k, v] of Object.entries(raw as Record<string, unknown>)) {
      out[k] = resolveStructuredValue(v, ctx);
    }
    return out;
  }
  return raw;
}

/** 按点路径写入：无点写顶层；有点则创建中间对象后写入末段（如 headers.X-Demo） */
function putByPath(root: Record<string, unknown>, path: string, value: unknown): void {
  if (!path.includes('.')) {
    root[path] = value;
    return;
  }
  const parts = path.split('.');
  let cur: Record<string, unknown> = root;
  for (let i = 0; i < parts.length - 1; i++) {
    const next = cur[parts[i]];
    if (next == null || typeof next !== 'object' || Array.isArray(next)) {
      const created: Record<string, unknown> = {};
      cur[parts[i]] = created;
      cur = created;
    } else {
      cur = next as Record<string, unknown>;
    }
  }
  cur[parts[parts.length - 1]] = value;
}

/** 按点路径读取：先试整键，再没有则分段下钻 */
function getByPath(root: Record<string, unknown>, path: string): unknown {
  if (!path.includes('.')) return root[path];
  if (Object.prototype.hasOwnProperty.call(root, path)) return root[path];
  let cur: unknown = root;
  for (const part of path.split('.')) {
    if (cur == null || typeof cur !== 'object') return undefined;
    cur = (cur as Record<string, unknown>)[part];
  }
  return cur;
}

/**
 * 前端 Mock：执行单条赋值，写入 ctx.flow 或 ctx.out。
 * out 不允许四则。返回 before/after；名为空则 null。
 */
export function applyAssignOp(
  a: Record<string, unknown>,
  ctx: FlowRunContext,
): AssignOpResult | null {
  const name = String(a.name || '').trim();
  if (!name) return null;
  const op = normalizeAssignOp(a.op);
  const scope = normalizeAssignScope(a.scope);
  if (!ctx.out) ctx.out = {};
  if (!ctx.in) ctx.in = {};
  const target = scope === 'out' ? ctx.out : ctx.flow;
  const before = getByPath(target, name);
  let after: unknown = before;

  if (scope === 'out' && ASSIGN_STEP_OPS.has(op)) {
    throw new Error('出参作用域 out 仅支持 set');
  }

  if (ASSIGN_STEP_OPS.has(op)) {
    const base = before != null && before !== '' ? Number(before) : Number(a.ifMissing) || 0;
    const step = Number(a.step) || 1;
    if (op === 'add') after = base + step;
    else if (op === 'sub') after = base - step;
    else if (op === 'mul') after = base * step;
    else if (op === 'div') after = step !== 0 ? base / step : base;
  } else {
    after = resolveStructuredValue(a.value ?? '', ctx);
  }

  putByPath(target, name, after);
  return { scope, name, op, before, after };
}
