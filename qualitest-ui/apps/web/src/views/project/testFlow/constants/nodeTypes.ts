/**
 * 节点库与画布卡片的类型元数据：主题色、默认 data、断言预设。
 * NODE_TYPES.defaults 在 createNodeData 时作为新节点初始值。
 *
 * 画布配色规则：每种节点仅一种主色（NODE_COLORS），顶栏/徽章/chip 等均继承 --node-accent。
 * flow/env/asset 作用域色仅用于属性面板编辑区（FlowScopeBadge），不在画布节点内混色。
 */
import { nextSnowflakeId } from '@/utils/flow/snowflakeId';
import { DELAY_DEFAULT_MS } from '@/utils/flow/delayConstants';
import { SCRIPT_DEFAULT_TIMEOUT_MS } from './flowConfig';

/** 各节点类型的主题色（画布单色来源） */
export const NODE_COLORS = {
  http: '#0b6edc',
  assert: '#16a34a',
  delay: '#64748b',
  condition: '#c2410c',
  assign: '#9333ea',
  script: '#0891b2',
  subflow: '#6366f1',
} as const;

export interface NodeTypeConfig {
  label: string;
  color: string;
  icon: string;
  desc: string;
  defaults: Record<string, unknown>;
}

/** 画布节点类型 key，须与 nodeRegistry 保持一致 */
export type FlowNodeTypeKey = 'http' | 'assert' | 'delay' | 'condition' | 'assign' | 'script' | 'subflow';

export const NODE_TYPES: Record<FlowNodeTypeKey, NodeTypeConfig> = {
  http: {
    label: 'HTTP',
    color: NODE_COLORS.http,
    icon: 'HTTP',
    desc: '调用项目接口 · 含响应提取',
    defaults: {
      name: 'HTTP 请求',
      callMode: 'project',
      testProjectApiId: '',
      timeoutMs: 30000,
      extracts: [],
      summary: '请选择项目接口',
    },
  },
  assert: {
    label: 'Assert',
    color: NODE_COLORS.assert,
    icon: 'AST',
    desc: '断言校验',
    defaults: {
      name: '断言',
      rules: [{ left: 'http.body.data.code', operator: 'eq', right: '0' }],
      summary: 'http.body.data.code 等于 0',
    },
  },
  delay: {
    label: 'Delay',
    color: NODE_COLORS.delay,
    icon: 'DEL',
    desc: '等待毫秒',
    defaults: {
      name: '等待',
      ms: DELAY_DEFAULT_MS,
      summary: `${DELAY_DEFAULT_MS} ms`,
    },
  },
  condition: {
    label: 'Condition',
    color: NODE_COLORS.condition,
    icon: 'IF',
    desc: 'IF / ELIF / ELSE 分支',
    defaults: {
      name: '条件分支',
      branches: null,
      summary: 'IF · ELSE',
    },
  },
  assign: {
    label: 'Assign',
    color: NODE_COLORS.assign,
    icon: 'ASN',
    desc: '写入 flow 变量 · = += -= *= /=',
    defaults: {
      name: '变量赋值',
      assignments: [{ scope: 'flow', name: 'pollAttempt', op: 'set', value: '0' }],
      summary: 'flow.pollAttempt ← 0',
    },
  },
  script: {
    label: 'Script',
    color: NODE_COLORS.script,
    icon: 'SCR',
    desc: '受限脚本 · flow 变量拼装 / JSON / 签名',
    defaults: {
      name: '脚本',
      language: 'javascript',
      source: "ctx.setFlow('demo', ctx.jsonStringify({ ok: true }));",
      timeoutMs: SCRIPT_DEFAULT_TIMEOUT_MS,
      summary: 'JavaScript · 未配置源码',
    },
  },
  subflow: {
    label: 'Subflow',
    color: NODE_COLORS.subflow,
    icon: 'SUB',
    desc: '引用子流 · 登录 / OAuth 等复用块',
    defaults: {
      name: '子流',
      subflowId: '',
      versionPolicy: 'pinned',
      inputs: [],
      outputs: [],
      summary: '请选择子流',
    },
  },
};

/** assign 节点快捷预设：轮询计数与标志位 */
export const ASSIGN_PRESETS = [
  { id: 'set0', label: '计数清零', assignment: { scope: 'flow', name: 'pollAttempt', op: 'set', value: '0' } },
  { id: 'add1', label: '计数 +1', assignment: { scope: 'flow', name: 'pollAttempt', op: 'add', step: 1, ifMissing: 0 } },
  { id: 'flag', label: '标记 true', assignment: { scope: 'flow', name: 'ready', op: 'set', value: 'true' } },
];

export const ASSERT_PRESETS = [
  { id: 'code0', label: 'Body code = 0', rule: { left: 'http.body.data.code', operator: 'eq', right: '0' } },
  { id: 'token', label: 'flow.token 存在', rule: { left: 'flow.token', operator: 'exists', right: '' } },
  { id: 'duration', label: '耗时 < 3s', rule: { left: 'http.duration', operator: 'lt', right: '3000' } },
];

export const ASSERT_LEFT_LABELS: Record<string, string> = {
  'http.duration': '耗时(ms)',
};

/** condition 节点默认分支：一条 IF + 一条 ELSE（分支 id 为雪花数字串） */
export function defaultConditionBranches() {
  return [
    { id: nextSnowflakeId(), kind: 'if', conditions: [{ left: 'flow.code', operator: 'eq', right: '0' }] },
    { id: nextSnowflakeId(), kind: 'else', conditions: [] },
  ];
}
