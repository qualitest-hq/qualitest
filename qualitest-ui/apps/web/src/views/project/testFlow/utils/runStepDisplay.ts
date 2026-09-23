/**
 * 运行详情与步骤时间线的展示工具：
 * 节点类型中文名、URL 解码、失败分类、审计步判定、耗时/时间格式化、一行摘要、关联变量。
 */
import { runStatusLabel } from '../constants/runStatus';

/** 失败类别：bizCode 业务码失败；assert 断言失败；other 其它失败 */
export type FailureCategory = 'bizCode' | 'assert' | 'other';

/** 失败摘要挂载的关联 flow 变量一项 */
export type RelatedFlowVar = { key: string; value: unknown };

/**
 * 生成时间线一行摘要时用到的步骤字段。
 * 仅声明展示所需形状，不要求完整 Run 步骤对象。
 */
export type SummarizeStepInput = {
  nodeType?: string | null;
  nodeName?: string | null;
  status?: string | null;
  durationMs?: number | null;
  error?: { code?: string | null; message?: string | null } | null;
  http?: {
    method?: string | null;
    url?: string | null;
    status?: number | string | null;
    callMode?: string | null;
    bizCheck?: {
      passed?: boolean | null;
      actualCode?: unknown;
      message?: string | null;
    } | null;
  } | null;
  assert?: {
    rules?: Array<{
      passed?: boolean;
      left?: unknown;
      right?: unknown;
      leftActual?: unknown;
    }> | null;
  } | null;
  assigns?: Array<{ name?: string | null }> | null;
  extracts?: Array<{ name?: string | null; scope?: string | null }> | null;
  script?: {
    language?: string | null;
    writes?: Array<{ key?: string | null }> | null;
  } | null;
  flowAfter?: Record<string, unknown> | null;
  subflow?: {
    childSteps?: Array<{
      status?: string | null;
      nodeName?: string | null;
      nodeType?: string | null;
    }> | null;
  } | null;
  scenarioLoaded?: {
    scenarioName?: string | null;
    scenarioId?: string | null;
    envName?: string | null;
    flowSeed?: Record<string, unknown> | null;
  } | null;
};

/** 关联变量值展示的最大字符数（对象 / 数组截断） */
const RELATED_VAR_VALUE_MAX = 80;

/**
 * 审计类节点类型：场景加载、快照、还原、续跑决策。
 * 时间线中会弱化显示，不作为业务主路径强调。
 */
const AUDIT_NODE_TYPES = new Set([
  'runConfig',
  'snapshot',
  'restore',
  'resume_decision',
  'resumeDecision',
]);

/**
 * 节点类型转中文展示名。
 * 空值返回短横线；未识别类型原样返回。
 */
export function nodeTypeLabelZh(nodeType: string | null | undefined): string {
  if (!nodeType?.trim()) return '-';
  switch (nodeType.trim()) {
    case 'http':
      return 'HTTP';
    case 'assert':
      return '断言';
    case 'condition':
      return '分支';
    case 'assign':
      return '赋值';
    case 'script':
      return '脚本';
    case 'subflow':
      return '子流';
    case 'awaitInput':
    case 'input':
      return '等待输入';
    case 'delay':
      return '延时';
    case 'runConfig':
      return '场景加载';
    case 'snapshot':
      return '快照';
    case 'restore':
      return '还原';
    case 'resume_decision':
    case 'resumeDecision':
      return '续跑决策';
    default:
      return nodeType.trim();
  }
}

/**
 * 将 URL 中的百分号编码解码为可读文案，仅用于界面展示。
 * 不含 %、或解码失败时返回原文。
 */
export function decodeUrlForDisplay(url: string | null | undefined): string {
  if (url == null || url === '') return url ?? '';
  if (!url.includes('%')) return url;
  try {
    return decodeURIComponent(url);
  } catch {
    return url;
  }
}

/** 是否为审计类步骤（场景加载 / 快照 / 还原 / 续跑决策） */
export function isAuditStep(nodeType: string | null | undefined): boolean {
  if (!nodeType) return false;
  return AUDIT_NODE_TYPES.has(nodeType);
}

/**
 * 判定失败步骤所属类别。
 * 断言节点或 TF_ASSERT_FAILED → assert；
 * TF_BIZ_CODE 或业务码校验未通过 → bizCode；其余 → other。
 */
