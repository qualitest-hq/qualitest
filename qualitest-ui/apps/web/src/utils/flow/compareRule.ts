/**
 * 断言节点、条件分支的单条比较规则求值。
 * 左值按路径从运行时上下文读取；右值先做宽松占位符替换再比较。
 */
import type { FlowRunContext } from './types';
import { resolvePathSegment } from './placeholder';
import { resolvePlaceholderString } from './placeholder';

/** 单条比较规则：左值路径、运算符、右值（可含占位符） */
export interface CompareRule {
  left?: string;
  operator?: string;
  right?: string;
}

/** 条件/断言下拉可选的比较运算符 */
export const COND_OPERATORS = [
  { value: 'eq', label: '等于' },
  { value: 'ne', label: '不等于' },
  { value: 'gt', label: '大于' },
  { value: 'lt', label: '小于' },
  { value: 'gte', label: '大于等于' },
  { value: 'lte', label: '小于等于' },
  { value: 'contains', label: '包含' },
  { value: 'not_contains', label: '不包含' },
  { value: 'exists', label: '存在' },
] as const;

export type CondOperator = (typeof COND_OPERATORS)[number]['value'];

/** 运算符 value → 中文标签 */
export function condOpLabel(op: string | undefined): string {
  return COND_OPERATORS.find((o) => o.value === op)?.label ?? op ?? '';
}

/**
 * 运算符别名归一：equals/== → eq；不等别名 → ne；
 * notempty/not_empty/isNotEmpty → exists；空 → eq。
 */
export function normalizeOperator(raw: string | undefined | null): string {
  if (raw == null || String(raw).trim() === '') return 'eq';
  const lower = String(raw).trim().toLowerCase();
  switch (lower) {
    case 'equals':
    case 'equal':
    case '==':
      return 'eq';
    case 'notequals':
    case 'not_equals':
    case 'neq':
    case '!=':
      return 'ne';
    case 'notempty':
    case 'not_empty':
    case 'isnotempty':
    case 'is_not_empty':
      return 'exists';
    default:
      return lower;
  }
}

/**
 * 规范为可比较值：number 保留；纯数字字符串解析为 number；数组保持；其余为字符串。
 */
export function coerceComparable(val: unknown): unknown {
  if (val == null) return val;
  if (typeof val === 'number') return val;
  if (Array.isArray(val)) return val;
  const s = String(val).trim();
  if (s === '') return '';
  const n = Number(s);
  if (!Number.isNaN(n) && String(n) === s) return n;
  return s;
}

/** 求值明细：是否通过 + 左值实测 */
export interface CompareEvalDetail {
  passed: boolean;
  leftActual: unknown;
}

/**
 * exists：非 null；非空串；数组/对象非空则通过；其它标量视为存在。
 */
function existsValue(leftRaw: unknown): boolean {
  if (leftRaw == null) return false;
  if (typeof leftRaw === 'string') return leftRaw !== '';
  if (Array.isArray(leftRaw)) return leftRaw.length > 0;
  if (typeof leftRaw === 'object') return Object.keys(leftRaw as object).length > 0;
  return true;
}

/** 数组长度 > 1 */
function isMultiValue(leftRaw: unknown): boolean {
  return Array.isArray(leftRaw) && leftRaw.length > 1;
}

/** 数组恰好 1 个元素时取出该元素 */
function unboxSingleton(leftRaw: unknown): unknown {
  if (Array.isArray(leftRaw) && leftRaw.length === 1) return leftRaw[0];
  return leftRaw;
}

/** 需要标量左值的比较运算符 */
function isScalarCompareOp(op: string): boolean {
  return op === 'eq' || op === 'ne' || op === 'gt' || op === 'lt' || op === 'gte' || op === 'lte';
}

/**
 * contains：左值为数组时任一元素转字符串包含右值即通过；否则按标量字符串包含判断。
 */
function containsValue(leftRaw: unknown, leftCoerced: unknown, right: unknown): boolean {
  const rightStr = String(right);
  if (Array.isArray(leftRaw)) {
    return leftRaw.some((item) => String(item).includes(rightStr));
  }
  return String(leftCoerced).includes(rightStr);
}

/**
 * 求值并返回是否通过与左值实测。
 * 多元素数组做 eq/ne/数值比较时直接失败（仅长度 1 可拆箱）。
 */
export function evalCompareRuleDetailed(rule: CompareRule, ctx: FlowRunContext): CompareEvalDetail {
  const leftRaw = resolvePathSegment(ctx, String(rule.left ?? '').trim());
  const op = normalizeOperator(rule.operator);

  if (op === 'exists') {
    return { passed: existsValue(leftRaw), leftActual: leftRaw };
  }

  if (isMultiValue(leftRaw) && isScalarCompareOp(op)) {
    return { passed: false, leftActual: leftRaw };
  }

  const leftUnboxed = unboxSingleton(leftRaw);
  const left = coerceComparable(leftUnboxed);
  const right = coerceComparable(resolvePlaceholderString(rule.right, ctx, 'lenient'));

  let passed = false;
  switch (op) {
    case 'eq':
      passed = left == right;
      break;
    case 'ne':
      passed = left != right;
      break;
    case 'gt':
      passed = Number(left) > Number(right);
      break;
    case 'lt':
      passed = Number(left) < Number(right);
      break;
    case 'gte':
      passed = Number(left) >= Number(right);
      break;
    case 'lte':
      passed = Number(left) <= Number(right);
      break;
    case 'contains':
      passed = containsValue(leftRaw, left, right);
      break;
    case 'not_contains':
      passed = !containsValue(leftRaw, left, right);
      break;
    default:
      passed = false;
  }
  return { passed, leftActual: leftRaw };
}

/**
 * 求值是否通过。
 * 左值按路径取值；右值先宽松替换占位符；多条 rules 的 AND 由调用方负责。
 */
export function evalCompareRule(rule: CompareRule, ctx: FlowRunContext): boolean {
  return evalCompareRuleDetailed(rule, ctx).passed;
}

/** 编辑器新增行的空规则模板 */
export function emptyCompareRule(): CompareRule {
  return { left: '', operator: 'eq', right: '' };
}

/** assert 节点默认规则：业务 code 等于 0 */
export function defaultAssertRules(): CompareRule[] {
  return [{ left: 'http.body.data.code', operator: 'eq', right: '0' }];
}
