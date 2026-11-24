/** 测试流图持久化 JSON 的最小 TypeScript 类型定义。 */

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

/** 运行场景（挂在 scenario-entry.data.scenarios） */
export interface GraphRunScenario {
  id: string;
  name: string;
  /** 环境 id */
  envId?: string;
  /** 入参值树，开跑灌 ctx.in */
  inSeed?: Record<string, unknown>;
  remark?: string;
  onNodeFailure?: 'fail' | 'prompt';
  onSnapshotFailure?: 'abort' | 'prompt' | 'continue';
  /** 单次运行允许执行的最大节点步数；留空表示使用默认 200 */
  maxSteps?: number | null;
}

/**
 * 画布侧运行场景编辑态（不落盘到 meta）。
 * scenarios 保存时写回 scenario-entry.data.scenarios；activeScenarioId 仅表示当前选中项。
 */
export interface GraphScenarioConfig {
  activeScenarioId: string;
  scenarios: GraphRunScenario[];
}

/**
 * 当前 graph_json 结构版本号；新保存的图写入该值。
 * 该版本的图支持：入口节点化、返回/出参节点、多出边隐式扇出、插件动作节点。
 */
export const CURRENT_GRAPH_SCHEMA_VERSION = 2;

/**
 * 图元数据：视口、排版、schema 版本。
 * 不存放场景列表、活动场景 id、出参声明或环境 id；场景写在 scenario-entry 节点 data 内。
 * 开跑起点由拓扑入口 + 调用方 entryNodeId 决定，不在 meta 缓存。
 */
export interface GraphMeta {
  schemaVersion?: number;
  viewport?: GraphViewport;
  /**
   * 这条流的排版方式，随图保存，不另存拐点。
   * default 从左往右分层并折行；horizontal 横向不折行；
   * horizontal-wrap 横向折行；vertical 自上而下。
   */
  layout?: string;
}

/** 测试流图根对象（nodes + edges + meta） */
export interface GraphJson {
  nodes: GraphNode[];
  edges: GraphEdge[];
  meta?: GraphMeta;
}

/** 图内单个节点：壳 = id / type / name / position / data */
export interface GraphNode {
  id: string;
  type: string;
  /** 节点展示名（必填） */
  name: string;
  position: { x: number; y: number };
  /** 按 type 存放业务配置；展示名写在节点顶层 name */
  data: Record<string, unknown>;
}

/** 节点间有向边（持久化仅 id / source / target / label） */
export interface GraphEdge {
  id: string;
  source: string;
  target: string;
  /** 画布展示标签（可选） */
  label?: string;
}
