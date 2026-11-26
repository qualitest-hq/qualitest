/**
 * 测试流图 JSON 结构校验（前端预检）。
 *
 * 检查节点壳字段（顶层 name 必填）、已知 type、边端点、开始节点、各类型业务字段，只产出 errors。
 * 场景写在 scenario-entry.data.scenarios（envId / inSeed）；meta 不承载场景列表。
 * http-return / subflow-return：from 非空；http-return 的 status 若写出须为 100～599；
 * subflow-return 禁止 status。入口 fallbackOut、HTTP method/path/envId、同序双 return、
 * 返回 type 匹配、本流 method+path 自冲突等记 errors。
 * 画布「异常」(errors) 一般保存不拦、开跑硬拦；本流开放路由自冲突除外（保存也硬拦，与后端一致）。
 * persistMinimalOnly：只检查节点 id 与边端点（保存地板）。
 * deferTopologyStructureRules：暂不校验开始节点（半成品 / 尚有未确认连线）。
 */
import {
  isValidJsonPath,
  looksLikeNodesPath,
  normalizeAssertLeftPath,
  parseNodesHttpPath,
  parseNodesOutPath,
  toAbsoluteJsonPath,
} from './placeholder';
import { isAnyEntryType, isKnownNodeType, nodeTypeLabel } from './nodeTypes';
import type { GraphEdge, GraphJson, GraphNode } from './graphTypes';
import { CURRENT_GRAPH_SCHEMA_VERSION } from './graphTypes';
import { DELAY_MAX_MS } from './delayConstants';
import { RETRY_UNTIL_MAX_ATTEMPTS, RETRY_UNTIL_MAX_INTERVAL_MS } from './retryUntilConstants';
import { isKnownInputFieldType, requiresOptions } from './inputFields';

const ALLOWED_EDGE_KEYS = new Set(['id', 'source', 'target', 'label']);

const ASSIGN_OPS = new Set(['set', 'add', 'sub', 'mul', 'div']);

/** assign 允许写出的 scope：缺省视为 flow；写出其它值按画布异常报错 */
const ASSIGN_SCOPES = new Set(['flow', 'out']);

/** §2.5：data 不得承载画布 UI 状态字段 */
const FORBIDDEN_DATA_UI_KEYS = ['selected', 'dragging', 'highlighted', 'hovered'] as const;

/**
 * 允许写出 alwaysRun 的节点 type：业务步骤统一允许（含 return），入口节点不允许。
 * 数组顺序用于拼错误文案「仅 a/b/c」。
 */
const ALWAYS_RUN_ALLOWED_TYPES = [
  'http',
  'script',
  'assign',
  'subflow',
  'delay',
  'assert',
  'condition',
  'input',
  'pluginAction',
  'http-return',
  'subflow-return',
] as const;
/** alwaysRun 允许类型的斜杠拼接，写入校验错误 */
const ALWAYS_RUN_ALLOWED_LABEL = ALWAYS_RUN_ALLOWED_TYPES.join('/');

/**
 * 允许写出 snapshotBefore 的节点 type：http / script / assign。
 * 数组顺序用于拼错误文案「仅 a/b/c」。
 */
const SNAPSHOT_BEFORE_ALLOWED_TYPES = ['http', 'script', 'assign'] as const;
/** snapshotBefore 允许类型的斜杠拼接，写入校验错误 */
const SNAPSHOT_BEFORE_ALLOWED_LABEL = SNAPSHOT_BEFORE_ALLOWED_TYPES.join('/');

/** 图校验结果；ok 为 true 当且仅当 errors 为空 */
export interface GraphValidationResult {
  ok: boolean;
  errors: string[];
  warnings: string[];
}

/** 开始节点校验结果（有节点须有入口；允许多入口并存） */
export interface StartNodesValidation {
  ok: boolean;
  ids: string[];
  /** 首条错误文案；无错误时为空串 */
  message: string;
  /** 全部错误文案（入口规则下可能多条） */
  messages: string[];
}

export type ValidateGraphJsonOptions = {
  /**
   * true：暂不校验开始节点（半成品 / 尚有未确认连线的落盘过滤图）。
   */
  deferTopologyStructureRules?: boolean;
  /**
   * true：只做落库地板（nodes/edges 为数组、节点 id、边端点），
   * 不检查 callMode、开始节点、断言规则等字段细节。
   */
  persistMinimalOnly?: boolean;
};

/** 从 graph_json / graphJson 包装中取出图对象 */
export function unwrapGraphPayload(raw: Record<string, unknown>): Record<string, unknown> {
  if (raw.graph_json != null && typeof raw.graph_json === 'object' && !Array.isArray(raw.graph_json)) {
    return raw.graph_json as Record<string, unknown>;
  }
  if (raw.graphJson != null && typeof raw.graphJson === 'object' && !Array.isArray(raw.graphJson)) {
    return raw.graphJson as Record<string, unknown>;
  }
  return raw;
}

/** 列出无入边节点 id（候选开始节点；同一 id 只计一次） */
export function findStartNodeIds(graph: Pick<GraphJson, 'nodes' | 'edges'>): string[] {
  const nodes = graph.nodes ?? [];
  const edges = graph.edges ?? [];
  if (!nodes.length) return [];
  const hasIncoming = new Set(edges.map((e) => e.target));
  const uniqueIds = [...new Set(nodes.map((n) => n.id).filter((id): id is string => Boolean(id)))];
  return uniqueIds.filter((id) => !hasIncoming.has(id));
}

/** 节点展示名：优先顶层 name，否则类型中文名，再否则 id */
function resolveGraphNodeName(node: Pick<GraphNode, 'id' | 'name' | 'type'> | null | undefined): string {
  const top = node?.name;
  if (top != null && String(top).trim()) return String(top).trim();
  return nodeTypeLabel(node?.type) || String(node?.id ?? '');
}

/** 源 → 出边目标 id 列表（可达性 / 扇出 / 双 return 共用） */
function buildOutgoingTargets(edges: GraphEdge[]): Map<string, string[]> {
  const outs = new Map<string, string[]>();
  for (const e of edges) {
    if (!e?.source || !e?.target) continue;
    const list = outs.get(e.source) ?? [];
    list.push(e.target);
    outs.set(e.source, list);
  }
  return outs;
}

/** 节点 id → 节点 */
function buildNodeById(nodes: GraphNode[]): Map<string | undefined, GraphNode> {
  return new Map(nodes.map((n) => [n.id, n]));
}

/**
 * 校验返回节点：from 必填；http-return 可选 status（有值须为 100～599 整数）；
 * subflow-return 不允许 status。
 */
function validateReturnNodeFields(
  p: string,
  type: string,
  nodeName: string,
  data: Record<string, unknown> | undefined,
  errors: string[],
): void {
  const from = data?.from;
  if (from == null || String(from).trim() === '') {
    errors.push(`${p} ${nodeTypeLabel(type)}节点「${nodeName}」from 不能为空`);
  }
  if (type === 'subflow-return' && data && Object.prototype.hasOwnProperty.call(data, 'status')) {
    errors.push(`${p} subflow-return「${nodeName}」不允许 status`);
    return;
  }
  if (type !== 'http-return' || !data) return;
  if (!Object.prototype.hasOwnProperty.call(data, 'status')) return;
  const statusRaw = data.status;
  if (statusRaw == null || String(statusRaw).trim() === '') return;
  const status = tryParseDelayMs(statusRaw);
  if (status == null) {
    errors.push(`${p} HTTP 返回节点「${nodeName}」status 无法解析`);
    return;
  }
  if (status < 100 || status > 599) {
    errors.push(`${p} HTTP 返回节点「${nodeName}」status 须在 100～599 之间`);
  }
}

/**
 * 校验开始节点。
 * 空白图画布可落盘（nodes 为空不在此报错）。
 * 有节点时必须至少有一个入口；无入边节点必须都是入口，入口本身须无入边，允许多入口并存。
 */
