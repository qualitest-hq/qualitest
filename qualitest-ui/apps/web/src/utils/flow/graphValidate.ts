/**
 * 测试流图 JSON 结构校验（设计态）。
 *
 * 检查节点类型、边、开始节点、HTTP 绑定、条件分支等结构问题，产出 errors / warnings。
 */
import {
  isValidJsonPath,
  normalizeAssertLeftPath,
  toAbsoluteJsonPath,
} from './placeholder';
import { isKnownNodeType, nodeTypeLabel } from './nodeTypes';
import type { GraphEdge, GraphJson, GraphNode } from './graphTypes';
import { DELAY_MAX_MS } from './delayConstants';
import { isTerminalBranch } from './conditionBranch';
import { isKnownInputFieldType, requiresOptions } from './inputFields';

const ALLOWED_EDGE_KEYS = new Set(['id', 'source', 'target', 'label']);

const ASSIGN_OPS = new Set(['set', 'add', 'sub', 'mul', 'div']);

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
   * 为 true 时：开始节点唯一性失败写入 warnings 而非 errors。
   * 用于 Staging 尚有未确认连线、落盘过滤图拓扑暂不可信的场景。
   */
  deferTopologyStructureRules?: boolean;
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

function formatStartNodeNames(nodes: GraphNode[], ids: string[]): string {
  return ids
    .map((id) => {
      const node = nodes.find((n) => n.id === id);
      const name = node?.data?.name;
      if (name != null && String(name).trim()) return String(name);
      return nodeTypeLabel(node?.type) || id;
    })
    .join('、');
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
  id: string | undefined,
  data: Record<string, unknown> | undefined,
  errors: string[],
  warnings: string[],
): void {
  const name = data?.name != null ? String(data.name) : id;
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
    if (!hasTestProjectApiId(data)) {
      warnings.push(`HTTP 节点「${name}」未绑定 testProjectApiId`);
    }
    validateHttpExtracts(p, name, data, errors);
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
}

function isAllowedCompareLeftScope(left: string): boolean {
  return (
    left.startsWith('flow.')
    || left.startsWith('env.')
    || left.startsWith('asset.')
    || left.startsWith('http.')
    || left === 'http.body'
  );
}

function stripMustache(raw: string): string {
  const s = String(raw ?? '').trim();
  const m = s.match(/^\{\{\s*(.+?)\s*\}\}$/);
  return m ? m[1].trim() : s;
}

