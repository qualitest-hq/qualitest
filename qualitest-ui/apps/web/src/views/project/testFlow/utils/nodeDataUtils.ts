/**
 * 节点 data 工具：生成 id、创建默认 data、格式化画布摘要（summary）。
 * 供节点库创建、属性编辑、画布卡片展示共用。
 */
import { formatAssignSummary, formatAssignAssignment, getAssignAssignments } from '@/utils/flow/assign';
import { condOpLabel } from '@/utils/flow/compareRule';
import { filterFilledExtracts } from '@/utils/flow/extract';

export { formatAssignAssignment, getAssignAssignments } from '@/utils/flow/assign';

import { isUrlencodedBodyMode } from '@/views/project/testProject/utils/bodyModeUtils';
import { ASSERT_LEFT_LABELS, defaultConditionBranches, NODE_TYPES } from '../constants/nodeTypes';
import { formatConditionSummary } from './conditionUtils';
import { formatExternalSummary, isExternalCallMode } from './httpSummary';
import { nextSnowflakeId } from '@/utils/flow/snowflakeId';

/** 生成画布节点 id（雪花数字串） */
export function generateNodeId(_type?: string): string {
  return nextSnowflakeId();
}

export function getAssertRules(data: Record<string, unknown> | undefined) {
  if (!data) return [];
  return (data.rules as Array<Record<string, unknown>>) ?? [];
}

export function formatAssertLeftLabel(left: string) {
  const key = String(left || '').trim();
  return ASSERT_LEFT_LABELS[key] || key;
}

export function formatAssertRule(rule: Record<string, unknown>) {
  if (!rule || !String(rule.left || '').trim()) return '未配置断言';
  const left = formatAssertLeftLabel(String(rule.left));
  if (rule.operator === 'exists') return `${left} 存在`;
  const right = rule.right != null && String(rule.right) !== '' ? ` ${rule.right}` : '';
  return `${left} ${condOpLabel(String(rule.operator))}${right}`;
}

export function formatAssertSummary(data: Record<string, unknown>) {
  const rules = getAssertRules(data).filter((r) => String(r.left || '').trim());
  if (!rules.length) return '点击配置断言';
  return rules.map(formatAssertRule).join(' 且 ');
}

/** 脚本节点卡片副标题：语言 + 源码长度或「未配置源码」 */
export function formatScriptSummary(data: Record<string, unknown>) {
  const lang = String(data.language || 'javascript');
  const langLabel = lang === 'python' ? 'Python' : 'JavaScript';
  const source = String(data.source || '');
  if (!source.trim()) return `${langLabel} · 未配置源码`;
  return `${langLabel} · ${source.length} 字符`;
}

function countFilledRows(rows: Array<Record<string, unknown>> | undefined) {
  return (rows || []).filter(
    (r) => r._enabled !== false && (String(r.name || '').trim() || String(r.value || '').trim()),
  ).length;
}

