/**
 * 测试流 graph_json 与 vue-flow 编辑态的双向转换。
 *
 * - fromGraphJson：unwrap 外层包装 + 还原 nodes/edges/viewport/runConfig/flowOutputs；condition 出边补 sourceHandle
 * - toGraphJson：剥离 selected/dragging 等 UI 字段，写入展平 meta
 * - createEmptyGraph：新建空图时附带默认 viewport 与运行场景
 */
import type { Edge, Node } from '@vue-flow/core';
import { markRaw } from 'vue';

import { findStartNodeIds, unwrapGraphPayload } from '@/utils/flow/graphValidate';

import { applyConditionEdgeProps } from './utils/conditionUtils';
import { nextSnowflakeId } from '@/utils/flow/snowflakeId';
import type {
  GraphEdge,
  GraphFlowOutput,
  GraphJson,
  GraphMeta,
  GraphRunScenario,
  GraphScenarioConfig,
  GraphViewport,
} from '@/utils/flow/graphTypes';
import { CURRENT_GRAPH_SCHEMA_VERSION } from '@/utils/flow/graphTypes';

import type { StagingPersistFilter } from './types/aiStagingTypes';

export const DEFAULT_VIEWPORT: GraphViewport = { x: 40, y: 40, zoom: 1 };

/** 视口坐标取有限精度，避免 VueFlow 浮点误差导致脏检查误判 */
export function normalizeViewport(vp: GraphViewport): GraphViewport {
  return {
    x: Math.round(vp.x * 10) / 10,
    y: Math.round(vp.y * 10) / 10,
    zoom: Math.round(vp.zoom * 1000) / 1000,
  };
}

/** 生成含一条默认冒烟场景的配置 */
export function createDefaultRunConfig(testProjectEnvId = ''): GraphScenarioConfig {
  const scenarioId = nextSnowflakeId();
  const scenario: GraphRunScenario = {
    id: scenarioId,
    name: '默认（冒烟）',
    testProjectEnvId,
    flowSeed: {},
    remark: '',
  };
  return {
    activeScenarioId: scenarioId,
    scenarios: [scenario],
  };
}

export interface CreateEmptyGraphOptions {
  testProjectEnvId?: string;
  viewport?: GraphViewport;
}

/** 新建空画布 graph_json */
export function createEmptyGraph(options: CreateEmptyGraphOptions = {}): GraphJson {
  const runConfig = createDefaultRunConfig(options.testProjectEnvId ?? '');
  return {
    nodes: [],
    edges: [],
    meta: {
      viewport: options.viewport ?? { ...DEFAULT_VIEWPORT },
      layout: 'manual',
      schemaVersion: CURRENT_GRAPH_SCHEMA_VERSION,
      activeScenarioId: runConfig.activeScenarioId,
      scenarios: runConfig.scenarios,
      flowOutputs: [],
    },
  };
}

export interface FromGraphJsonResult {
  nodes: Node[];
  edges: Edge[];
  viewport: GraphViewport;
  runConfig: GraphScenarioConfig;
  flowOutputs: GraphFlowOutput[];
}

function normalizeScenario(scenario: GraphRunScenario): GraphRunScenario {
  return {
    ...scenario,
    id: String(scenario.id ?? ''),
    name: String(scenario.name ?? ''),
    testProjectEnvId: scenario.testProjectEnvId != null ? String(scenario.testProjectEnvId).trim() : '',
    remark: scenario.remark != null ? String(scenario.remark) : '',
    flowSeed: scenario.flowSeed && typeof scenario.flowSeed === 'object' ? scenario.flowSeed : {},
  };
}

