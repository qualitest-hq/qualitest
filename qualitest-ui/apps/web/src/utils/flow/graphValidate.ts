/**
 * 测试流图 JSON 结构校验（前端预检）。
 *
 * 检查节点壳字段（顶层 name 必填）、已知 type、边端点、开始节点、各类型业务字段，产出 errors / warnings。
 * 场景只允许写在 scenario-entry.data.scenarios；meta 上若出现 scenarios / activeScenarioId / flowOutputs 记错误。
 * 场景条目禁止 testProjectEnvId / flowSeed，应使用 envId / inSeed。
 * http-return / subflow-return：from 非空；http-return 的 status 若写出须为 100～599。
 * 另校验 alwaysRun、snapshotBefore（有值须为布尔且类型允许）、HTTP retryUntil。
 * persistMinimalOnly：只检查节点 id 与边端点。
 * deferTopologyStructureRules：开始节点问题降为警告。
 */
import {
  isValidJsonPath,
  looksLikeNodesPath,
  normalizeAssertLeftPath,
  parseNodesHttpPath,
  toAbsoluteJsonPath,
} from './placeholder';
import { isAnyEntryType, isKnownNodeType, nodeTypeLabel } from './nodeTypes';
import type { GraphEdge, GraphJson, GraphNode } from './graphTypes';
import { DELAY_MAX_MS } from './delayConstants';
import { RETRY_UNTIL_MAX_ATTEMPTS, RETRY_UNTIL_MAX_INTERVAL_MS } from './retryUntilConstants';
import { isKnownInputFieldType, requiresOptions } from './inputFields';

const ALLOWED_EDGE_KEYS = new Set(['id', 'source', 'target', 'label']);

const ASSIGN_OPS = new Set(['set', 'add', 'sub', 'mul', 'div']);

/**
 * 允许写出 alwaysRun 的节点 type：http / script / assign / subflow。
 * 数组顺序用于拼错误文案「仅 a/b/c」。
 */
const ALWAYS_RUN_ALLOWED_TYPES = ['http', 'script', 'assign', 'subflow'] as const;
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

/** 开始节点唯一性校验结果 */
export interface StartNodesValidation {
  ok: boolean;
  ids: string[];
  message: string;
}

/**
 * 存在未确认连线时，开始节点「看起来像多个入口」的提示文案（降为 warning，不硬拦保存预览）。
 */
export const DEFERRED_MULTI_START_WARNING =
  '尚有未确认的连线；确认边之后将只保留一个开始节点（当前看起来像多个入口）。';

/**
 * 存在未确认连线时，开始节点「看起来缺失」的提示文案（降为 warning）。
 */
export const DEFERRED_NO_START_WARNING =
  '尚有未确认的连线；确认边之后再校验开始节点（当前每个节点都有入边或图不完整）。';

/**
 * 保存路径：若错误文案涉及开始节点，且业务上属于「pending 连线导致的假象」，则改写成延后提示文案。
 * 不匹配则原样返回。
 */
export function rewriteStartNodeErrorForPendingEdges(error: string): string {
  const text = String(error ?? '').trim();
  if (!/开始节点/.test(text)) return text;
  return /只能有一个开始节点/.test(text)
    ? DEFERRED_MULTI_START_WARNING
    : DEFERRED_NO_START_WARNING;
}

