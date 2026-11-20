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

/** 运行场景（场景预设）：环境、入参初值与失败策略 */
export interface GraphRunScenario {
  id: string;
  name: string;
  testProjectEnvId: string;
  /** 入参初值 KV；Run 启动时注入 in；键由入参契约声明 */
  inSeed?: Record<string, unknown>;
  remark?: string;
  onNodeFailure?: 'fail' | 'prompt';
  onSnapshotFailure?: 'abort' | 'prompt' | 'continue';
  /** 单次运行允许执行的最大节点步数；留空表示使用默认 200 */
  maxSteps?: number | null;
}

/** 流程对外暴露的 flow 返回值声明 */
export interface GraphFlowOutput {
  name: string;
  description?: string;
}

/** 入参字段：开放入站与场景 inSeed 灌值时按 name 写入运行时 in */
export interface GraphInputField {
  name: string;
  /** 画布展示名 */
  label?: string;
  /** 类型：string / number / boolean / json 等 */
  type?: string;
  /** 开放入站开跑前必须非空 */
  required?: boolean;
  /** 场景 inSeed 与请求都未给时写入 in 的缺省值 */
  defaultValue?: unknown;
  remark?: string;
}

/**
 * 入参定义（meta.inputSpec）。
 * open=true 时按完整 path（如 /v3/refund）入站：握连接跑图后把 out 写成 HTTP 回包。
 */
export interface GraphInputSpec {
  fields?: GraphInputField[];
  /** 是否开放 HTTP 入站 */
  open?: boolean;
  /** 完整相对 path，须以 / 开头（如 /v3/refund） */
  path?: string;
  holdTimeoutMs?: number | null;
  /** 开放入站绑定的运行环境 id（字符串雪花）；open=true 时必填 */
  runEnvId?: string;
  /** @deprecated 旧入参预设兜底；场景已吸收 inSeed 后不再使用 */
  defaultPresetId?: string;
}

/**
 * 旧版入参预设（meta.inputPresets）。
 * 新图以场景 inSeed 为准；加载时会迁入场景，仅兼容未重存的旧图。
 */
export interface GraphInputPreset {
  id: string;
  name: string;
  testProjectEnvId?: string;
  values?: Record<string, unknown>;
  remark?: string;
  onNodeFailure?: string;
  onSnapshotFailure?: string;
  maxSteps?: number | null;
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
  /**
   * 这条流的排版方式，随图保存，不另存拐点。
   * default 从左往右分层并折行；horizontal 横向不折行；
   * horizontal-wrap 横向折行；vertical 自上而下。
   */
  layout?: string;
  startNodeId?: string;
  activeScenarioId?: string;
  scenarios?: GraphRunScenario[];
  /** 入参字段与是否开放入站（流级契约） */
  inputSpec?: GraphInputSpec | null;
  /** @deprecated 旧入参预设列表；加载迁入场景 inSeed 后不再维护 */
  inputPresets?: GraphInputPreset[];
  /** @deprecated 旧活动预设 id */
  activeInputPresetId?: string;
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

/** 节点间有向边（持久化仅 id / source / target / label） */
export interface GraphEdge {
  id: string;
  source: string;
  target: string;
  /** 画布展示标签（可选） */
  label?: string;
}