export function validateStartNodes(graph: Pick<GraphJson, 'nodes' | 'edges'>): StartNodesValidation {
  const nodes = graph.nodes ?? [];
  const ids = findStartNodeIds(graph);
  if (!nodes.length) {
    return { ok: true, ids, message: '', messages: [] };
  }

  const messages: string[] = [];
  const hasEntry = nodes.some((n) => isAnyEntryType(n?.type));

  if (!hasEntry) {
    messages.push('须至少配置一个入口节点（场景 / HTTP / 子流入口）才能开跑');
    return {
      ok: false,
      ids,
      message: messages[0],
      messages,
    };
  }

  if (ids.length === 0) {
    messages.push('未找到开始节点（入口须无入边）');
  } else {
    const byId = new Map(nodes.map((n) => [n.id, n]));
    for (const id of ids) {
      const node = byId.get(id);
      if (!node || !isAnyEntryType(node.type)) {
        messages.push(
          `存在非入口的无入边节点：${resolveGraphNodeName(node) || id}（有入口时仅入口可作开始）`,
        );
      }
    }
    for (const node of nodes) {
      if (isAnyEntryType(node?.type) && node.id && !ids.includes(node.id)) {
        messages.push(`入口节点须无入边：${resolveGraphNodeName(node)}`);
      }
    }
  }

  return {
    ok: messages.length === 0,
    ids,
    message: messages[0] ?? '',
    messages,
  };
}

function hasTestProjectApiId(data: Record<string, unknown> | undefined): boolean {
  const value = data?.testProjectApiId;
  return value != null && String(value).trim() !== '';
}

const HTTP_CALL_MODES = new Set(['project', 'external']);

function validateHttpExtracts(
  p: string,
  name: string | undefined,
  data: Record<string, unknown> | undefined,
  errors: string[],
): void {
  const extracts = data?.extracts;
  if (!Array.isArray(extracts) || !extracts.length) return;
  extracts.forEach((item, i) => {
    if (!item || typeof item !== 'object' || Array.isArray(item)) return;
    const row = item as Record<string, unknown>;
    const fromRaw = row.from;
    const from = fromRaw == null ? 'body' : String(fromRaw).trim().toLowerCase();
    if (from !== 'body') return;
    const expr = row.expr == null ? '' : String(row.expr).trim();
    if (!expr) {
      errors.push(`${p} HTTP 节点「${name}」extracts[${i}] body 表达式不能为空`);
      return;
    }
    if (!expr.startsWith('$')) {
      errors.push(`${p} HTTP 节点「${name}」extracts[${i}] body 表达式须以 $ 开头：${expr}`);
      return;
    }
    if (!isValidJsonPath(expr)) {
      errors.push(`${p} HTTP 节点「${name}」extracts[${i}] JsonPath 无法解析：${expr}`);
    }
  });
}

function validateHttpNodeFields(
  p: string,
  nodeName: string,
  data: Record<string, unknown> | undefined,
  nodeTypesById: Map<string, string>,
  errors: string[],
): void {
  const name = nodeName;
  const callModeRaw = data?.callMode;
  if (callModeRaw == null || String(callModeRaw).trim() === '') {
    errors.push(`${p} HTTP 节点「${name}」缺少 callMode`);
    return;
  }
  const callMode = String(callModeRaw).trim();
  if (!HTTP_CALL_MODES.has(callMode)) {
    errors.push(`${p} HTTP 节点「${name}」callMode 无效：${callMode}`);
    return;
  }
  if (callMode === 'project') {
    // project 模式未绑接口不在此告警（由 API 语义健康检查另行提示）
    validateHttpExtracts(p, name, data, errors);
    validateHttpRetryUntil(p, name, data, nodeTypesById, errors);
    return;
  }
  const externalUrl = data?.externalUrl;
  if (externalUrl == null || String(externalUrl).trim() === '') {
    errors.push(`${p} HTTP 节点「${name}」外联模式缺少 externalUrl`);
  }
  const httpMethod = data?.httpMethod;
  if (httpMethod == null || String(httpMethod).trim() === '') {
    errors.push(`${p} HTTP 节点「${name}」外联模式缺少 httpMethod`);
  }
  if (hasTestProjectApiId(data)) {
    errors.push(`${p} HTTP 节点「${name}」外联模式不可填写 testProjectApiId`);
  }
  validateHttpExtracts(p, name, data, errors);
  validateHttpRetryUntil(p, name, data, nodeTypesById, errors);
}

/**
 * 校验 HTTP 节点 data.retryUntil：
 * 须为对象；maxAttempts、intervalMs 可解析且在上下限内；
 * until 若存在须为数组，并对每条规则校验 left 作用域与路径。
 */
function validateHttpRetryUntil(
  p: string,
  name: string | undefined,
  data: Record<string, unknown> | undefined,
  nodeTypesById: Map<string, string>,
  errors: string[],
): void {
  if (!data || !Object.prototype.hasOwnProperty.call(data, 'retryUntil')) return;
  const raw = data.retryUntil;
  if (raw == null) return;
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    errors.push(`${p} HTTP 节点「${name}」retryUntil 须为对象`);
    return;
  }
  const retry = raw as Record<string, unknown>;
  if (retry.maxAttempts != null && String(retry.maxAttempts).trim() !== '') {
    const maxAttempts = tryParseDelayMs(retry.maxAttempts);
    if (maxAttempts == null) {
      errors.push(`${p} HTTP 节点「${name}」retryUntil.maxAttempts 无法解析`);
    } else if (maxAttempts < 1 || maxAttempts > RETRY_UNTIL_MAX_ATTEMPTS) {
      errors.push(
        `${p} HTTP 节点「${name}」retryUntil.maxAttempts 须在 1～${RETRY_UNTIL_MAX_ATTEMPTS} 之间`,
      );
    }
  }
  if (retry.intervalMs != null && String(retry.intervalMs).trim() !== '') {
    const intervalMs = tryParseDelayMs(retry.intervalMs);
    if (intervalMs == null) {
      errors.push(`${p} HTTP 节点「${name}」retryUntil.intervalMs 无法解析`);
    } else if (intervalMs < 0 || intervalMs > RETRY_UNTIL_MAX_INTERVAL_MS) {
      errors.push(
        `${p} HTTP 节点「${name}」retryUntil.intervalMs 超过上限 ${RETRY_UNTIL_MAX_INTERVAL_MS}`,
      );
    }
  }
  const until = retry.until;
  if (until == null) return;
  if (!Array.isArray(until)) {
    errors.push(`${p} HTTP 节点「${name}」retryUntil.until 须为数组`);
    return;
  }
  until.forEach((item, i) => {
    if (!item || typeof item !== 'object' || Array.isArray(item)) return;
    validateCompareRule(
      `${p} HTTP 节点「${name}」retryUntil.until[${i}]`,
      item as Record<string, unknown>,
      nodeTypesById,
      errors,
    );
  });
}

/** 比较左值作用域是否合法：run / flow / env / in / out / asset / http / nodes，或整段 http.body */
function isAllowedCompareLeftScope(left: string): boolean {
  return (
    left.startsWith('run.')
    || left.startsWith('flow.')
    || left.startsWith('env.')
    || left.startsWith('in.')
    || left.startsWith('out.')
    || left.startsWith('asset.')
    || left.startsWith('http.')
    || left.startsWith('nodes.')
    || left === 'http.body'
  );
}

/** 若整段被 {{…}} 包裹则剥壳，得到内部路径 */
function stripMustache(raw: string): string {
  const s = String(raw ?? '').trim();
  const m = s.match(/^\{\{\s*(.+?)\s*\}\}$/);
  return m ? m[1].trim() : s;
}

/**
 * 校验 http 相对段里 body 字段路径。
 * 禁止 body.$…；body. 后须为可解析 JsonPath；仅 body 或非 body 段跳过。
 */