export function resolveFailureCategory(step: {
  nodeType?: string | null;
  error?: { code?: string | null } | null;
  http?: { bizCheck?: { passed?: boolean | null } | null } | null;
} | null | undefined): FailureCategory {
  if (!step) return 'other';
  if (step.nodeType === 'assert') return 'assert';
  const code = step.error?.code;
  if (code === 'TF_BIZ_CODE') return 'bizCode';
  if (code === 'TF_ASSERT_FAILED') return 'assert';
  if (step.http?.bizCheck?.passed === false) return 'bizCode';
  return 'other';
}

/** 失败类别转中文：业务码 / 断言 / 其他 */
export function failureCategoryLabel(category: FailureCategory | string | null | undefined): string {
  if (category === 'bizCode') return '业务码';
  if (category === 'assert') return '断言';
  return '其他';
}

/**
 * 是否业务码校验失败。
 * 错误码为 TF_BIZ_CODE，或 http.bizCheck.passed === false。
 */
export function isBizCodeFailure(step: {
  error?: { code?: string | null } | null;
  http?: { bizCheck?: { passed?: boolean | null } | null } | null;
} | null | undefined): boolean {
  return step?.error?.code === 'TF_BIZ_CODE' || step?.http?.bizCheck?.passed === false;
}

/** 格式化耗时：不足 1 秒显示毫秒，否则显示秒（保留两位小数）；空值返回短横线 */
export function formatDurationMs(ms: number | null | undefined): string {
  if (ms == null || Number.isNaN(Number(ms))) return '-';
  const n = Number(ms);
  if (n < 1000) return `${n} ms`;
  return `${(n / 1000).toFixed(2)} s`;
}

