/**
 * 测试流节点 type 常量与展示标签。
 * 持久化 JSON 中 type 为字符串；校验层用 isKnownNodeType 判断是否合法。
 */

/** 画布固定节点 type：执行类、入口类、返回类、插件动作 */
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
  'pluginAction',
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
  pluginAction: '插件步骤',
};

/** 平台内置入口 type：场景入口、HTTP 入口、子流入口（不含协议入口） */
export const PLATFORM_ENTRY_TYPES = [
  'scenario-entry',
  'http-entry',
  'subflow-entry',
] as const;

/** 平台内置入口 type 的联合类型 */
export type PlatformEntryType = (typeof PLATFORM_ENTRY_TYPES)[number];

/** 协议入口的 type 命名规则：小写字母开头的「{协议名}-entry」，且不属于平台内置入口 */
const PROTOCOL_ENTRY_RE = /^[a-z][a-z0-9]*-entry$/;

/** 是否为平台内置入口 type */
export function isPlatformEntryType(type: string | undefined | null): type is PlatformEntryType {
  return type != null && (PLATFORM_ENTRY_TYPES as readonly string[]).includes(type);
}

/** 是否为协议入口 type：符合「{协议名}-entry」命名且不是平台内置入口 */
export function isProtocolEntryType(type: string | undefined | null): boolean {
  if (!type || isPlatformEntryType(type)) return false;
  return PROTOCOL_ENTRY_RE.test(type);
}

/** 是否为任一入口 type（平台内置入口或协议入口） */
export function isAnyEntryType(type: string | undefined | null): boolean {
  return isPlatformEntryType(type) || isProtocolEntryType(type);
}

/** 是否为已知 type：固定节点类型，或符合命名规则的协议入口 */
export function isKnownNodeType(type: string | undefined | null): boolean {
  if (type == null) return false;
  if ((KNOWN_NODE_TYPES as readonly string[]).includes(type)) return true;
  return isProtocolEntryType(type);
}

/** 取展示标签；协议入口显示为「{协议名} 入口」；其他未知 type 原样返回 */
export function nodeTypeLabel(type: string | undefined | null): string {
  if (type != null && type in NODE_TYPE_LABELS) {
    return NODE_TYPE_LABELS[type as KnownNodeType];
  }
  if (isProtocolEntryType(type)) {
    return `${type!.slice(0, -'-entry'.length)} 入口`;
  }
  return type ?? '';
}