function validateHttpBodyRelativePath(prefix: string, left: string, httpRest: string, errors: string[]): void {
  if (httpRest.startsWith('body.$')) {
    errors.push(`${prefix} left 不可写成 http.body.$.…，请用 http.body.data… 或 $.data…：${left}`);
    return;
  }
  if (httpRest === 'body' || !httpRest.startsWith('body.')) return;
  const relative = httpRest.slice('body.'.length);
  const abs = toAbsoluteJsonPath(relative);
  if (!isValidJsonPath(abs)) {
    errors.push(`${prefix} left JsonPath 无法解析：${left}`);
  }
}

/**
 * 校验 nodes.<id>.http… 引用的目标节点：须存在于图中且类型为 http。
 */
function validateNodesHttpTarget(
  prefix: string,
  left: string,
  nodeId: string,
  nodeTypesById: Map<string, string>,
  errors: string[],
): void {
  const targetType = nodeTypesById.get(nodeId);
  if (targetType == null) {
    errors.push(`${prefix} left 引用的节点不存在：${nodeId}（${left}）`);
    return;
  }
  if (String(targetType).trim().toLowerCase() !== 'http') {
    errors.push(
      `${prefix} left 只能引用 HTTP 节点，节点「${nodeId}」类型为 ${targetType || '(空)'}（${left}）`,
    );
  }
}

/**
 * 校验 nodes.<id>.out… 引用的目标节点：须存在于图中且类型为 subflow。
 */
function validateNodesOutTarget(
  prefix: string,
  left: string,
  nodeId: string,
  nodeTypesById: Map<string, string>,
  errors: string[],
): void {
  const targetType = nodeTypesById.get(nodeId);
  if (targetType == null) {
    errors.push(`${prefix} left 引用的节点不存在：${nodeId}（${left}）`);
    return;
  }
  if (String(targetType).trim().toLowerCase() !== 'subflow') {
    errors.push(
      `${prefix} left 的 .out 只能引用子流节点，节点「${nodeId}」类型为 ${targetType || '(空)'}（${left}）`,
    );
  }
}

/**
 * 校验单条比较规则左值：非空、作用域合法；
 * nodes 可为 .http…（目标 HTTP）或 .out…（目标子流）；body JsonPath 可解析。
 */
function validateCompareRule(
  prefix: string,
  rule: Record<string, unknown>,
  nodeTypesById: Map<string, string>,
  errors: string[],
): void {
  const leftRaw = rule.left;
  let left = leftRaw == null ? '' : String(leftRaw).trim();
  if (!left) {
    errors.push(`${prefix} left 不能为空`);
    return;
  }
  left = stripMustache(left);
  const normalized = normalizeAssertLeftPath(left);
  if (!isAllowedCompareLeftScope(normalized)) {
    errors.push(
      `${prefix} left 作用域非法（须为 run./flow./env./in./out./asset./http./nodes.<id>.http.|out. 或 $.…）：${left}`,
    );
    return;
  }
  if (looksLikeNodesPath(normalized)) {
    const nodesOut = parseNodesOutPath(normalized);
    if (nodesOut) {
      validateNodesOutTarget(prefix, left, nodesOut.nodeId, nodeTypesById, errors);
      return;
    }
    const nodesRef = parseNodesHttpPath(normalized);
    if (!nodesRef) {
      errors.push(`${prefix} left 须为 nodes.<节点ID>.http.… 或 nodes.<节点ID>.out…：${left}`);
      return;
    }
    validateNodesHttpTarget(prefix, left, nodesRef.nodeId, nodeTypesById, errors);
    validateHttpBodyRelativePath(prefix, left, nodesRef.httpRest, errors);
    return;
  }
  if (normalized.startsWith('http.body.$')) {
    errors.push(`${prefix} left 不可写成 http.body.$.…，请用 http.body.data… 或 $.data…：${left}`);
    return;
  }
  if (normalized === 'http.body') return;
  if (normalized.startsWith('http.body.')) {
    const relative = normalized.slice('http.body.'.length);
    const abs = toAbsoluteJsonPath(relative);
    if (!isValidJsonPath(abs)) {
      errors.push(`${prefix} left JsonPath 无法解析：${left}`);
    }
  }
}

function validateAssertNodeFields(
  p: string,
  nodeName: string,
  data: Record<string, unknown> | undefined,
  nodeTypesById: Map<string, string>,
  errors: string[],
): void {
  const name = nodeName;
  const rules = data?.rules;
  if (!Array.isArray(rules) || !rules.length) {
    errors.push(`${p} 断言节点「${name}」rules 不能为空`);
    return;
  }
  rules.forEach((item, i) => {
    if (!item || typeof item !== 'object' || Array.isArray(item)) return;
    validateCompareRule(
      `${p} 断言节点「${name}」rules[${i}]`,
      item as Record<string, unknown>,
      nodeTypesById,
      errors,
    );
  });
}

function validateConditionNodeFields(
  p: string,
  nodeName: string,
  data: Record<string, unknown> | undefined,
  nodeTypesById: Map<string, string>,
  errors: string[],
): void {
  const name = nodeName;
  const branches = data?.branches;
  if (!Array.isArray(branches)) return;
  branches.forEach((branchItem, bi) => {
    if (!branchItem || typeof branchItem !== 'object' || Array.isArray(branchItem)) return;
    const branch = branchItem as Record<string, unknown>;
    // 无 target：本分支结束本流；此处只校 conditions 规则
    const conditions = branch.conditions;
    if (!Array.isArray(conditions)) return;
    conditions.forEach((cond, ci) => {
      if (!cond || typeof cond !== 'object' || Array.isArray(cond)) return;
      validateCompareRule(
        `${p} 条件节点「${name}」branches[${bi}].conditions[${ci}]`,
        cond as Record<string, unknown>,
        nodeTypesById,
        errors,
      );
    });
  });
}

function validateAssignNodeFields(
  p: string,
  nodeName: string,
  data: Record<string, unknown> | undefined,
  errors: string[],
): void {
  const name = nodeName;
  const assignments = data?.assignments;
  if (!Array.isArray(assignments) || !assignments.length) {
    errors.push(`${p} Assign 节点「${name}」assignments 不能为空`);
    return;
  }
  assignments.forEach((item, i) => {
    if (!item || typeof item !== 'object' || Array.isArray(item)) {
      errors.push(`${p} Assign 节点「${name}」assignments[${i}] 不是有效对象`);
      return;
    }
    const row = item as Record<string, unknown>;
    const varName = row.name == null ? '' : String(row.name).trim();
    if (!varName) {
      errors.push(`${p} Assign 节点「${name}」assignments[${i}] name 不能为空`);
    }
    const op = row.op == null ? '' : String(row.op).trim();
    if (!op) {
      errors.push(`${p} Assign 节点「${name}」assignments[${i}] op 无效：(空)`);
    } else if (!ASSIGN_OPS.has(op)) {
      errors.push(`${p} Assign 节点「${name}」assignments[${i}] op 无效：${op}`);
    }
    // scope 缺省按 flow；一旦写出只允许 flow / out（去空白、忽略大小写，与运行时归一一致）
    if ('scope' in row) {
      const rawScope = row.scope;
      const scope = typeof rawScope === 'string' ? rawScope.trim().toLowerCase() : '';
      if (typeof rawScope !== 'string' || !ASSIGN_SCOPES.has(scope)) {
        errors.push(
          `${p} Assign 节点「${name}」assignments[${i}] scope 只能是 flow 或 out（当前为：${describeScopeValue(rawScope)}）`,
        );
      }
    }
  });
}

function tryParseDelayMs(raw: unknown): number | null {
  if (raw == null) return null;
  if (typeof raw === 'number' && Number.isFinite(raw)) return Math.trunc(raw);
  const s = String(raw).trim();
  if (!s) return null;
  const n = Number(s);
  if (!Number.isFinite(n)) return null;
  return Math.trunc(n);
}