function validateCompareRule(prefix: string, rule: Record<string, unknown>, errors: string[]): void {
  const leftRaw = rule.left;
  let left = leftRaw == null ? '' : String(leftRaw).trim();
  if (!left) {
    errors.push(`${prefix} left 不能为空`);
    return;
  }
  left = stripMustache(left);
  const normalized = normalizeAssertLeftPath(left);
  if (!isAllowedCompareLeftScope(normalized)) {
    errors.push(`${prefix} left 作用域非法（须为 flow./env./asset./http. 或 $.…）：${left}`);
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
  id: string | undefined,
  data: Record<string, unknown> | undefined,
  errors: string[],
): void {
  const name = data?.name != null ? String(data.name) : id;
  const rules = data?.rules;
  if (!Array.isArray(rules) || !rules.length) {
    errors.push(`${p} 断言节点「${name}」rules 不能为空`);
    return;
  }
  rules.forEach((item, i) => {
    if (!item || typeof item !== 'object' || Array.isArray(item)) return;
    validateCompareRule(`${p} 断言节点「${name}」rules[${i}]`, item as Record<string, unknown>, errors);
  });
}

function validateConditionNodeFields(
  p: string,
  id: string | undefined,
  data: Record<string, unknown> | undefined,
  errors: string[],
): void {
  const name = data?.name != null ? String(data.name) : id;
  const branches = data?.branches;
  if (!Array.isArray(branches)) return;
  branches.forEach((branchItem, bi) => {
    if (!branchItem || typeof branchItem !== 'object' || Array.isArray(branchItem)) return;
    const branch = branchItem as Record<string, unknown>;
    const terminal = isTerminalBranch(branch as { terminal?: boolean });
    const kind = String(branch.kind || '').trim();
    const target = branch.target != null ? String(branch.target).trim() : '';
    if (terminal) {
      if (kind === 'else') {
        errors.push(`${p} 条件节点「${name}」ELSE 分支不可设为结束流程`);
      } else if (target) {
        errors.push(`${p} 条件节点「${name}」branches[${bi}] terminal 与 target 不可同时配置`);
      }
    }
    const conditions = branch.conditions;
    if (!Array.isArray(conditions)) return;
    conditions.forEach((cond, ci) => {
      if (!cond || typeof cond !== 'object' || Array.isArray(cond)) return;
      validateCompareRule(
        `${p} 条件节点「${name}」branches[${bi}].conditions[${ci}]`,
        cond as Record<string, unknown>,
        errors,
      );
    });
  });
}

function validateAssignNodeFields(
  p: string,
  id: string | undefined,
  data: Record<string, unknown> | undefined,
  errors: string[],
): void {
  const name = data?.name != null ? String(data.name) : id;
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
  id: string | undefined,
  data: Record<string, unknown> | undefined,
  errors: string[],
): void {
  const name = data?.name != null ? String(data.name) : id;
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
  id: string | undefined,
  data: Record<string, unknown> | undefined,
  errors: string[],
): void {
  const name = data?.name != null ? String(data.name) : id;
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

/** subflowId 缺失为 error；inputs/outputs 空映射合法（asset 跨流 / meta.flowOutputs 回退） */
function validateSubflowNodeFields(
  p: string,
  id: string | undefined,
  data: Record<string, unknown> | undefined,
  errors: string[],
  _warnings: string[],
): void {
  const name = data?.name != null ? String(data.name) : id;
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
  id: string | undefined,
  data: Record<string, unknown> | undefined,
  errors: string[],
  warnings: string[],
): void {
  const name = data?.name != null ? String(data.name) : id;
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

function validateNodeFields(
  p: string,
  node: GraphNode | null | undefined,
  nodeIds: Set<string>,
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

  if (type === 'http') {
    validateHttpNodeFields(p, id, node.data, errors, warnings);
  }
  if (type === 'assert') {
    validateAssertNodeFields(p, id, node.data, errors);
  }
  if (type === 'condition') {
    const branches = node.data?.branches;
    if (!Array.isArray(branches) || !branches.length) {
      const name = node.data?.name != null ? String(node.data.name) : id;
      errors.push(`条件节点「${name}」缺少 branches，请配置 IF/ELSE 分支`);
    } else {
      validateConditionNodeFields(p, id, node.data, errors);
    }
  }
  if (type === 'assign') {
    validateAssignNodeFields(p, id, node.data, errors);
  }
  if (type === 'delay') {
    validateDelayNodeFields(p, id, node.data, errors);
  }
  if (type === 'input') {
    validateInputNodeFields(p, id, node.data, errors);
  }
  if (type === 'subflow') {
    validateSubflowNodeFields(p, id, node.data, errors, warnings);
  }
  if (type === 'script') {
    validateScriptNodeFields(p, id, node.data, errors, warnings);
  }
}

function validateConditionBranches(
  nodes: GraphNode[],
  edges: GraphEdge[],
  warnings: string[],
): void {
  nodes.forEach((n) => {
    if (n.type !== 'condition') return;
    const branches = (n.data?.branches as Array<Record<string, unknown>> | undefined) ?? [];
    branches.forEach((b) => {
      const branchId = b.id;
      const target = b.target;
      if (isTerminalBranch(b as { terminal?: boolean })) return;
      if (!target || String(target).trim() === '') {
        warnings.push(`条件节点 ${n.id} 分支 ${branchId ?? '?'} 未绑定 target`);
      } else if (!hasOutgoingEdge(edges, n.id, String(target))) {
        warnings.push(`条件节点 ${n.id} 分支 ${branchId} 的 target 无对应出边：${target}`);
      }
    });
  });
}

function appendMetaRunError(graph: Record<string, unknown>, errors: string[]): void {
  const meta = graph.meta;
  if (meta == null || typeof meta !== 'object' || Array.isArray(meta)) {
    errors.push('缺少 meta.scenarios');
    return;
  }
  const scenarios = (meta as Record<string, unknown>).scenarios;
  if (!Array.isArray(scenarios) || scenarios.length === 0) {
    errors.push('meta.scenarios 不能为空');
  }
}

/** 校验测试流图 JSON 结构，返回 errors / warnings 列表 */
export function validateGraphJson(
  raw: unknown,
  options?: ValidateGraphJsonOptions,
): GraphValidationResult {
  const errors: string[] = [];
  const warnings: string[] = [];
  const deferTopology = options?.deferTopologyStructureRules === true;

  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    return { ok: false, errors: ['根对象必须是 JSON 对象'], warnings };
  }

  const graph = unwrapGraphPayload(raw as Record<string, unknown>);
  const nodes = graph.nodes;
  const edges = graph.edges;

  if (!Array.isArray(nodes)) errors.push('缺少 nodes 数组');
  if (!Array.isArray(edges)) errors.push('缺少 edges 数组');
  if (errors.length) return { ok: false, errors, warnings };

  const nodeIds = new Set<string>();
  (nodes as GraphNode[]).forEach((node, i) => {
    validateNodeFields(`nodes[${i}]`, node, nodeIds, errors, warnings);
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

  if (!(nodes as GraphNode[]).length) {
    warnings.push('nodes 为空，导入后将得到空白画布');
  }

  return { ok: errors.length === 0, errors, warnings };
}
