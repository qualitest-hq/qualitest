/**
 * 测试流节点 type 常量与展示标签。
 * 持久化 JSON 中 type 为字符串；校验层用 isKnownNodeType 判断是否合法。
 */

/** 画布可执行 / 入口 / 返回节点 type */
export const KNOWN_NODE_TYPES = [
  'http',
  'assert',
  'delay',
  'condition',
  'assign',
  'script',
  'subflow',
  'input',
  'scenario-entry',
  'http-entry',
  'subflow-entry',
  'http-return',
  'subflow-return',
] as const;

export type KnownNodeType = (typeof KNOWN_NODE_TYPES)[number];

/** 各 type 的画布展示标签 */
export const NODE_TYPE_LABELS: Record<KnownNodeType, string> = {
  http: 'HTTP',
  assert: 'Assert',
  delay: 'Delay',
  condition: 'Condition',
  assign: 'Assign',
  script: 'Script',
  subflow: 'Subflow',
  input: 'Input',
  'scenario-entry': '场景入口',
  'http-entry': 'HTTP 入口',
  'subflow-entry': '子流入口',
  'http-return': 'HTTP 返回',
  'subflow-return': '子流返回',
};

/** 是否为已知 type */
export function isKnownNodeType(type: string | undefined | null): type is KnownNodeType {
  return type != null && (KNOWN_NODE_TYPES as readonly string[]).includes(type);
}

/** 取展示标签；未知 type 原样返回 */
export function nodeTypeLabel(type: string | undefined | null): string {
  if (type != null && type in NODE_TYPE_LABELS) {
    return NODE_TYPE_LABELS[type as KnownNodeType];
  }
  return type ?? '';
}