function validateDelayNodeFields(
  p: string,
  nodeName: string,
  data: Record<string, unknown> | undefined,
  errors: string[],
): void {
  const name = nodeName;
  const ms = tryParseDelayMs(data?.ms);
  if (ms == null) {
    errors.push(`${p} Delay 节点「${name}」缺少 ms 或无法解析`);
    return;
  }
  if (ms > DELAY_MAX_MS) {
    errors.push(`${p} Delay 节点「${name}」ms 超过上限 ${DELAY_MAX_MS}`);
  }
}

/** input：fields 非空；name 唯一；type 合法；select/multiselect 须有 options 且 value 非空 */
function validateInputNodeFields(
  p: string,
  nodeName: string,
  data: Record<string, unknown> | undefined,
  errors: string[],
): void {
  const name = nodeName;
  const raw = data?.fields;
  if (!Array.isArray(raw) || !raw.length) {
    errors.push(`${p} Input 节点「${name}」fields 不能为空`);
    return;
  }
  const fieldNames = new Set<string>();
  raw.forEach((item, i) => {
    const prefix = `${p} Input 节点「${name}」fields[${i}]`;
    if (!item || typeof item !== 'object') {
      errors.push(`${prefix} 不是有效对象`);
      return;
    }
    const row = item as Record<string, unknown>;
    const fieldName = row.name != null ? String(row.name).trim() : '';
    if (!fieldName) {
      errors.push(`${prefix} name 不能为空`);
    } else if (fieldNames.has(fieldName)) {
      errors.push(`${prefix} name 重复：${fieldName}`);
    } else {
      fieldNames.add(fieldName);
    }
    const type = row.type != null ? String(row.type).trim() : '';
    if (type && !isKnownInputFieldType(type)) {
      errors.push(`${prefix} type 无效：${type}`);
    }
    if (requiresOptions(type || 'text')) {
      const options = row.options;
      if (!Array.isArray(options) || !options.length) {
        errors.push(`${prefix} options 不能为空`);
      } else {
        options.forEach((opt, oi) => {
          if (!opt || typeof opt !== 'object') {
            errors.push(`${prefix} options[${oi}] 不是有效对象`);
            return;
          }
          const value = (opt as Record<string, unknown>).value;
          if (value == null || String(value).trim() === '') {
            errors.push(`${prefix} options[${oi}] value 不能为空`);
          }
        });
      }
    }
  });
}

/** subflowId 缺失为 error */
function validateSubflowNodeFields(
  p: string,
  nodeName: string,
  data: Record<string, unknown> | undefined,
  errors: string[],
): void {
  const name = nodeName;
  const subflowId = data?.subflowId;
  if (subflowId == null || String(subflowId).trim() === '') {
    errors.push(`${p} 子流节点「${name}」缺少 subflowId`);
  }
}

const SCRIPT_LANGUAGES = new Set(['javascript', 'python']);

/** script 节点：非法 language 为 error，空 source 为 warning */
function validateScriptNodeFields(
  p: string,
  nodeName: string,
  data: Record<string, unknown> | undefined,
  errors: string[],
  warnings: string[],
): void {
  const name = nodeName;
  const language = data?.language != null ? String(data.language).trim() : '';
  if (!language || !SCRIPT_LANGUAGES.has(language)) {
    errors.push(`${p} script 节点「${name}」language 无效：${language || '(空)'}`);
  }
}

function hasOutgoingEdge(edges: GraphEdge[], source: string, target: string): boolean {
  return edges.some((e) => e.source === source && e.target === target);
}

/**
 * 校验单个节点：id 唯一、type 合法、position、data 对象、顶层 name 非空，
 * 再按 type 分派 HTTP / 断言 / 条件 / 赋值 / 延迟 / 输入 / 子流 / 脚本 / 返回等字段规则。
 * 旧 type「return」一律报错，须改用 http-return 或 subflow-return。
 */
function validateNodeFields(
  p: string,
  node: GraphNode | null | undefined,
  nodeIds: Set<string>,
  nodeTypesById: Map<string, string>,
  errors: string[],
  warnings: string[],
): void {
  if (!node || typeof node !== 'object') {
    errors.push(`${p} 不是有效对象`);
    return;
  }
  const { id, type } = node;
  if (!id || typeof id !== 'string') {
    errors.push(`${p} 缺少 id`);
  } else if (nodeIds.has(id)) {
    errors.push(`${p} id 重复：${id}`);
  } else {
    nodeIds.add(id);
  }

  if (!type || !isKnownNodeType(type)) {
    errors.push(`${p} type 无效：${type || '(空)'}`);
  }
  const pos = node.position;
  if (!pos || typeof pos.x !== 'number' || typeof pos.y !== 'number') {
    errors.push(`${p} position 需包含数字 x / y`);
  }
  if (!node.data || typeof node.data !== 'object') {
    errors.push(`${p} 缺少 data 对象`);
  }

  const nodeName = resolveGraphNodeName(node as GraphNode);
  if (!node.name || !String(node.name).trim()) {
    errors.push(`${p} 节点缺少非空 name`);
  }
  if (node.data && typeof node.data === 'object' && !Array.isArray(node.data)) {
    appendDataUiStateErrors(p, nodeName, node.data as Record<string, unknown>, errors);
  }
  if (type === 'return') {
    errors.push(`${p} type 无效：return（请使用 http-return 或 subflow-return）`);
  }
  if (type === 'http') {
    validateHttpNodeFields(p, nodeName, node.data, nodeTypesById, errors);
  } else if (node.data && Object.prototype.hasOwnProperty.call(node.data, 'retryUntil')) {
    errors.push(`${p} 节点「${nodeName}」retryUntil 仅允许挂在 HTTP 节点`);
  }
  if (type === 'assert') {
    validateAssertNodeFields(p, nodeName, node.data, nodeTypesById, errors);
  }
  if (type === 'condition') {
    const branches = node.data?.branches;
    if (!Array.isArray(branches) || !branches.length) {
      errors.push(`条件节点「${nodeName}」缺少 branches，请配置 IF/ELSE 分支`);
    } else {
      validateConditionNodeFields(p, nodeName, node.data, nodeTypesById, errors);
    }
  }
  if (type === 'assign') {
    validateAssignNodeFields(p, nodeName, node.data, errors);
  }
  if (type === 'delay') {
    validateDelayNodeFields(p, nodeName, node.data, errors);
  }
  if (type === 'input') {
    validateInputNodeFields(p, nodeName, node.data, errors);
  }
  if (type === 'subflow') {
    validateSubflowNodeFields(p, nodeName, node.data, errors);
  }
  if (type === 'script') {
    validateScriptNodeFields(p, nodeName, node.data, errors, warnings);
  }
  if (type === 'http-return' || type === 'subflow-return') {
    validateReturnNodeFields(p, type, nodeName, node.data, errors);
  }
  // alwaysRun / snapshotBefore：有值须为布尔，且只能挂在允许的节点类型上
  validateBooleanFlags(p, type, node.data, errors);
}

/**
 * 校验节点 data 上的 alwaysRun、snapshotBefore。
 * 键不存在则跳过；挂错节点类型或值不是布尔时写入 errors。
 */
function validateBooleanFlags(
  p: string,
  type: string | undefined,
  data: Record<string, unknown> | null | undefined,
  errors: string[],
): void {
  if (!data || typeof data !== 'object') return;
  validateOneBooleanFlag(p, type, data, 'alwaysRun', ALWAYS_RUN_ALLOWED_TYPES, ALWAYS_RUN_ALLOWED_LABEL, errors);
  validateOneBooleanFlag(
    p,
    type,
    data,
    'snapshotBefore',
    SNAPSHOT_BEFORE_ALLOWED_TYPES,
    SNAPSHOT_BEFORE_ALLOWED_LABEL,
    errors,
  );
}

/**
 * 校验单个布尔开关字段。
 * 键存在时：节点 type 须在允许列表内，值须为 boolean。
 */