export type ValidateGraphJsonOptions = {
  /**
   * true：开始节点不唯一/缺失时写入 warnings，不写入 errors。
   * 用于尚有未确认连线、落盘过滤图拓扑暂不可信。
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

/** 把多个开始节点 id 拼成「展示名」列表，写入错误文案 */
function formatStartNodeNames(nodes: GraphNode[], ids: string[]): string {
  return ids
    .map((id) => {
      const node = nodes.find((n) => n.id === id);
      return resolveGraphNodeName(node) || id;
    })
    .join('、');
}

/**
 * 校验返回节点：from 必填；http-return 可选 status（有值须为 100～599 整数）。
 * subflow-return 不读 status。
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

/** 校验流程是否恰有一个开始节点（无入边节点） */
export function validateStartNodes(graph: Pick<GraphJson, 'nodes' | 'edges'>): StartNodesValidation {
  const nodes = graph.nodes ?? [];
  const ids = findStartNodeIds(graph);
  if (!nodes.length) {
    return { ok: true, ids, message: '' };
  }
  if (ids.length === 0) {
    return {
      ok: false,
      ids,
      message: '未找到开始节点（每个节点都有入边，可能存在无法触发的子图）',
    };
  }
  if (ids.length > 1) {
    return {
      ok: false,
      ids,
      message: `流程只能有一个开始节点，当前有 ${ids.length} 个：${formatStartNodeNames(nodes, ids)}`,
    };
  }
  return { ok: true, ids, message: '' };
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

/** 比较左值作用域是否合法：flow / env / asset / http / nodes，或整段 http.body */
function isAllowedCompareLeftScope(left: string): boolean {
  return (
    left.startsWith('flow.')
    || left.startsWith('env.')
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
 * 校验单条比较规则左值：非空、作用域合法、nodes 目标存在且为 HTTP、body JsonPath 可解析。
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
    errors.push(`${prefix} left 作用域非法（须为 flow./env./asset./http./nodes.<id>.http. 或 $.…）：${left}`);
    return;
  }
  const nodesRef = parseNodesHttpPath(normalized);
  if (looksLikeNodesPath(normalized) && !nodesRef) {
    errors.push(`${prefix} left 须为 nodes.<节点ID>.http.…：${left}`);
    return;
  }
  if (nodesRef) {
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

/** subflowId 缺失为 error；inputs/outputs 空映射合法 */
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
  const policy = data?.versionPolicy;
  if (policy != null && String(policy).trim() !== '') {
    const pv = String(policy).trim();
    if (pv !== 'pinned' && pv !== 'latest') {
      errors.push(`${p} 子流节点「${name}」versionPolicy 无效：${pv}`);
    }
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
  const source = data?.source != null ? String(data.source) : '';
  if (!source.trim()) {
    warnings.push(`Script 节点「${name}」source 为空`);
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

function validateConditionBranches(
  nodes: GraphNode[],
  edges: GraphEdge[],
  warnings: string[],
): void {
  nodes.forEach((n) => {
    if (n.type !== 'condition') return;
    const name = resolveGraphNodeName(n);
    const branches = (n.data?.branches as Array<Record<string, unknown>> | undefined) ?? [];
    branches.forEach((b) => {
      const branchId = b.id;
      const target = b.target;
      // 无 target：结束出口，不告警；有 target 须存在对应出边
      if (!target || String(target).trim() === '') return;
      if (!hasOutgoingEdge(edges, n.id, String(target))) {
        warnings.push(`条件节点「${name}」分支 ${branchId} 的 target 无对应出边：${target}`);
      }
    });
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

/**
 * 拒绝已废弃的 meta / 场景字段：
 * meta.scenarios、meta.activeScenarioId、meta.flowOutputs；
 * 场景条目上的 testProjectEnvId、flowSeed。
 */
function appendForbiddenLegacyMetaScenarios(graph: Record<string, unknown>, errors: string[]): void {
  const meta = graph.meta;
  if (meta == null || typeof meta !== 'object' || Array.isArray(meta)) return;
  const m = meta as Record<string, unknown>;
  if (Array.isArray(m.scenarios) && m.scenarios.length > 0) {
    errors.push('非法字段 meta.scenarios，请写在 scenario-entry.data.scenarios');
  }
  const activeId = m.activeScenarioId;
  if (activeId != null && String(activeId).trim()) {
    errors.push('非法字段 meta.activeScenarioId');
  }
  if ('flowOutputs' in m) {
    errors.push('非法字段 meta.flowOutputs');
  }
  const nodes = Array.isArray(graph.nodes) ? graph.nodes : [];
  for (const n of nodes) {
    if (!n || typeof n !== 'object') continue;
    const node = n as Record<string, unknown>;
    if (node.type !== 'scenario-entry') continue;
    const data = node.data;
    if (data == null || typeof data !== 'object' || Array.isArray(data)) continue;
    const scenarios = (data as Record<string, unknown>).scenarios;
    if (!Array.isArray(scenarios)) continue;
    scenarios.forEach((sc, j) => {
      if (sc == null || typeof sc !== 'object' || Array.isArray(sc)) return;
      const row = sc as Record<string, unknown>;
      if ('testProjectEnvId' in row) {
        errors.push(`scenarios[${j}] 非法字段 testProjectEnvId，请使用 envId`);
      }
      if ('flowSeed' in row) {
        errors.push(`scenarios[${j}] 非法字段 flowSeed，请使用 inSeed`);
      }
    });
  }
}

/**
 * 检查运行场景是否齐全：须落在 scenario-entry.data.scenarios。
 * 仅有 http-entry / subflow-entry、不依赖人跑场景的图可跳过场景列表。
 */
/**
 * 隐式扇出：非 condition 且出边≥2 时，至多一臂可达返回节点（避免二次交付）。
 * 错误写入 errors，保存/画布预检会展示给用户。
 */
function appendFanoutReturnRules(nodes: GraphNode[], edges: GraphEdge[], errors: string[]): void {
  const outs = new Map<string, string[]>();
  for (const e of edges) {
    if (!e?.source || !e?.target) continue;
    const list = outs.get(e.source) ?? [];
    list.push(e.target);
    outs.set(e.source, list);
  }
  const byId = new Map(nodes.map((n) => [n.id, n]));
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
  if (node.type === 'http-return' || node.type === 'subflow-return') return true;
  const nextPath = new Set(path);
  nextPath.add(nodeId);
  const targets = outs.get(nodeId) ?? [];
  return targets.some((t) => armCanReachReturn(t, byId, outs, nextPath));
}

function appendMetaRunError(graph: Record<string, unknown>, errors: string[]): void {
  appendForbiddenLegacyMetaScenarios(graph, errors);
  const nodes = Array.isArray(graph.nodes) ? graph.nodes : [];
  if (nodes.some(entryHasScenarios)) {
    return;
  }
  const hasScenarioEntry = nodes.some(isScenarioEntryNode);
  if (hasScenarioEntry) {
    errors.push('scenario-entry 须配置非空 data.scenarios（含 inSeed）');
    return;
  }
  const hasAnyEntry = nodes.some(isEntryNode);
  const hasNonScenarioEntry = nodes.some(
    (n) => isEntryNode(n) && !isScenarioEntryNode(n),
  );
  if (hasAnyEntry && hasNonScenarioEntry) {
    return;
  }
  errors.push('缺少运行场景：请在 scenario-entry 配置 scenarios');
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
    if (!(nodes as GraphNode[]).length) {
      warnings.push('nodes 为空，导入后将得到空白画布');
    }
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

  validateConditionBranches(nodes as GraphNode[], edges as GraphEdge[], warnings);

  const startCheck = validateStartNodes({ nodes: nodes as GraphNode[], edges: edges as GraphEdge[] });
  if (!startCheck.ok) {
    if (deferTopology) {
      warnings.push(
        startCheck.ids.length > 1 ? DEFERRED_MULTI_START_WARNING : DEFERRED_NO_START_WARNING,
      );
    } else {
      errors.push(startCheck.message);
    }
  }

  appendMetaRunError(graph, errors);
  appendFanoutReturnRules(nodes as GraphNode[], edges as GraphEdge[], errors);

  if (!(nodes as GraphNode[]).length) {
    warnings.push('nodes 为空，导入后将得到空白画布');
  }

  return { ok: errors.length === 0, errors, warnings };
}
