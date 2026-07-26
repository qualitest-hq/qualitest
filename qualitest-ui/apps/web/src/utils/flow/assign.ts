/**
 * Assign 节点赋值逻辑：运算符常量、数据规范化、flow 变量写入与可读摘要。
 * 供属性面板编辑、画布卡片展示、Mock 路径模拟执行共用。
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

/**
 * 规范化节点 data.assignments：补全 scope、trim 变量名、按 op 保留 value 或 step/ifMissing。
 * 就地修改 data 对象。
 */
export function normalizeAssignNodeData(data: Record<string, unknown> | undefined): void {
  if (!data || typeof data !== 'object') return;
  if (Array.isArray(data.assignments) && data.assignments.length) {
    data.assignments = (data.assignments as Array<Record<string, unknown>>).map((a) => {
      const op = normalizeAssignOp(a.op);
      const row: Record<string, unknown> = {
        scope: 'flow',
        name: String(a.name || '').trim(),
        op,
      };
      if (ASSIGN_STEP_OPS.has(op)) {
        row.step = Number(a.step) || 1;
        row.ifMissing = a.ifMissing != null && a.ifMissing !== '' ? Number(a.ifMissing) : 0;
      } else {
        row.value = a.value != null ? String(a.value) : '';
      }
      return row;
    });
    return;
  }
  data.assignments = defaultAssignments();
}

/** 返回规范化后的 assignments 数组 */
export function getAssignAssignments(data: Record<string, unknown> | undefined): Array<Record<string, unknown>> {
  if (!data) return [];
  normalizeAssignNodeData(data);
  return (data.assignments as Array<Record<string, unknown>>) || [];
}

/** 单条赋值的目标路径，变量名为空时返回空串 */
export function formatAssignDest(a: Record<string, unknown>): string {
  const name = String(a.name || '').trim();
  if (!name) return '';
  return `flow.${name}`;
}

/** 单条赋值的可读摘要，如 flow.pollAttempt ← 0 或 flow.pollAttempt += 1 */
export function formatAssignAssignment(a: Record<string, unknown>): string {
  const dest = formatAssignDest(a);
  if (!dest) return '未配置赋值';
  const op = normalizeAssignOp(a.op);
  if (ASSIGN_STEP_OPS.has(op)) {
    const step = a.step != null && a.step !== 1 ? a.step : 1;
    return `${dest} ${assignOpSymbol(op)} ${step}`;
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
 * 对单条 assignment 执行赋值，写入 ctx.flow。
 * @returns 赋值前后快照；变量名为空时返回 null
 */
export function applyAssignOp(
  a: Record<string, unknown>,
  ctx: FlowRunContext,
): AssignOpResult | null {
  const name = String(a.name || '').trim();
  if (!name) return null;
  const op = normalizeAssignOp(a.op);
  const before = ctx.flow[name];
  let after: unknown = before;

  if (ASSIGN_STEP_OPS.has(op)) {
    const base = before != null && before !== '' ? Number(before) : Number(a.ifMissing) || 0;
    const step = Number(a.step) || 1;
    if (op === 'add') after = base + step;
    else if (op === 'sub') after = base - step;
    else if (op === 'mul') after = base * step;
    else if (op === 'div') after = step !== 0 ? base / step : base;
  } else {
    after = resolvePlaceholderString(String(a.value ?? ''), ctx);
    if (after === 'true') after = true;
    if (after === 'false') after = false;
    const num = Number(after);
    if (!Number.isNaN(num) && String(num) === String(after).trim()) after = num;
  }

  ctx.flow[name] = after;
  return { scope: 'flow', name, op, before, after };
}
