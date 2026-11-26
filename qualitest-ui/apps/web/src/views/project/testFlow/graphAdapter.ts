/**
 * 测试流 graph_json 与 vue-flow 编辑态的双向转换。
 *
 * - fromGraphJson：解开外层包装，还原 nodes/edges/viewport；从场景入口 data.scenarios 灌入 runConfig，
 *   并带回该入口 id（scenarioEntryNodeId）供跑前入口选中；condition 出边补 sourceHandle；打开画布不重排坐标
 * - toGraphJson：剥离界面字段，把 runConfig.scenarios（envId / inSeed）写回指定的场景入口；
 *   未指定入口时不改写任一入口的 scenarios；排版写入 meta.layout；meta 不写场景列表
 * - createEmptyGraph：新建完全空白图（无入口节点），仅默认视口
 */
import type { Edge, Node } from '@vue-flow/core';
import { markRaw } from 'vue';

import { unwrapGraphPayload } from '@/utils/flow/graphValidate';

import { applyConditionEdgeProps } from './utils/conditionUtils';
import { nextSnowflakeId } from '@/utils/flow/snowflakeId';
import type {
  GraphEdge,
  GraphJson,
  GraphMeta,
  GraphRunScenario,
  GraphScenarioConfig,
  GraphViewport,
} from '@/utils/flow/graphTypes';
import { CURRENT_GRAPH_SCHEMA_VERSION } from '@/utils/flow/graphTypes';

import type { StagingPersistFilter } from './types/aiStagingTypes';
import { normalizeLayoutPresetId, type FlowLayoutPresetId } from './utils/flowLayoutPreset';

export const DEFAULT_VIEWPORT: GraphViewport = { x: 40, y: 40, zoom: 1 };

/** 子流节点落盘允许的 data 键（与后端 FlowDesignNodeDataKeys.SUBFLOW 对齐） */
const SUBFLOW_PERSIST_DATA_KEYS = new Set([
  'subflowId',
  'subflowName',
  'inputs',
  'alwaysRun',
  'templateId',
]);

/** 只保留子流落盘白名单键，丢弃其余未知键 */
function pickSubflowPersistData(data: Record<string, unknown>): Record<string, unknown> {
  const next: Record<string, unknown> = {};
  for (const key of Object.keys(data)) {
    if (SUBFLOW_PERSIST_DATA_KEYS.has(key)) {
      next[key] = data[key];
    }
  }
  return next;
}

/** 视口坐标取有限精度，避免 VueFlow 浮点误差导致脏检查误判 */
export function normalizeViewport(vp: GraphViewport): GraphViewport {
  return {
    x: Math.round(vp.x * 10) / 10,
    y: Math.round(vp.y * 10) / 10,
    zoom: Math.round(vp.zoom * 1000) / 1000,
  };
}

/** 解析场景环境 id（正名 envId；读旧图时兼容 testProjectEnvId） */
export function resolveScenarioEnvId(sc: GraphRunScenario | undefined | null): string {
  if (!sc) return '';
  if (sc.envId != null && String(sc.envId).trim()) return String(sc.envId).trim();
  const legacy = (sc as Record<string, unknown>).testProjectEnvId;
  return legacy != null ? String(legacy).trim() : '';
}

/** 解析入参值树（正名 inSeed；读旧图时兼容 flowSeed） */
export function resolveScenarioInSeed(sc: GraphRunScenario | undefined | null): Record<string, unknown> {
  if (!sc) return {};
  if (sc.inSeed && typeof sc.inSeed === 'object') return { ...sc.inSeed };
  const legacy = (sc as Record<string, unknown>).flowSeed;
  if (legacy && typeof legacy === 'object' && !Array.isArray(legacy)) {
    return { ...(legacy as Record<string, unknown>) };
  }
  return {};
}

/** 生成含一条默认冒烟场景的配置 */
export function createDefaultRunConfig(testProjectEnvId = ''): GraphScenarioConfig {
  const scenarioId = nextSnowflakeId();
  const scenario: GraphRunScenario = {
    id: scenarioId,
    name: '默认（冒烟）',
    envId: testProjectEnvId,
    inSeed: {},
    remark: '',
  };
  return {
    activeScenarioId: scenarioId,
    scenarios: [scenario],
  };
}

/** 新建空画布的可选参数 */
export interface CreateEmptyGraphOptions {
  /** 初始视口；缺省使用默认视口 */
  viewport?: GraphViewport;
}