function validateOneBooleanFlag(
  p: string,
  type: string | undefined,
  data: Record<string, unknown>,
  key: string,
  allowedTypes: readonly string[],
  allowedLabel: string,
  errors: string[],
): void {
  if (!(key in data)) return;
  const typeNorm = type != null ? String(type).trim().toLowerCase() : '';
  const typeLabel = typeNorm || '(空)';
  if (!allowedTypes.includes(typeNorm)) {
    errors.push(`${p} 节点类型 ${typeLabel} 不允许设置 ${key}（仅 ${allowedLabel}）`);
    return;
  }
  const value = data[key];
  if (typeof value !== 'boolean') {
    errors.push(`${p} ${key} 须为 JSON 布尔（true/false），当前为：${describeFlagValue(value)}`);
  }
}

/** 生成布尔开关非法值的中文描述（字符串 / 数字 / 其它） */
function describeFlagValue(value: unknown): string {
  if (value === null) return 'null';
  if (typeof value === 'string') return `字符串 "${value}"`;
  if (typeof value === 'number') return `数字 ${value}`;
  return typeof value;
}

/** 生成 assign scope 非法值的中文描述（字符串 / 数字 / 对象 / 数组 / null） */
function describeScopeValue(value: unknown): string {
  if (value === null || value === undefined) return 'null';
  if (typeof value === 'string') return `字符串 "${value}"`;
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  if (Array.isArray(value)) return '数组';
  return '对象';
}

/** §2.5：data 只存业务配置，不得承载画布 UI 状态字段 */
function appendDataUiStateErrors(
  p: string,
  name: string,
  data: Record<string, unknown>,
  errors: string[],
): void {
  for (const key of FORBIDDEN_DATA_UI_KEYS) {
    if (key in data) {
      errors.push(`${p} 节点「${name}」data 不得承载画布 UI 状态字段：${key}`);
    }
  }
}

function validateConditionBranches(
  nodes: GraphNode[],
  edges: GraphEdge[],
  errors: string[],
  deferEdgeAlignment: boolean,
): void {
  nodes.forEach((n) => {
    if (n.type !== 'condition') return;
    const name = resolveGraphNodeName(n);
    const branches = (n.data?.branches as Array<Record<string, unknown>> | undefined) ?? [];
    branches.forEach((b) => {
      const branchId = b.id;
      const target = b.target;
      // 无 target：结束出口；有 target 须存在对应出边（半成品阶段延后判定）
      if (!target || String(target).trim() === '') return;
      if (!deferEdgeAlignment && !hasOutgoingEdge(edges, n.id, String(target))) {
        errors.push(`条件节点「${name}」分支 ${branchId} 的 target 无对应出边：${target}`);
      }
    });
    // 反向：条件节点的出边必须被某个 branches.target 认领，否则运行时走不到该目标
    if (!branches.length || deferEdgeAlignment) return;
    const claimed = new Set(
      branches
        .map((b) => (b.target == null ? '' : String(b.target).trim()))
        .filter((t) => !!t),
    );
    for (const edge of edges) {
      if (edge.source !== n.id) continue;
      const target = edge.target == null ? '' : String(edge.target).trim();
      if (target && !claimed.has(target)) {
        errors.push(`条件节点「${name}」出边无对应 branches.target：${target}`);
      }
    }
  });
}

/** 是否为场景入口节点 */
function isScenarioEntryNode(node: unknown): boolean {
  return (
    !!node &&
    typeof node === 'object' &&
    !Array.isArray(node) &&
    (node as Record<string, unknown>).type === 'scenario-entry'
  );
}

/** 是否为任一入口类型节点：场景 / HTTP / 子流等平台入口，或「{协议名}-entry」协议入口 */
function isEntryNode(node: unknown): boolean {
  if (!node || typeof node !== 'object' || Array.isArray(node)) return false;
  const t = (node as Record<string, unknown>).type;
  return typeof t === 'string' && isAnyEntryType(t);
}

/** 场景入口是否已挂非空 scenarios 列表 */
function entryHasScenarios(node: unknown): boolean {
  if (!isScenarioEntryNode(node)) return false;
  const data = (node as Record<string, unknown>).data;
  if (!data || typeof data !== 'object' || Array.isArray(data)) return false;
  const scenarios = (data as Record<string, unknown>).scenarios;
  return Array.isArray(scenarios) && scenarios.length > 0;
}

/** 入口 type 对应的返回节点 type；场景入口无对外返回 */
function expectedReturnTypeForEntry(entryType: string | undefined | null): string | null {
  if (entryType === 'http-entry') return 'http-return';
  if (entryType === 'subflow-entry') return 'subflow-return';
  return null;
}

function isReturnNodeType(type: string | undefined | null): boolean {
  return type === 'http-return' || type === 'subflow-return';
}

/**
 * 入口与返回规则：fallbackOut、场景图禁返回、可达返回 type、同序双 return、入口声明形状。
 */
function appendReturnAndEntryRules(
  nodes: GraphNode[],
  edges: GraphEdge[],
  errors: string[],
  warnings: string[],
): void {
  let hasScenarioEntry = false;
  let hasHttpEntry = false;
  let hasSubflowEntry = false;
  for (const n of nodes) {
    if (!n) continue;
    if (n.type === 'scenario-entry') hasScenarioEntry = true;
    if (n.type === 'http-entry') hasHttpEntry = true;
    if (n.type === 'subflow-entry') hasSubflowEntry = true;
    if (n.type === 'http-entry' || n.type === 'subflow-entry') {
      if (n.type === 'http-entry') {
        appendHttpEntryFieldErrors(n, errors);
        appendHttpInputShapeErrors(n, errors);
      }
      const fb = n.data?.fallbackOut;
      if (fb == null || typeof fb !== 'object' || Array.isArray(fb)) {
        errors.push(`入口节点「${resolveGraphNodeName(n)}」须配置 fallbackOut`);
      } else if (n.type === 'http-entry') {
        appendHttpFallbackOutShapeErrors(n, fb as Record<string, unknown>, errors);
      }
    }
    if (n.type === 'scenario-entry' && n.data?.fallbackOut != null) {
      errors.push(`场景入口「${resolveGraphNodeName(n)}」不得配置 fallbackOut`);
    }
    if (n.type === 'scenario-entry') {
      appendEntryDeclarationShapeErrors(n, 'inSeedKeys', errors);
    }
    if (n.type === 'subflow-entry' && n.data?.inputShape != null) {
      errors.push(
        `子流入口「${resolveGraphNodeName(n)}」须改用 inputDecl（inputShape 仅 HTTP 入口支持）`,
      );
    }
    if (n.type === 'subflow-entry') {
      appendEntryDeclarationShapeErrors(n, 'inputDecl', errors);
    }
  }

  const scenarioOnly = hasScenarioEntry && !hasHttpEntry && !hasSubflowEntry;
  for (const n of nodes) {
    if (!n || !isReturnNodeType(n.type)) continue;
    if (scenarioOnly) {
      errors.push(`场景图不支持返回节点（含「${resolveGraphNodeName(n)}」）`);
    }
  }

  const outs = buildOutgoingTargets(edges);
  const byId = buildNodeById(nodes);

  for (const entry of nodes) {
    if (!entry?.id || !isAnyEntryType(entry.type)) continue;
    const expected = expectedReturnTypeForEntry(entry.type);
    if (entry.type === 'http-entry') {
      detectInputAfterReturn(entry.id, 0, new Set(), byId, outs, errors);
    }
    if (expected) {
      detectReturnTypeMismatch(entry.id, expected, new Set(), byId, outs, errors);
    } else if (entry.type === 'scenario-entry' && !scenarioOnly) {
      // 混图：场景入口可达子图不得含返回节点
      detectScenarioEntryReachableReturn(entry.id, new Set(), byId, outs, errors);
    }
  }

  const startIds = findStartNodeIds({ nodes, edges });
  for (const start of startIds) {
    detectSequentialDoubleReturn(start, 0, new Set(), byId, outs, errors);
  }

  appendFanoutReturnRules(nodes, byId, outs, errors);
  appendHttpRouteSelfCollisionErrors(nodes, errors);
}

