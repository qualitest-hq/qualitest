/**
 * 测试流图持久化 JSON 的最小 TypeScript 类型。
 * 与后端 GraphJson / GraphNode / GraphEdge 字段对应。
 */

/** 画布视口 */
export interface GraphViewport {
  x: number;
  y: number;
  zoom: number;
}

/** 节点级被测数据快照范围 */
export interface SnapshotScope {
  scope: string;
  tables: string[];
}

/** 运行场景 */
export interface GraphRunScenario {
  id: string;
  name: string;
  testProjectEnvId: string;
  flowSeed: Record<string, unknown>;
  remark?: string;
  onNodeFailure?: 'fail' | 'prompt';
  onSnapshotFailure?: 'abort' | 'prompt' | 'continue';
}

/** 流程对外暴露的 flow 返回值声明 */
export interface GraphFlowOutput {
  name: string;
  description?: string;
}

/** 运行场景配置（画布 store 使用，对应 meta 展平字段） */
export interface GraphScenarioConfig {
  activeScenarioId: string;
  scenarios: GraphRunScenario[];
}

/** 当前 graph_json schema 版本；新图写入 1 */
export const CURRENT_GRAPH_SCHEMA_VERSION = 1;

/** 图元数据 */
export interface GraphMeta {
  schemaVersion?: number;
  viewport?: GraphViewport;
  layout?: 'manual';
  startNodeId?: string;
  activeScenarioId?: string;
  scenarios?: GraphRunScenario[];
  flowOutputs?: GraphFlowOutput[];
}

/** 测试流图根对象（nodes + edges + meta） */
export interface GraphJson {
  nodes: GraphNode[];
  edges: GraphEdge[];
  meta?: GraphMeta;
}

/** 图内单个节点 */
export interface GraphNode {
  id: string;
  type: string;
  position: { x: number; y: number };
  /** 按 type 存放业务配置 */
  data: Record<string, unknown>;
}

/** 节点间有向边（持久化仅允许 id / source / target / label） */
export interface GraphEdge {
  id: string;
  source: string;
  target: string;
  /** 画布展示标签（可选） */
  label?: string;
}