/** 新建空画布：无入口、无场景；用户自行拖入口。meta 仅视口/排版/schemaVersion */
export function createEmptyGraph(options: CreateEmptyGraphOptions = {}): GraphJson {
  return {
    nodes: [],
    edges: [],
    meta: {
      viewport: options.viewport ?? { ...DEFAULT_VIEWPORT },
      layout: 'default',
      schemaVersion: CURRENT_GRAPH_SCHEMA_VERSION,
    },
  };
}

/** 图 JSON 转画布编辑态的结果 */
export interface FromGraphJsonResult {
  nodes: Node[];
  edges: Edge[];
  viewport: GraphViewport;
  /** 打开时灌入的场景运行配置 */
  runConfig: GraphScenarioConfig;
  /**
   * 上述 runConfig 所来自的场景入口节点 id。
   * 打开画布时用来同步跑前选中入口，避免多场景入口时场景列表与选中入口错位；无场景入口时为 null。
   */
  scenarioEntryNodeId: string | null;
  /** 这条流记住的排版方式；打开画布只恢复，不自动重排节点 */
  layoutPreset: FlowLayoutPresetId;
}

/** 规整单条场景：补齐 id/name，统一 envId 与 inSeed；不保留旧键 */
function normalizeScenario(scenario: GraphRunScenario): GraphRunScenario {
  const envId = resolveScenarioEnvId(scenario);
  const inSeed = resolveScenarioInSeed(scenario);
  return {
    id: String(scenario.id ?? ''),
    name: String(scenario.name ?? ''),
    envId,
    inSeed,
    remark: scenario.remark != null ? String(scenario.remark) : '',
  };
}

/**
 * 从场景入口节点读出场景列表。
 * 恰好一个 scenario-entry 时用它（空列表也记住入口 id）；多个则返回 null，由 UI 显式选入口后再灌。
 */
function scenariosFromEntryNodes(
  nodes: GraphJson['nodes'] | undefined,
): { entryId: string; scenarios: GraphRunScenario[] } | null {
  if (!nodes?.length) return null;
  const entries: Array<{ entryId: string; scenarios: GraphRunScenario[] }> = [];
  for (const n of nodes) {
    if (n?.type !== 'scenario-entry') continue;
    const entryId = String(n.id ?? '').trim();
    if (!entryId) continue;
    const raw = n.data?.scenarios;
    const scenarios = Array.isArray(raw) && raw.length
      ? raw.map((s) => normalizeScenario(s as GraphRunScenario))
      : [];
    entries.push({ entryId, scenarios });
  }
  if (entries.length !== 1) return null;
  return entries[0];
}

/**
 * 打开画布时组装运行配置，并标出场景列表来自哪个入口节点。
 * 唯一入口：有场景则灌入并默认激活第一条；入口上场景为空则保持空列表（不静默塞默认冒烟）。
 * 多入口：不静默灌，等用户选入口。
 */
function readScenarioConfig(nodes?: GraphJson['nodes']): {
  runConfig: GraphScenarioConfig;
  scenarioEntryNodeId: string | null;
} {
  const fromEntry = scenariosFromEntryNodes(nodes);
  if (!fromEntry) {
    return { scenarioEntryNodeId: null, runConfig: createDefaultRunConfig() };
  }
  if (!fromEntry.scenarios.length) {
    return {
      scenarioEntryNodeId: fromEntry.entryId,
      runConfig: { activeScenarioId: '', scenarios: [] },
    };
  }
  return {
    scenarioEntryNodeId: fromEntry.entryId,
    runConfig: {
      activeScenarioId: fromEntry.scenarios[0].id,
      scenarios: fromEntry.scenarios,
    },
  };
}

/** 从 API/导入的原始对象解析为 GraphJson（unwrap 外层 graphJson/graph_json 包装） */
export function parseGraphJson(raw: unknown): GraphJson {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    throw new Error('根对象必须是 JSON 对象');
  }
  const graph = unwrapGraphPayload(raw as Record<string, unknown>);
  return {
    nodes: JSON.parse(JSON.stringify(graph.nodes ?? [])),
    edges: JSON.parse(JSON.stringify(graph.edges ?? [])),
    meta: graph.meta != null ? JSON.parse(JSON.stringify(graph.meta)) : undefined,
  };
}

/** 持久化 JSON 转成画布编辑态。排版方式只读回来，不重算节点坐标 */
export function fromGraphJson(raw: unknown): FromGraphJsonResult {
  const graph = parseGraphJson(raw);
  const meta = graph.meta;
  const viewport = meta?.viewport ?? { ...DEFAULT_VIEWPORT };
  const { runConfig, scenarioEntryNodeId } = readScenarioConfig(graph.nodes);

  const nodes = buildVueFlowNodes(graph.nodes);

  const nodeById = new Map(graph.nodes.map((n) => [n.id, n]));
  const edges = buildVueFlowEdges(nodeById, graph.edges);

  return {
    nodes,
    edges,
    viewport,
    runConfig,
    scenarioEntryNodeId,
    layoutPreset: normalizeLayoutPresetId(meta?.layout),
  };
}

