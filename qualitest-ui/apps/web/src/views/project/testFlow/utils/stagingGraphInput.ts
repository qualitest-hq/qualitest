/**
 * 组装画布 graph_json 输入（保存、脏检查、运行、设计请求与 confirm 共用）。
 * 边取 getEffectiveEdges：含 pendingEdges，避免边尚未灌入时把边表丢掉。
 * 当前跑前选中场景入口时，先把 runConfig.scenarios 刷回该入口节点，再序列化。
 */
import { writeScenariosToScenarioEntry } from '../graphAdapter';
import type { useFlowCanvasStore } from '../stores/flowCanvasStore';
import type { ToGraphJsonInput } from '../graphAdapter';

type FlowCanvasStore = ReturnType<typeof useFlowCanvasStore>;

/** 当前选中的场景入口 id；未选中或非场景入口时为 null */
export function resolveSelectedScenarioEntryId(store: FlowCanvasStore): string | null {
  const selected = store.selectedRunEntryNodeId;
  if (!selected) return null;
  return store.nodes.some((n) => n.id === selected && n.type === 'scenario-entry')
    ? selected
    : null;
}

/**
 * 把编辑中的 runConfig.scenarios 写回当前选中的场景入口节点。
 * 切入口 / 保存前调用，避免多场景入口串写。
 */
export function flushRunConfigToSelectedScenarioEntry(store: FlowCanvasStore): boolean {
  const entryId = resolveSelectedScenarioEntryId(store);
  if (!entryId) return false;
  const wrote = writeScenariosToScenarioEntry(store.nodes, entryId, store.runConfig.scenarios);
  if (wrote) {
    // 触发 nodes 引用更新，便于脏检查与 Vue 追踪
    store.nodes = [...store.nodes];
  }
  return wrote;
}

export type BindScenarioEntryOptions = {
  /**
   * 为 true 且目标为场景入口时，同步画布选中该节点
   *（跑前面板切换场景入口用；属性面板打开时画布已选中，无需再设）。
   */
  selectOnCanvas?: boolean;
};

/**
 * 切到指定跑前入口：先刷回上一场景入口编辑，再设 selectedRunEntryNodeId；
 * 若目标为场景入口则载入其 data.scenarios。
 */
export function bindScenarioEntry(
  store: FlowCanvasStore,
  entryId: string,
  options: BindScenarioEntryOptions = {},
): void {
  if (!entryId) return;
  const prevId = store.selectedRunEntryNodeId;
  if (prevId === entryId) return;
  if (prevId) {
    flushRunConfigToSelectedScenarioEntry(store);
  }
  store.selectedRunEntryNodeId = entryId;
  const entry = store.nodes.find((n) => n.id === entryId);
  if (entry?.type === 'scenario-entry') {
    const raw = Array.isArray(entry.data?.scenarios) ? entry.data.scenarios : [];
    store.runConfig = {
      activeScenarioId: raw[0]?.id ?? '',
      scenarios: JSON.parse(JSON.stringify(raw)),
    };
    if (options.selectOnCanvas) {
      store.selected = { kind: 'node', id: entryId };
    }
  }
}

/** 由画布 store 组装序列化 graph_json 所需的输入（节点、边、视口、场景配置、排版等） */
export function buildFlowGraphInput(store: FlowCanvasStore): ToGraphJsonInput {
  flushRunConfigToSelectedScenarioEntry(store);
  const scenarioEntryNodeId = resolveSelectedScenarioEntryId(store);
  return {
    nodes: store.nodes,
    edges: store.getEffectiveEdges(),
    viewport: store.viewport,
    runConfig: store.runConfig,
    // 当前排版方式，保存、运行和脏检查都带着它，避免落盘时被写成默认排版
    layoutPreset: store.layoutPresetId,
    scenarioEntryNodeId,
  };
}