/** HTTP 节点卡片副标题：project 为 method + 名称/路径；external 为 method ↗ host/path */
export function formatHttpRequestLine(data: Record<string, unknown>) {
  if (isExternalCallMode(data?.callMode)) {
    const method = String(data.httpMethod || 'GET').toUpperCase();
    const externalUrl = String(data.externalUrl || '—');
    let line = formatExternalSummary(method, externalUrl);
    const timeoutMs = data.timeoutMs;
    if (timeoutMs != null && timeoutMs !== '') line += ` · ${timeoutMs}ms`;
    return line;
  }
  if (!data?.testProjectApiId) return '请选择项目接口';
  const method = data.httpMethod || '—';
  // 薄节点通常无 apiPath，用 apiName 展示；若有残留 apiPath 则优先显示
  const path = (data.apiPath && String(data.apiPath).trim())
    || data.apiName
    || '—';
  let line = `${String(method).toUpperCase()} ${path}`;
  const overrides = (data.requestValueOverrides as Record<string, unknown>) || {};
  const paramDefaults = (overrides.paramDefaults as Record<string, unknown>) || {};
  let paramCount =
    Object.keys(paramDefaults).length +
    countFilledRows(data.headers as Array<Record<string, unknown>>) +
    countFilledRows(data.cookies as Array<Record<string, unknown>>) +
    (Object.prototype.hasOwnProperty.call(overrides, 'bodyExample')
      && overrides.bodyExample != null
      && String(overrides.bodyExample).trim() !== ''
      ? 1
      : 0);
  // 旧版整份 requestConfig 仍在时，按结构统计参数数
  const rc = (data.requestConfig as Record<string, unknown>) || {};
  if (!Object.keys(paramDefaults).length && rc) {
    const body = (rc.body as Record<string, unknown>) || {};
    paramCount =
      countFilledRows(rc.queryParams as Array<Record<string, unknown>>) +
      countFilledRows(rc.pathParams as Array<Record<string, unknown>>) +
      countFilledRows(data.headers as Array<Record<string, unknown>>) +
      countFilledRows(data.cookies as Array<Record<string, unknown>>) +
      (body.mode === 'json' && String((body.json as Record<string, unknown>)?.example || '').trim() ? 1 : 0) +
      (isUrlencodedBodyMode(body.mode) ? countFilledRows(body.urlencoded as Array<Record<string, unknown>>) : 0);
  }
  if (paramCount) line += ` · ${paramCount} 项参数`;
  const timeoutMs = data.timeoutMs;
  if (timeoutMs != null && timeoutMs !== '') line += ` · ${timeoutMs}ms`;
  return line;
}

/** 子流节点卡片副标题 */
export function formatSubflowSummary(data: Record<string, unknown>) {
  let name = String(data.subflowName || '').trim();
  if (!name && data.subflowId != null && String(data.subflowId).trim()) {
    name = `子流#${data.subflowId}`;
  }
  if (!name && data.templateId) {
    name = String(data.templateId);
  }
  if (!name) return '请选择子流';
  const outputs = (data.outputs as Array<Record<string, unknown>>) || [];
  const keys = outputs
    .map((o) => String(o.flowKey || o.name || '').trim())
    .filter(Boolean);
  if (keys.length) return `${name} → ${keys.join(', ')}`;
  return name;
}

/** 按节点类型刷新 data.summary，供卡片副标题展示 */
export function updateSummary(type: string, data: Record<string, unknown>) {
  switch (type) {
    case 'http': {
      data.summary = formatHttpRequestLine(data);
      const extractCount = filterFilledExtracts((data.extracts as never[]) || []).length;
      if (extractCount) data.summary += ` · 提取 ${extractCount} 项`;
      break;
    }
    case 'assert':
      data.summary = formatAssertSummary(data);
      break;
    case 'delay':
      data.summary = `${data.ms || 0} ms`;
      break;
    case 'condition':
      data.summary = formatConditionSummary(data);
      break;
    case 'assign':
      data.summary = formatAssignSummary(data);
      break;
    case 'script':
      data.summary = formatScriptSummary(data);
      break;
    case 'subflow':
      data.summary = formatSubflowSummary(data);
      break;
    default:
      break;
  }
}

/** 合并 NODE_TYPES 默认值与覆盖项，condition 自动补 branches */
export function createNodeData(type: string, override: Record<string, unknown> = {}) {
  const cfg = NODE_TYPES[type];
  if (!cfg) throw new Error(`Unknown node type: ${type}`);
  const data = { ...cfg.defaults, ...override };
  if (type === 'condition' && !data.branches) {
    data.branches = defaultConditionBranches();
  }
  updateSummary(type, data);
  return data;
}

export function getExtractChipTitle(t: Record<string, unknown>) {
  const from = t.from || 'body';
  const expr = t.expr || '';
  return `${from}: ${expr}`;
}

export function formatExtractTargetDest(t: Record<string, unknown>) {
  const scope = String(t.scope || 'flow');
  if (scope === 'flow') return `flow.${t.name || '?'}`;
  const key = t.entryKey || '?';
  const path = t.fieldPath ? `.${t.fieldPath}` : '';
  return `${scope}.${key}${path}`;
}

