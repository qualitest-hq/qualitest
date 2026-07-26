/**
 * 断言与条件分支的比较规则求值。
 * 供 assert 节点、condition 节点及运行报告使用。
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

/** 条件/断言可用的比较运算符（UI 下拉与求值共用） */
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
 * 将原始值转为可比较形态：纯数字字符串解析为 number，其余保持字符串。
 */
export function coerceComparable(val: unknown): unknown {
  if (val == null) return val;
  if (typeof val === 'number') return val;
  const s = String(val).trim();
  if (s === '') return '';
  const n = Number(s);
  if (!Number.isNaN(n) && String(n) === s) return n;
  return s;
}

/**
 * 对单条规则求值并返回是否成立。
 *
 * - 左值：经 resolvePathSegment 从 ctx 取值（如 http.body.*、flow.*、env.*、asset.*）
 * - 右值：先经 lenient 占位符替换，再参与比较
 * - exists：仅判断左值非 null 且非空串
 * - 多条 rules 的 AND 语义由调用方负责
 */
export function evalCompareRule(rule: CompareRule, ctx: FlowRunContext): boolean {
  const leftKey = String(rule.left ?? '').trim();
  const leftRaw = resolvePathSegment(ctx, leftKey);
  const op = rule.operator || 'eq';

  if (op === 'exists') {
    return leftRaw != null && leftRaw !== '';
  }

  const left = coerceComparable(leftRaw);
  const right = coerceComparable(resolvePlaceholderString(rule.right, ctx, 'lenient'));

  switch (op) {
    case 'eq':
      return left == right;
    case 'ne':
      return left != right;
    case 'gt':
      return Number(left) > Number(right);
    case 'lt':
      return Number(left) < Number(right);
    case 'gte':
      return Number(left) >= Number(right);
    case 'lte':
      return Number(left) <= Number(right);
    case 'contains':
      return String(left).includes(String(right));
    case 'not_contains':
      return !String(left).includes(String(right));
    default:
      return false;
  }
}

/** 空断言规则模板（编辑器新增行） */
export function emptyCompareRule(): CompareRule {
  return { left: '', operator: 'eq', right: '' };
}

/** assert 节点默认规则：业务 code 等于 0 */
export function defaultAssertRules(): CompareRule[] {
  return [{ left: 'http.body.data.code', operator: 'eq', right: '0' }];
}