type RawNodeLike = {
  id: string;
  type: string;
  name?: string;
  position: { x: number; y: number };
  data?: Record<string, unknown>;
};

/** 裸节点数据 → vue-flow Node（markRaw 避免响应式包装组件引用） */
function buildVueFlowNodes(rawNodes: RawNodeLike[]): Node[] {
  return rawNodes.map((n) => {
    const data = JSON.parse(JSON.stringify(n.data ?? {})) as Record<string, unknown>;
    return markRaw({
      id: n.id,
      type: n.type,
      name: String(n.name ?? '').trim(),
      position: { x: n.position.x, y: n.position.y },
      data,
    }) as Node;
  });
}

/**
 * 将历史快照或 JSON 中的裸节点/边数据还原为 vue-flow 可渲染的编辑态。
 * condition 出边需补全 type 与 sourceHandle。
 */
export function rehydrateCanvasSnapshot(
  rawNodes: RawNodeLike[],
  rawEdges: Array<{
    id: string;
    source: string;
    target: string;
    label?: string;
    sourceHandle?: string | null;
  }>,
): { nodes: Node[]; edges: Edge[] } {
  const nodeById = new Map(rawNodes.map((n) => [n.id, n]));
  const nodes = buildVueFlowNodes(rawNodes);

  const edges = buildVueFlowEdges(nodeById, rawEdges, { allowPersistedSourceHandle: true });

  return { nodes, edges };
}

type RawEdgeLike = {
  id: string;
  source: string;
  target: string;
  label?: string;
  sourceHandle?: string | null;
};

/** 裸边数据 → vue-flow Edge，condition 出边补全 type 与 sourceHandle（运行时反推或撤销快照） */
function buildVueFlowEdges(
  nodeById: Map<string, { type?: string; data?: Record<string, unknown> }>,
  rawEdges: RawEdgeLike[],
  options?: { allowPersistedSourceHandle?: boolean },
): Edge[] {
  return rawEdges.map((e) => {
    const edge: Edge = {
      id: e.id,
      source: e.source,
      target: e.target,
    };
    if (e.label != null && String(e.label).trim()) {
      edge.label = String(e.label).trim();
    }
    const rawForProps = options?.allowPersistedSourceHandle
      ? e
      : { target: e.target, label: e.label };
    applyConditionEdgeProps(edge, nodeById.get(e.source), rawForProps);
    return edge;
  });
}

export interface ToGraphJsonInput {
  nodes: Node[];
  edges: Edge[];
  viewport: GraphViewport;
  runConfig: GraphScenarioConfig;
  /** 当前排版方式；缺省写成 default */
  layoutPreset?: string | null;
  /** 保存时排除未 confirm 的 Staging 对象并回滚 pending update */
  stagingFilter?: StagingPersistFilter;
  /** 场景列表写回到哪个场景入口节点；为空或找不到时不改写任何入口的 scenarios */
  scenarioEntryNodeId?: string | null;
}

/**
 * 把场景列表写成落盘形态（envId / inSeed 等），供写回入口节点。
 */
export function scenariosToPersistPayload(scenarios: GraphRunScenario[]): GraphRunScenario[] {
  return JSON.parse(JSON.stringify(scenarios ?? [])).map((s: GraphRunScenario) => {
    const envId = resolveScenarioEnvId(s);
    const inSeed = resolveScenarioInSeed(s);
    return {
      id: s.id,
      name: s.name,
      envId,
      inSeed,
      remark: s.remark ?? '',
    };
  });
}

/**
 * 将场景列表写回指定 scenario-entry 的 data.scenarios。
 * 入口不存在或不是场景入口时不改 nodes，返回 false。
 */
export function writeScenariosToScenarioEntry(
  nodes: Array<{ id: string; type?: string; data?: Record<string, unknown> | null }>,
  entryNodeId: string | null | undefined,
  scenarios: GraphRunScenario[],
): boolean {
  const preferredEntryId = entryNodeId != null ? String(entryNodeId).trim() : '';
  if (!preferredEntryId) return false;
  const entryIdx = nodes.findIndex(
    (n) => n.type === 'scenario-entry' && n.id === preferredEntryId,
  );
  if (entryIdx < 0) return false;
  const entry = nodes[entryIdx];
  const data = { ...(entry.data ?? {}) };
  data.scenarios = scenariosToPersistPayload(scenarios);
  nodes[entryIdx] = { ...entry, data };
  return true;
}