/** 格式化时间为 yyyy-MM-dd HH:mm:ss；空值或非法时间返回短横线或原文 */
export function formatRunTime(iso: string | Date | null | undefined): string {
  if (iso == null || iso === '') return '-';
  const d = iso instanceof Date ? iso : new Date(iso);
  if (Number.isNaN(d.getTime())) return String(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

/** 超长文案截断并加省略号，避免时间线单行过长 */
export function truncateText(text: string | null | undefined, max: number): string {
  const s = String(text ?? '');
  if (s.length <= max) return s;
  return `${s.slice(0, Math.max(0, max - 1))}…`;
}

/**
 * 键名列表一行摘要。
 * 0 项：动词 +「0 项」；1 项：动词 + 键名；多项：动词 + 首键 + 「等 N 项」。
 */
export function summarizeKeyList(keys: string[], verb: string): string {
  const n = keys.length;
  if (n === 0) return `${verb} 0 项`;
  if (n === 1) return `${verb} ${keys[0]}`;
  return `${verb} ${keys[0]} 等 ${n} 项`;
}

/**
 * 赋值 / 录入步骤的一行摘要。
 * 只列变量名与条数，不展开 before → after 明细。
 */
export function summarizeAssignKeys(
  assigns: Array<{ name?: string | null }> | null | undefined,
  verb: string,
): string {
  const keys = (assigns || []).map((a) => a.name).filter((n): n is string => !!n);
  return summarizeKeyList(keys, verb);
}

/**
 * 从断言左值路径解析 flow 变量键：仅接受以 `flow.` 开头的写法，返回点号后整段；
 * 非该前缀或前缀后为空则返回 null。
 */
export function flowKeyFromPath(path: string | null | undefined): string | null {
  const p = String(path ?? '').trim();
  if (!p.startsWith('flow.')) return null;
  const key = p.slice('flow.'.length);
  return key || null;
}

/**
 * 从失败步收集关联 flow 变量：键 → 原值。
 * 来源包括 extracts（scope 为 flow 或缺省）、assigns、script.writes、
 * 以及断言规则 left 上的 flow. 路径；断言若带 leftActual 则优先用实测值，
 * 否则从 flowAfter 取值。不读取 right，不回退整份快照。
 */
export function pickRelatedFlowVars(step: SummarizeStepInput | null | undefined): RelatedFlowVar[] {
  if (!step) return [];
  const flowAfter =
    step.flowAfter && typeof step.flowAfter === 'object' ? step.flowAfter : null;
  const values = new Map<string, unknown>();

  const takeFromFlowAfter = (key: string) => {
    if (!flowAfter || !Object.prototype.hasOwnProperty.call(flowAfter, key)) return;
    if (!values.has(key)) values.set(key, flowAfter[key]);
  };

  for (const e of step.extracts || []) {
    if (!e?.name) continue;
    const scope = (e.scope || 'flow').trim().toLowerCase();
    if (scope === 'flow') takeFromFlowAfter(e.name);
  }
  for (const a of step.assigns || []) {
    if (a?.name) takeFromFlowAfter(a.name);
  }
  for (const w of step.script?.writes || []) {
    if (w?.key) takeFromFlowAfter(w.key);
  }
  for (const r of step.assert?.rules || []) {
    const key = flowKeyFromPath(r?.left != null ? String(r.left) : '');
    if (!key) continue;
    if (r != null && Object.prototype.hasOwnProperty.call(r, 'leftActual')) {
      values.set(key, r.leftActual);
    } else {
      takeFromFlowAfter(key);
    }
  }

  return Array.from(values.entries()).map(([key, value]) => ({ key, value }));
}

/**
 * 关联变量值的短展示：标量原样；对象 / 数组截断 JSON。
 */
export function formatRelatedVarValue(value: unknown, max = RELATED_VAR_VALUE_MAX): string {
  if (value === undefined) return 'undefined';
  if (value === null) return 'null';
  const t = typeof value;
  if (t === 'string' || t === 'number' || t === 'boolean') {
    return truncateText(String(value), max);
  }
  try {
    return truncateText(JSON.stringify(value), max);
  } catch {
    return truncateText(String(value), max);
  }
}

/**
 * 业务码失败时的一行摘要。
 * 优先「业务码 {实际码}: {消息}」；缺省时退回错误消息或固定文案。
 */
export function formatBizCheckFailure(step: SummarizeStepInput | null | undefined): string {
  const bc = step?.http?.bizCheck;
  const msg = bc?.message || step?.error?.message || '';
  const code = bc?.actualCode != null ? String(bc.actualCode) : '';
  if (code && msg) return `业务码 ${code}: ${msg}`;
  if (code) return `业务码 ${code} 失败`;
  return step?.error?.message || '业务码校验失败';
}

/**
 * 步骤时间线副标题：按节点类型给出短摘要。
 * 业务码失败、错误消息、HTTP、断言、赋值、脚本、子流、场景加载等各有格式；
 * 明细仍由 Inspector 摘要 Tab 展示。
 */
export function summarizeStep(step: SummarizeStepInput | null | undefined): string {
  if (!step) return '-';
  if (isBizCodeFailure(step)) {
    return formatBizCheckFailure(step);
  }
  if (step.error?.message) return truncateText(step.error.message, 120);
  if (step.nodeType === 'subflow' && step.subflow) {
    const childSteps = step.subflow.childSteps || [];
    const failed = childSteps.find((c) => c.status === 'failed');
    if (failed) return `子流内失败: ${failed.nodeName || failed.nodeType}`;
    return `子流 ${childSteps.length} 步`;
  }
  if (step.nodeType === 'http' && step.http) {
    const prefix = step.http.callMode === 'external' ? '↗ ' : '';
    const bizOk = step.http.bizCheck?.passed === true ? ' · 业务码通过' : '';
    const url = truncateText(decodeUrlForDisplay(step.http.url || ''), 48);
    const urlPart = url ? ` ${url}` : '';
    return `${prefix}${step.http.method || 'HTTP'}${urlPart} → ${step.http.status}${bizOk}`;
  }
  if (step.nodeType === 'assert' && step.assert) {
    const rules = step.assert.rules || [];
    const failed = rules.filter((r) => !r.passed);
    if (failed.length) return `断言失败 ${failed.length}/${rules.length}`;
    return rules.length ? `断言通过 ${rules.length} 条` : '断言通过';
  }
  if (step.nodeType === 'delay') return `等待 ${formatDurationMs(step.durationMs)}`;
  if (step.nodeType === 'input' && step.assigns?.length) {
    return summarizeAssignKeys(step.assigns, '录入');
  }
  if (step.nodeType === 'input' && step.status === 'paused') {
    return '等待人工输入';
  }
  if (step.nodeType === 'assign' && step.assigns?.length) {
    return summarizeAssignKeys(step.assigns, '写入');
  }
  if (step.nodeType === 'script' && step.script?.writes?.length) {
    const keys = step.script.writes.map((w) => w.key).filter((k): k is string => !!k);
    return summarizeKeyList(keys, '写入');
  }
  if (step.nodeType === 'script' && step.script?.language) {
    return `${step.script.language} 脚本`;
  }
  if (step.nodeType === 'runConfig') {
    const sl = step.scenarioLoaded;
    if (sl) {
      const flowSeed = sl.flowSeed && typeof sl.flowSeed === 'object' ? sl.flowSeed : {};
      const flowCount = Object.keys(flowSeed).length;
      const scenarioLabel = sl.scenarioName || sl.scenarioId || '场景';
      const envLabel = sl.envName || '环境';
      return `${scenarioLabel} · ${envLabel} · ${flowCount} 个 flow 初值`;
    }
    return '场景加载';
  }
  return runStatusLabel(step.status);
}