function readScenarioConfig(meta: GraphMeta | undefined): GraphScenarioConfig {
  if (meta?.scenarios?.length) {
    const scenarios = JSON.parse(JSON.stringify(meta.scenarios)).map(normalizeScenario);
    return {
      activeScenarioId: String(meta.activeScenarioId ?? scenarios[0].id),
      scenarios,
    };
  }
  return createDefaultRunConfig();
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

/** 持久化 JSON → vue-flow 编辑态 */
export function fromGraphJson(raw: unknown): FromGraphJsonResult {
  const graph = parseGraphJson(raw);
  const meta = graph.meta;
  const viewport = meta?.viewport ?? { ...DEFAULT_VIEWPORT };
  const runConfig = readScenarioConfig(meta);
  const flowOutputs = meta?.flowOutputs?.length
    ? JSON.parse(JSON.stringify(meta.flowOutputs))
    : [];

  const nodes: Node[] = graph.nodes.map(
    (n) =>
      markRaw({
        id: n.id,
        type: n.type,
        position: { x: n.position.x, y: n.position.y },
        data: JSON.parse(JSON.stringify(n.data)),
      }) as Node,
  );

  const nodeById = new Map(graph.nodes.map((n) => [n.id, n]));
  const edges = buildVueFlowEdges(nodeById, graph.edges);

  return { nodes, edges, viewport, runConfig, flowOutputs };
}

/**
 * 将历史快照或 JSON 中的裸节点/边数据还原为 vue-flow 可渲染的编辑态。
 * 节点组件引用需 markRaw；condition 出边需补全 type 与 sourceHandle。
 */
export function rehydrateCanvasSnapshot(
  rawNodes: Array<{
    id: string;
    type: string;
    position: { x: number; y: number };
    data?: Record<string, unknown>;
  }>,
  rawEdges: Array<{
    id: string;
    source: string;
    target: string;
    label?: string;
    type?: string;
    sourceHandle?: string | null;
  }>,
): { nodes: Node[]; edges: Edge[] } {
  const nodeById = new Map(rawNodes.map((n) => [n.id, n]));

  const nodes: Node[] = rawNodes.map(
    (n) =>
      markRaw({
        id: n.id,
        type: n.type,
        position: { x: n.position.x, y: n.position.y },
        data: JSON.parse(JSON.stringify(n.data ?? {})),
      }) as Node,
  );

  const edges = buildVueFlowEdges(nodeById, rawEdges);

  return { nodes, edges };
}

type RawEdgeLike = {
  id: string;
  source: string;
  target: string;
  label?: string;
  sourceHandle?: string | null;
};

/** 裸边数据 → vue-flow Edge，condition 出边补全 type 与 sourceHandle */
function buildVueFlowEdges(
  nodeById: Map<string, { type?: string; data?: Record<string, unknown> }>,
  rawEdges: RawEdgeLike[],
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
    applyConditionEdgeProps(edge, nodeById.get(e.source), e);
    return edge;
  });
}

export interface ToGraphJsonInput {
  nodes: Node[];
  edges: Edge[];
  viewport: GraphViewport;
  runConfig: GraphScenarioConfig;
  flowOutputs?: GraphFlowOutput[];
  /** 保存时排除未 confirm 的 Staging 对象并回滚 pending update */
  stagingFilter?: StagingPersistFilter;
}

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
  const activeScenarioId =
    filter.baselineActiveScenarioId != null ? filter.baselineActiveScenarioId : runConfig.activeScenarioId;
  return {
    activeScenarioId,
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
      return {
        ...n,
        type: baseline.type ?? n.type,
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

/** vue-flow 编辑态 → 持久化 JSON（剥离 UI 字段；可选排除 Staging） */
export function toGraphJson(input: ToGraphJsonInput): GraphJson {
  const effective = applyStagingPersistFilter(input);
  const nodes = effective.nodes.map((n) => ({
    id: n.id,
    type: n.type,
    position: { x: n.position.x, y: n.position.y },
    data: JSON.parse(JSON.stringify(n.data ?? {})),
  }));

  const edges: GraphEdge[] = effective.edges.map((e) => {
    const edge: GraphEdge = { id: e.id, source: e.source, target: e.target };
    if (e.label != null && String(e.label).trim()) {
      edge.label = String(e.label).trim();
    }
    return edge;
  });

  const startIds = findStartNodeIds({ nodes, edges });
  const meta: GraphMeta = {
    schemaVersion: CURRENT_GRAPH_SCHEMA_VERSION,
    viewport: normalizeViewport(effective.viewport),
    layout: 'manual',
    activeScenarioId: effective.runConfig.activeScenarioId,
    scenarios: JSON.parse(JSON.stringify(effective.runConfig.scenarios)),
    flowOutputs: JSON.parse(JSON.stringify(effective.flowOutputs ?? [])),
  };
  if (startIds.length === 1) {
    meta.startNodeId = startIds[0];
  }

  return { nodes, edges, meta };
}