/**
 * Staging 落盘过滤场景：去掉未确认新增，并把未确认修改回滚到基线快照。
 */
function applyScenarioStagingFilter(
  runConfig: GraphScenarioConfig,
  filter: NonNullable<StagingPersistFilter['scenarioFilter']>,
): GraphScenarioConfig {
  let scenarios = runConfig.scenarios.filter((s) => !filter.excludeScenarioIds.has(s.id));
  scenarios = scenarios.map((scenario) => {
    const baseline = filter.scenarioBaselines.get(scenario.id);
    if (!baseline) return scenario;
    return JSON.parse(JSON.stringify({ ...scenario, ...baseline })) as GraphRunScenario;
  });
  return {
    activeScenarioId: runConfig.activeScenarioId,
    scenarios,
  };
}

/** 按 Staging 过滤规则处理 nodes/edges/runConfig，供 toGraphJson 与单测使用 */
export function applyStagingPersistFilter(input: ToGraphJsonInput): ToGraphJsonInput {
  const filter = input.stagingFilter;
  if (!filter) return input;

  let nodes = input.nodes;
  let edges = input.edges;
  let runConfig = input.runConfig;

  if (filter.excludeNodeIds.size) {
    nodes = nodes.filter((n) => !filter.excludeNodeIds.has(n.id));
  }
  if (filter.excludeEdgeIds.size) {
    edges = edges.filter((e) => !filter.excludeEdgeIds.has(e.id));
  }

  if (filter.nodeBaselines.size) {
    nodes = nodes.map((n) => {
      const baseline = filter.nodeBaselines.get(n.id);
      if (!baseline) return n;
      const baselineName = (baseline as { name?: string }).name;
      const canvasName = (n as Node & { name?: string }).name;
      return {
        ...n,
        type: baseline.type ?? n.type,
        name: baselineName != null ? String(baselineName) : canvasName,
        position: baseline.position ? { ...baseline.position } : n.position,
        data: JSON.parse(JSON.stringify(baseline.data ?? n.data ?? {})),
      };
    });
  }

  if (filter.edgeBaselines.size) {
    edges = edges.map((e) => {
      const baseline = filter.edgeBaselines.get(e.id);
      if (!baseline) return e;
      const next = { ...e };
      if (baseline.source != null) next.source = baseline.source;
      if (baseline.target != null) next.target = baseline.target;
      if (baseline.label !== undefined) {
        if (baseline.label) next.label = baseline.label;
        else delete next.label;
      }
      return next;
    });
  }

  if (filter.scenarioFilter) {
    runConfig = applyScenarioStagingFilter(runConfig, filter.scenarioFilter);
  }

  return { ...input, nodes, edges, runConfig };
}

/**
 * 画布编辑态转成可落盘的图。
 * 节点展示名写在顶层 name；runConfig.scenarios 写回 scenario-entry.data；
 * meta 只含 schemaVersion / viewport / layout。
 */
export function toGraphJson(input: ToGraphJsonInput): GraphJson {
  const effective = applyStagingPersistFilter(input);
  const nodes = effective.nodes.map((n) => {
    let data = JSON.parse(JSON.stringify(n.data ?? {})) as Record<string, unknown>;
    if (n.type === 'subflow') {
      data = pickSubflowPersistData(data);
    }
    const shellName = (n as Node & { name?: string }).name;
    return {
      id: n.id,
      type: n.type,
      name: String(shellName ?? '').trim(),
      position: { x: n.position.x, y: n.position.y },
      data,
    };
  });

  const edges: GraphEdge[] = effective.edges.map((e) => {
    const edge: GraphEdge = { id: e.id, source: e.source, target: e.target };
    if (e.label != null && String(e.label).trim()) {
      edge.label = String(e.label).trim();
    }
    return edge;
  });

  // 只写回明确指定的场景入口；未指定时保留各入口既有 data.scenarios，避免串写
  writeScenariosToScenarioEntry(
    nodes,
    effective.scenarioEntryNodeId,
    effective.runConfig.scenarios,
  );

  const meta: GraphMeta = {
    schemaVersion: CURRENT_GRAPH_SCHEMA_VERSION,
    viewport: normalizeViewport(effective.viewport),
    layout: normalizeLayoutPresetId(effective.layoutPreset),
  };

  return { nodes, edges, meta };
}
