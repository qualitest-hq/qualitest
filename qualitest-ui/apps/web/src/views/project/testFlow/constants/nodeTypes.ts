/**
 * 节点库与画布卡片的类型元数据：主题色、默认 data、断言预设。
 * NODE_TYPES.defaults 在 createNodeData 时作为新节点初始值；展示名用 NODE_TYPES[type].label。
 *
 * 画布配色规则：每种节点仅一种主色（NODE_COLORS），顶栏/徽章/chip 等均继承 --node-accent。
 * flow/env/asset 作用域色仅用于属性面板编辑区（FlowScopeBadge），不在画布节点内混色。
 */
import { nextSnowflakeId } from '@/utils/flow/snowflakeId';
import { DELAY_DEFAULT_MS } from '@/utils/flow/delayConstants';
import { defaultInputFields } from '@/utils/flow/inputFields';
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
  input: '#d97706',
  'scenario-entry': '#0f766e',
  'http-entry': '#0369a1',
  'subflow-entry': '#4f46e5',
  'http-return': '#0e7490',
  'subflow-return': '#4338ca',
  pluginAction: '#334155',
} as const;

/** 单个节点类型的展示与默认值配置 */
export interface NodeTypeConfig {
  /** 节点库与画布显示的名称 */
  label: string;
  /** 主题色 */
  color: string;
  /** 节点图标缩写文字 */
  icon: string;
  /** 节点库中的一句话说明 */
  desc: string;
  /** 新建该类型节点时写入 data 的初始值 */
  defaults: Record<string, unknown>;
}

/** 画布可创建的节点类型 key（入口 / 业务 / 返回 / 插件动作） */
export type FlowNodeTypeKey =
  | 'http'
  | 'assert'
  | 'delay'
  | 'condition'
  | 'assign'
  | 'script'
  | 'subflow'
  | 'input'
  | 'scenario-entry'
  | 'http-entry'
  | 'subflow-entry'
  | 'http-return'
  | 'subflow-return'
  | 'pluginAction';

/**
 * 各 type 的展示元数据与新建默认 data。
 * 入口默认含 scenarios 或 fallbackOut；返回默认 from=flow.out（http-return 另带 status=200）。
 */
export const NODE_TYPES: Record<FlowNodeTypeKey, NodeTypeConfig> = {
  'scenario-entry': {
    label: '场景入口',
    color: NODE_COLORS['scenario-entry'],
    icon: 'SC',
    desc: '人跑 / 套件 · 场景列表与 inSeed',
    defaults: {
      doc: '',
      maxSteps: 200,
      scenarios: [],
    },
  },
  'http-entry': {
    label: 'HTTP 入口',
    color: NODE_COLORS['http-entry'],
    icon: 'HE',
    desc: '开放入站 · method/path/fallbackOut',
    defaults: {
      method: 'POST',
      path: '/api/demo',
      envId: '',
      doc: '',
      maxSteps: 200,
      fallbackOut: {
        status: 500,
        headers: { 'Content-Type': 'application/json;charset=UTF-8' },
        body: { code: 'NO_RETURN', message: '未执行到返回节点' },
      },
    },
  },
  'subflow-entry': {
    label: '子流入口',
    color: NODE_COLORS['subflow-entry'],
    icon: 'SE',
    desc: '被父流调用 · fallbackOut',
    defaults: {
      doc: '',
      maxSteps: 200,
      fallbackOut: {
        status: 500,
        headers: { 'Content-Type': 'application/json;charset=UTF-8' },
        body: { code: 'NO_RETURN', message: '未执行到返回节点' },
      },
    },
  },
  'http-return': {
    label: 'HTTP 返回',
    color: NODE_COLORS['http-return'],
    icon: 'HR',
    desc: '定稿 out 并立刻写出',
    defaults: {
      from: 'flow.out',
      status: 200,
    },
  },
  'subflow-return': {
    label: '子流返回',
    color: NODE_COLORS['subflow-return'],
    icon: 'SR',
    desc: '定稿 out 并挂父节点',
    defaults: {
      from: 'flow.out',
    },
  },
  http: {
    label: 'HTTP',
    color: NODE_COLORS.http,
    icon: 'HTTP',
    desc: '调用项目接口 · 含响应提取',
    defaults: {
      callMode: 'project',
      testProjectApiId: '',
      timeoutMs: 30000,
      extracts: [],
    },
  },
  assert: {
    label: 'Assert',
    color: NODE_COLORS.assert,
    icon: 'AST',
    desc: '断言校验',
    defaults: {
      rules: [{ left: 'http.body.data.code', operator: 'eq', right: '0' }],
    },
  },
  delay: {
    label: 'Delay',
    color: NODE_COLORS.delay,
    icon: 'Delay',
    desc: '等待毫秒',
    defaults: {
      ms: DELAY_DEFAULT_MS,
    },
  },
  condition: {
    label: 'Condition',
    color: NODE_COLORS.condition,
    icon: 'IF',
    desc: 'IF / ELIF / ELSE 分支',
    defaults: {
      branches: null,
    },
  },
  assign: {
    label: 'Assign',
    color: NODE_COLORS.assign,
    icon: 'ASN',
    desc: '写入流程变量 flow 或出参 out · = += -= *= /=',
    defaults: {
      assignments: [{ scope: 'flow', name: 'pollAttempt', op: 'set', value: '0' }],
    },
  },
  script: {
    label: 'Script',
    color: NODE_COLORS.script,
    icon: 'SCR',
    desc: '受限脚本 · flow 变量拼装 / JSON / 签名',
    defaults: {
      language: 'javascript',
      source: "ctx.setFlow('demo', ctx.jsonStringify({ ok: true }));",
      timeoutMs: SCRIPT_DEFAULT_TIMEOUT_MS,
    },
  },
  subflow: {
    label: 'Subflow',
    color: NODE_COLORS.subflow,
    icon: 'SUB',
    desc: '引用子流 · 登录 / OAuth 等复用块',
    defaults: {
      subflowId: '',
      inputs: [],
    },
  },
  input: {
    label: 'Input',
    color: NODE_COLORS.input,
    icon: 'IN',
    desc: '暂停等待人工输入 · 写入 flow',
    defaults: {
      prompt: '请填写后继续',
      fields: defaultInputFields(),
    },
  },
  // 插件动作：默认值依次为插件 id、贡献点 id、能力名称、能力参数
  pluginAction: {
    label: '插件步骤',
    color: NODE_COLORS.pluginAction,
    icon: 'PA',
    desc: '调用已启用插件能力 · pluginId + capability',
    defaults: {
      pluginId: '',
      contributionId: '',
      capability: '',
      config: {},
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