/** 与后端 OpenHttpRouteCatalog 对齐的 method 规范化 */
export function normalizeOpenHttpMethod(method: unknown): string {
  const raw = method == null ? '' : String(method).trim();
  return raw ? raw.toUpperCase() : 'GET';
}

/** 与后端 OpenHttpRouteCatalog 对齐的 path 规范化；剥 query/fragment、折叠连续 /；空 → 空串 */
export function normalizeOpenHttpPath(path: unknown): string {
  let p = path == null ? '' : String(path).trim();
  const query = p.indexOf('?');
  if (query >= 0) p = p.slice(0, query);
  const fragment = p.indexOf('#');
  if (fragment >= 0) p = p.slice(0, fragment);
  p = p.trim();
  if (!p) return '';
  if (!p.startsWith('/')) p = `/${p}`;
  while (p.includes('//')) p = p.replace('//', '/');
  while (p.length > 1 && p.endsWith('/')) p = p.slice(0, -1);
  return p;
}

/** HTTP 入口开跑必填：method、非空 path、envId（保存不拦，画布/开跑拦） */
function appendHttpEntryFieldErrors(n: GraphNode, errors: string[]): void {
  const label = resolveGraphNodeName(n);
  const method = n.data?.method == null ? '' : String(n.data.method).trim();
  if (!method) {
    errors.push(`HTTP 入口「${label}」须配置 method`);
  }
  if (!normalizeOpenHttpPath(n.data?.path)) {
    errors.push(`HTTP 入口「${label}」须配置非空 path`);
  }
  const envId = n.data?.envId == null ? '' : String(n.data.envId).trim();
  if (!envId) {
    errors.push(`HTTP 入口「${label}」须配置 envId`);
  }
}

/**
 * 入口声明数组（scenario 的 inSeedKeys / subflow 的 inputDecl）：须为扁平数组，name 必填，
 * 不允许 children 嵌套（与 HTTP inputShape 的三节树分治）。
 */
function appendEntryDeclarationShapeErrors(
  n: GraphNode,
  key: 'inSeedKeys' | 'inputDecl',
  errors: string[],
): void {
  const raw = n.data?.[key];
  if (raw == null) return;
  const label = resolveGraphNodeName(n);
  if (!Array.isArray(raw)) {
    errors.push(`入口「${label}」的 ${key} 须为数组`);
    return;
  }
  raw.forEach((item, i) => {
    if (!item || typeof item !== 'object' || Array.isArray(item)) {
      errors.push(`入口「${label}」${key}[${i}] 须为对象`);
      return;
    }
    const row = item as Record<string, unknown>;
    const name = row.name == null ? '' : String(row.name).trim();
    if (!name) {
      errors.push(`入口「${label}」${key}[${i}].name 不能为空`);
    }
    if ('children' in row) {
      errors.push(`入口「${label}」${key}[${i}] 须为扁平声明，不允许 children`);
    }
  });
}

/** HTTP 入口 inputShape：仅 body/query/headers 三节；body 可嵌套 children，query/headers 扁平 */
function appendHttpInputShapeErrors(n: GraphNode, errors: string[]): void {
  const raw = n.data?.inputShape;
  if (raw == null) return;
  const label = resolveGraphNodeName(n);
  if (typeof raw !== 'object' || Array.isArray(raw)) {
    errors.push(`HTTP 入口「${label}」的 inputShape 须为对象`);
    return;
  }
  const shape = raw as Record<string, unknown>;
  for (const key of Object.keys(shape)) {
    if (!['body', 'query', 'headers'].includes(key)) {
      errors.push(`HTTP 入口「${label}」inputShape 只支持 body / query / headers，出现：${key}`);
    }
  }
  appendShapeSection(label, 'body', shape.body, true, errors);
  appendShapeSection(label, 'query', shape.query, false, errors);
  appendShapeSection(label, 'headers', shape.headers, false, errors);
}

/** 校验 inputShape 的一节（须为数组） */
function appendShapeSection(
  label: string,
  section: string,
  raw: unknown,
  allowChildren: boolean,
  errors: string[],
): void {
  if (raw == null) return;
  if (!Array.isArray(raw)) {
    errors.push(`HTTP 入口「${label}」inputShape.${section} 须为数组`);
    return;
  }
  appendShapeFields(label, `inputShape.${section}`, raw, allowChildren, errors);
}

/** 递归校验字段表：name 必填；仅 body 允许 children */
function appendShapeFields(
  label: string,
  path: string,
  fields: unknown[],
  allowChildren: boolean,
  errors: string[],
): void {
  fields.forEach((item, i) => {
    if (!item || typeof item !== 'object' || Array.isArray(item)) {
      errors.push(`HTTP 入口「${label}」${path}[${i}] 须为对象`);
      return;
    }
    const row = item as Record<string, unknown>;
    const name = row.name == null ? '' : String(row.name).trim();
    if (!name) {
      errors.push(`HTTP 入口「${label}」${path}[${i}].name 不能为空`);
    }
    const children = row.children;
    if (children == null) return;
    if (!allowChildren) {
      errors.push(`HTTP 入口「${label}」${path}[${i}] 须为扁平参数表，不允许 children`);
    } else if (Array.isArray(children)) {
      appendShapeFields(label, `${path}[${name}].children`, children, true, errors);
    } else {
      errors.push(`HTTP 入口「${label}」${path}[${i}].children 须为数组`);
    }
  });
}

/** HTTP 入口 fallbackOut 须含合法 status（100–599）以及 headers / body 键 */
function appendHttpFallbackOutShapeErrors(
  n: GraphNode,
  fb: Record<string, unknown>,
  errors: string[],
): void {
  const label = resolveGraphNodeName(n);
  if (coerceHttpStatus(fb.status) == null) {
    errors.push(`HTTP 入口「${label}」的 fallbackOut.status 须为 100–599 的数字`);
  }
  if (!Object.prototype.hasOwnProperty.call(fb, 'headers')) {
    errors.push(`HTTP 入口「${label}」的 fallbackOut 须含 headers`);
  }
  if (!Object.prototype.hasOwnProperty.call(fb, 'body')) {
    errors.push(`HTTP 入口「${label}」的 fallbackOut 须含 body`);
  }
}

/** 解析 HTTP 状态码；非法或不在 100–599 返回 null */
function coerceHttpStatus(raw: unknown): number | null {
  if (typeof raw === 'number' && Number.isFinite(raw)) {
    const v = Math.trunc(raw);
    return v >= 100 && v <= 599 ? v : null;
  }
  if (typeof raw === 'string' && raw.trim()) {
    const v = Number.parseInt(raw.trim(), 10);
    if (!Number.isNaN(v) && v >= 100 && v <= 599) return v;
  }
  return null;
}

/**
 * 本流 http-entry method+path 自冲突（路由占用）。
 * 供全量校验与保存硬拦共用。
 */
export function collectHttpRouteSelfCollisionErrors(
  nodes: GraphNode[] | undefined | null,
): string[] {
  const errors: string[] = [];
  appendHttpRouteSelfCollisionErrors(nodes ?? [], errors);
  return errors;
}

/** 其它流已占用的开放路由（与后端 PeerRoute 对齐） */
export type OpenHttpPeerRoute = {
  testFlowId?: string | number | null
  flowName?: string | null
  nodeId?: string | null
  nodeName?: string | null
  method?: string | null
  path?: string | null
}

/**
 * 本流 http-entry 与同项目其它流的 method+path 冲突。
 * peers 通常来自 GET /project/testFlow/openHttpRoutes。
 */
