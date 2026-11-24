/**
 * 测流入口说明相关的纯数据工具：从图 JSON 抽出入口节点，并生成入参形状、场景列表的只读摘要文案。
 * 不负责界面渲染；列表抽屉与画布侧只读区调用本模块拿数据。
 */
import { unwrapGraphPayload } from '@/utils/flow/graphValidate';
import { isAnyEntryType } from '@/utils/flow/nodeTypes';

/** 入口节点在说明面用的精简结构 */
export type EntryDocNode = {
  /** 节点 id */
  id: string;
  /** 节点 type，如 scenario-entry / http-entry / subflow-entry */
  type: string;
  /** 展示名；空则回退为 id */
  name: string;
  /** 入口说明原文（Markdown 字符串，可为空） */
  doc: string;
  /** 该入口 data 业务配置的浅拷贝 */
  data: Record<string, unknown>;
};

/** 入参形状摘要的分区列表（body / query / headers） */
export type InputShapeSummarySections = Array<{
  key: 'body' | 'query' | 'headers';
  title: string;
  items: string[];
}>;

/** 图节点松散形态，仅读取抽入口所需字段 */
type GraphNodeLike = {
  id?: unknown;
  type?: unknown;
  name?: unknown;
  data?: Record<string, unknown> | null;
};

/**
 * 从图 JSON（字符串或对象）抽出全部入口节点，顺序与画布 nodes 数组一致。
 * 非入口节点跳过；缺 id 的节点跳过。
 */
export function extractEntryNodesFromGraph(graphJson: unknown): EntryDocNode[] {
  const graph = parseGraphObject(graphJson);
  if (!graph) return [];
  const nodes = Array.isArray(graph.nodes) ? graph.nodes : [];
  const out: EntryDocNode[] = [];
  for (const raw of nodes) {
    if (!raw || typeof raw !== 'object') continue;
    const n = raw as GraphNodeLike;
    const type = String(n.type ?? '');
    if (!isAnyEntryType(type)) continue;
    const id = String(n.id ?? '').trim();
    if (!id) continue;
    const data = n.data && typeof n.data === 'object' && !Array.isArray(n.data) ? n.data : {};
    const name = String(n.name ?? '').trim() || id;
    const doc = typeof data.doc === 'string' ? data.doc : '';
    out.push({ id, type, name, doc, data: { ...data } });
  }
  return out;
}

/**
 * 把入参形状转成带中文标题的分区，方便只读列表直接 v-for 渲染。
 */
export function inputShapeSummarySections(inputShape: unknown): InputShapeSummarySections {
  const shape = summarizeInputShape(inputShape);
  return [
    { key: 'body', title: 'Body 字段', items: shape.body },
    { key: 'query', title: 'Query', items: shape.query },
    { key: 'headers', title: 'Headers', items: shape.headers },
  ];
}

/**
 * 将入参形状对象压成三组短文案数组。
 * 每项形如「字段名 · 类型 · 必填」；非法或空形状返回空数组。
 */
export function summarizeInputShape(inputShape: unknown): {
  body: string[];
  query: string[];
  headers: string[];
} {
  const empty = { body: [] as string[], query: [] as string[], headers: [] as string[] };
  if (!inputShape || typeof inputShape !== 'object' || Array.isArray(inputShape)) {
    return empty;
  }
  const shape = inputShape as Record<string, unknown>;
  return {
    body: summarizeFieldList(shape.body),
    query: summarizeFieldList(shape.query),
    headers: summarizeFieldList(shape.headers),
  };
}

/**
 * 将场景条目压成只读摘要：场景名、绑定环境 id、入参值树的键名列表。
 * 不输出 inSeed 的具体取值，避免在说明面泄露测值明文。
 */
export function summarizeScenarios(scenarios: unknown): Array<{
  name: string;
  envId: string;
  inSeedKeys: string[];
}> {
  if (!Array.isArray(scenarios)) return [];
  return scenarios.map((raw, i) => {
    const row = raw && typeof raw === 'object' && !Array.isArray(raw) ? (raw as Record<string, unknown>) : {};
    const name = String(row.name ?? '').trim() || `场景 ${i + 1}`;
    const envId = String(row.envId ?? '').trim();
    const inSeed =
      row.inSeed && typeof row.inSeed === 'object' && !Array.isArray(row.inSeed)
        ? (row.inSeed as Record<string, unknown>)
        : {};
    return { name, envId, inSeedKeys: Object.keys(inSeed) };
  });
}

/** 把字段声明数组压成展示用短句列表 */
function summarizeFieldList(list: unknown): string[] {
  if (!Array.isArray(list)) return [];
  return list.map((item) => {
    if (!item || typeof item !== 'object') return '?';
    const row = item as Record<string, unknown>;
    const name = String(row.name ?? '').trim() || '?';
    const required = row.required === true;
    const type = String(row.type ?? '').trim();
    const bits = [name];
    if (type) bits.push(type);
    if (required) bits.push('必填');
    return bits.join(' · ');
  });
}

/**
 * 把多种图 JSON 入参形态归一成带 nodes 的对象。
 * 支持：纯图对象；外层包一层 graphJson / graph_json；graphJson 本身是 JSON 字符串。
 * 解析失败返回 null。
 */
function parseGraphObject(graphJson: unknown): { nodes?: unknown[] } | null {
  if (graphJson == null) return null;
  if (typeof graphJson === 'string') {
    const text = graphJson.trim();
    if (!text) return null;
    try {
      return parseGraphObject(JSON.parse(text));
    } catch {
      return null;
    }
  }
  if (typeof graphJson !== 'object' || Array.isArray(graphJson)) return null;
  const obj = graphJson as Record<string, unknown>;
  // 外层字段仍是字符串时继续往里解
  if (typeof obj.graphJson === 'string' || typeof obj.graph_json === 'string') {
    return parseGraphObject(obj.graphJson ?? obj.graph_json);
  }
  const unwrapped = unwrapGraphPayload(obj);
  if (unwrapped !== obj) {
    return parseGraphObject(unwrapped);
  }
  return obj as { nodes?: unknown[] };
}