export function collectHttpRoutePeerCollisionErrors(
  nodes: GraphNode[] | undefined | null,
  peers: OpenHttpPeerRoute[] | undefined | null,
  currentFlowId?: string | number | null,
): string[] {
  const errors: string[] = [];
  if (!nodes?.length || !peers?.length) return errors;
  const currentId = currentFlowId != null && String(currentFlowId).trim()
    ? String(currentFlowId).trim()
    : '';
  const localOwners = new Map<string, string>();
  for (const n of nodes) {
    if (!n || n.type !== 'http-entry') continue;
    const path = normalizeOpenHttpPath(n.data?.path);
    if (!path) continue;
    const method = normalizeOpenHttpMethod(n.data?.method);
    const key = `${method} ${path}`;
    if (!localOwners.has(key)) {
      localOwners.set(key, resolveGraphNodeName(n));
    }
  }
  for (const [key, localLabel] of localOwners) {
    for (const peer of peers) {
      if (!peer) continue;
      const peerFlowId = peer.testFlowId != null ? String(peer.testFlowId).trim() : '';
      if (currentId && peerFlowId && currentId === peerFlowId) continue;
      const peerPath = normalizeOpenHttpPath(peer.path);
      if (!peerPath) continue;
      const peerKey = `${normalizeOpenHttpMethod(peer.method)} ${peerPath}`;
      if (key !== peerKey) continue;
      const peerFlowLabel = (peer.flowName && String(peer.flowName).trim())
        || (peerFlowId ? `#${peerFlowId}` : '?');
      const peerNodeLabel = (peer.nodeName && String(peer.nodeName).trim())
        || (peer.nodeId && String(peer.nodeId).trim())
        || '?';
      errors.push(
        `开放 HTTP 入口冲突：${key} 已被流「${peerFlowLabel}」的入口「${peerNodeLabel}」占用（本流节点「${localLabel}」）`,
      );
    }
  }
  return errors;
}

function appendHttpRouteSelfCollisionErrors(nodes: GraphNode[], errors: string[]): void {
  const owners = new Map<string, string>();
  for (const n of nodes) {
    if (!n || n.type !== 'http-entry') continue;
    const path = normalizeOpenHttpPath(n.data?.path);
    if (!path) continue;
    const method = normalizeOpenHttpMethod(n.data?.method);
    const key = `${method} ${path}`;
    const label = resolveGraphNodeName(n);
    const previous = owners.get(key);
    if (previous != null) {
      errors.push(
        `开放 HTTP 入口冲突：${key} 在本流被「${previous}」与「${label}」同时占用`,
      );
    } else {
      owners.set(key, label);
    }
  }
}

/** 从入口沿出边：返回节点 type 须匹配 */
function detectReturnTypeMismatch(
  nodeId: string,
  expectedReturnType: string,
  path: Set<string>,
  byId: Map<string | undefined, GraphNode>,
  outs: Map<string, string[]>,
  errors: string[],
): void {
  if (!nodeId || path.has(nodeId)) return;
  const node = byId.get(nodeId);
  if (!node) return;
  if (isReturnNodeType(node.type) && node.type !== expectedReturnType) {
    errors.push(
      `入口可达的返回节点「${nodeId}」类型须为 ${expectedReturnType}（当前为 ${node.type}）`,
    );
  }
  const nextPath = new Set(path);
  nextPath.add(nodeId);
  for (const t of outs.get(nodeId) ?? []) {
    detectReturnTypeMismatch(t, expectedReturnType, nextPath, byId, outs, errors);
  }
}

/** 混图中场景入口可达子图若含返回节点则报错 */
function detectScenarioEntryReachableReturn(
  nodeId: string,
  path: Set<string>,
  byId: Map<string | undefined, GraphNode>,
  outs: Map<string, string[]>,
  errors: string[],
): void {
  if (!nodeId || path.has(nodeId)) return;
  const node = byId.get(nodeId);
  if (!node) return;
  if (isReturnNodeType(node.type)) {
    errors.push(
      `场景入口可达子图不支持返回节点（含「${resolveGraphNodeName(node)}」）；返回节点须放在 run.entryKind 守卫的 IF 臂里（如 run.entryKind eq subflow）`,
    );
    return;
  }
  const nextPath = new Set(path);
  nextPath.add(nodeId);
  const guardedTargets = node.type === 'condition' ? scenarioGuardedTargets(node) : new Set<string>();
  for (const t of outs.get(nodeId) ?? []) {
    if (guardedTargets.has(t)) continue;
    detectScenarioEntryReachableReturn(t, nextPath, byId, outs, errors);
  }
}

/** 运算符归一（与后端 CompareRuleEvaluator 对齐：空 → eq） */
function normalizeGuardOperator(raw: unknown): string {
  const s = raw == null ? '' : String(raw).trim().toLowerCase();
  if (!s) return 'eq';
  if (s === 'equals' || s === 'equal' || s === '==') return 'eq';
  if (s === 'notequals' || s === 'not_equals' || s === 'neq' || s === '!=') return 'ne';
  return s;
}

/** 去掉整段 {{…}} 外壳 */
function stripMustacheText(raw: unknown): string {
  let s = raw == null ? '' : String(raw).trim();
  if (s.startsWith('{{') && s.endsWith('}}') && s.length >= 4) {
    s = s.slice(2, -2).trim();
  }
  return s;
}

/**
 * 条件节点的「场景不可达」目标：分支条件含 run.entryKind eq 非 scenario / ne scenario。
 * 同一目标若还有未守卫的分支指过来，则不算守卫（保守口径，与后端一致）。
 */
function scenarioGuardedTargets(node: GraphNode): Set<string> {
  const branches = Array.isArray(node.data?.branches)
    ? (node.data.branches as Array<Record<string, unknown>>)
    : [];
  const allGuarded = new Map<string, boolean>();
  for (const branch of branches) {
    if (!branch || typeof branch !== 'object') continue;
    const target = branch.target == null ? '' : String(branch.target).trim();
    if (!target) continue;
    const guarded = branchExcludesScenario(branch);
    const previous = allGuarded.get(target);
    allGuarded.set(target, previous === undefined ? guarded : previous && guarded);
  }
  const out = new Set<string>();
  allGuarded.forEach((guarded, target) => {
    if (guarded) out.add(target);
  });
  return out;
}

/** 分支条件是否把 entryKind 锁死为非 scenario */
function branchExcludesScenario(branch: Record<string, unknown>): boolean {
  const conditions = Array.isArray(branch.conditions)
    ? (branch.conditions as Array<Record<string, unknown>>)
    : [];
  for (const condition of conditions) {
    if (!condition || typeof condition !== 'object') continue;
    if (stripMustacheText(condition.left) !== 'run.entryKind') continue;
    const op = normalizeGuardOperator(condition.operator);
    const right = condition.right == null ? '' : String(condition.right).trim();
    if (op === 'eq' && right && right.toLowerCase() !== 'scenario') return true;
    if (op === 'ne' && right.toLowerCase() === 'scenario') return true;
  }
  return false;
}

/** HTTP 入口可达路径上：返回节点之后再出现 Input → 响应已交付，入站无法暂停续跑 */
function detectInputAfterReturn(
  nodeId: string,
  returnsBefore: number,
  path: Set<string>,
  byId: Map<string | undefined, GraphNode>,
  outs: Map<string, string[]>,
  errors: string[],
): void {
  if (!nodeId || path.has(nodeId)) return;
  const node = byId.get(nodeId);
  if (!node) return;
  if (node.type === 'input' && returnsBefore > 0) {
    errors.push(
      `HTTP 入口可达路径上，返回节点之后又出现 Input「${resolveGraphNodeName(node)}」；响应已交付后无法暂停续跑`,
    );
    return;
  }
  const returnsHere = returnsBefore + (isReturnNodeType(node.type) ? 1 : 0);
  const nextPath = new Set(path);
  nextPath.add(nodeId);
  for (const t of outs.get(nodeId) ?? []) {
    detectInputAfterReturn(t, returnsHere, nextPath, byId, outs, errors);
  }
}

/** 同序路径上两个返回节点 */
function detectSequentialDoubleReturn(
  nodeId: string,
  returnsSoFar: number,
  path: Set<string>,
  byId: Map<string | undefined, GraphNode>,
  outs: Map<string, string[]>,
  errors: string[],
): void {
  if (!nodeId || path.has(nodeId)) return;
  const node = byId.get(nodeId);
  if (!node) return;
  let returns = returnsSoFar;
  if (isReturnNodeType(node.type)) {
    returns += 1;
    if (returns > 1) {
      errors.push(`同一路径上存在多个返回节点（含「${nodeId}」）`);
      return;
    }
  }
  const nextPath = new Set(path);
  nextPath.add(nodeId);
  for (const t of outs.get(nodeId) ?? []) {
    detectSequentialDoubleReturn(t, returns, nextPath, byId, outs, errors);
  }
}

/**
 * 隐式扇出：非 condition 且出边≥2 时，至多一臂可达返回节点（避免二次交付）。
 * 错误写入 errors（画布提醒 + 挡开跑；保存不拦）。
 */
function appendFanoutReturnRules(
  nodes: GraphNode[],
  byId: Map<string | undefined, GraphNode>,
  outs: Map<string, string[]>,
  errors: string[],
): void {
  for (const node of nodes) {
    if (!node?.id || node.type === 'condition') continue;
    const targets = outs.get(node.id) ?? [];
    if (targets.length < 2) continue;
    let armsWithReturn = 0;
    for (const t of targets) {
      if (armCanReachReturn(t, byId, outs, new Set())) {
        armsWithReturn++;
      }
    }
    if (armsWithReturn > 1) {
      const name = resolveGraphNodeName(node) || node.id;
      errors.push(`多出边扇出后至多一臂可含返回节点（节点「${name}」）`);
    }
  }
}

/** 从臂起点沿出边（含 condition 全部分支）是否能碰到返回节点 */
function armCanReachReturn(
  nodeId: string,
  byId: Map<string | undefined, GraphNode>,
  outs: Map<string, string[]>,
  path: Set<string>,
): boolean {
  if (!nodeId || path.has(nodeId)) return false;
  const node = byId.get(nodeId);
  if (!node) return false;
  if (isReturnNodeType(node.type)) return true;
  const nextPath = new Set(path);
  nextPath.add(nodeId);
  const targets = outs.get(nodeId) ?? [];
  return targets.some((t) => armCanReachReturn(t, byId, outs, nextPath));
}

/**
 * schemaVersion：缺失不拦（保存会写当前版本）；显式旧版本记错误（不做热升，挡开跑）。
 */
function appendSchemaVersionFinding(
  graph: Record<string, unknown>,
  errors: string[],
): void {
  const meta = graph.meta;
  const version =
    meta != null && typeof meta === 'object' && !Array.isArray(meta)
      ? (meta as Record<string, unknown>).schemaVersion
      : undefined;
  if (version == null) return;
  if (version !== CURRENT_GRAPH_SCHEMA_VERSION) {
    errors.push(
      `meta.schemaVersion 须为 ${CURRENT_GRAPH_SCHEMA_VERSION}（当前为 ${String(version)}，不做旧图热升）`,
    );
  }
}

function appendMetaRunError(
  graph: Record<string, unknown>,
  errors: string[],
  deferTopology: boolean,
): void {
  const nodes = Array.isArray(graph.nodes) ? graph.nodes : [];
  if (!nodes.length || deferTopology) {
    return;
  }
  // 每个场景入口各自须挂非空 scenarios（多入口不能互相顶替）
  for (const n of nodes) {
    if (!isScenarioEntryNode(n)) continue;
    if (entryHasScenarios(n)) continue;
    const name =
      n && typeof n === 'object' && !Array.isArray(n)
        ? resolveGraphNodeName(n as GraphNode)
        : 'scenario-entry';
    errors.push(`场景入口「${name}」须配置非空 data.scenarios（含 inSeed）`);
  }
}

/** 校验测试流图 JSON；按 options 选择完整规则或仅落库地板 */
export function validateGraphJson(
  raw: unknown,
  options?: ValidateGraphJsonOptions,
): GraphValidationResult {
  const errors: string[] = [];
  const warnings: string[] = [];
  const deferTopology = options?.deferTopologyStructureRules === true;
  const persistMinimal = options?.persistMinimalOnly === true;

  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    return { ok: false, errors: ['根对象必须是 JSON 对象'], warnings };
  }

  const graph = unwrapGraphPayload(raw as Record<string, unknown>);
  const nodes = graph.nodes;
  const edges = graph.edges;

  if (!Array.isArray(nodes)) errors.push('缺少 nodes 数组');
  if (!Array.isArray(edges)) errors.push('缺少 edges 数组');
  if (errors.length) return { ok: false, errors, warnings };

  if (persistMinimal) {
    const nodeIds = new Set<string>();
    (nodes as GraphNode[]).forEach((node, i) => {
      const p = `nodes[${i}]`;
      if (!node || typeof node !== 'object') {
        errors.push(`${p} 不是有效对象`);
        return;
      }
      const id = node.id;
      if (!id || typeof id !== 'string' || !id.trim()) {
        errors.push(`${p} 缺少 id`);
        return;
      }
      if (nodeIds.has(id)) {
        errors.push(`nodes 存在重复 id：${id}`);
      } else {
        nodeIds.add(id);
      }
    });
    (edges as GraphEdge[]).forEach((edge, i) => {
      const p = `edges[${i}]`;
      if (!edge || typeof edge !== 'object') {
        errors.push(`${p} 不是有效对象`);
        return;
      }
      if (!edge.source) errors.push(`${p} 缺少 source`);
      if (!edge.target) errors.push(`${p} 缺少 target`);
    });
    return { ok: errors.length === 0, errors, warnings };
  }

  const nodeIds = new Set<string>();
  const nodeTypesById = new Map<string, string>();
  (nodes as GraphNode[]).forEach((node) => {
    if (node?.id && typeof node.id === 'string') {
      nodeTypesById.set(node.id, String(node.type ?? ''));
    }
  });
  (nodes as GraphNode[]).forEach((node, i) => {
    validateNodeFields(`nodes[${i}]`, node, nodeIds, nodeTypesById, errors, warnings);
  });

  const edgeIds = new Set<string>();
  (edges as GraphEdge[]).forEach((edge, i) => {
    const p = `edges[${i}]`;
    if (!edge || typeof edge !== 'object') {
      errors.push(`${p} 不是有效对象`);
      return;
    }
    const { id, source, target } = edge;
    if (!id || typeof id !== 'string') {
      errors.push(`${p} 缺少 id`);
    } else if (edgeIds.has(id)) {
      errors.push(`${p} id 重复：${id}`);
    } else {
      edgeIds.add(id);
    }
    if (!source) {
      errors.push(`${p} 缺少 source`);
    } else if (!nodeIds.has(source)) {
      errors.push(`${p} source 不存在：${source}`);
    }
    if (!target) {
      errors.push(`${p} 缺少 target`);
    } else if (!nodeIds.has(target)) {
      errors.push(`${p} target 不存在：${target}`);
    }
    Object.keys(edge as Record<string, unknown>).forEach((key) => {
      if (!ALLOWED_EDGE_KEYS.has(key)) {
        errors.push(`边 ${id || i} 含不允许的字段：${key}`);
      }
    });
  });

  validateConditionBranches(nodes as GraphNode[], edges as GraphEdge[], errors, deferTopology);

  const startCheck = validateStartNodes({ nodes: nodes as GraphNode[], edges: edges as GraphEdge[] });
  if (!startCheck.ok && !deferTopology) {
    errors.push(...startCheck.messages);
  }

  appendSchemaVersionFinding(graph, errors);
  appendMetaRunError(graph, errors, deferTopology);
  appendReturnAndEntryRules(
    nodes as GraphNode[],
    edges as GraphEdge[],
    errors,
    warnings,
  );

  return { ok: errors.length === 0, errors, warnings };
}
