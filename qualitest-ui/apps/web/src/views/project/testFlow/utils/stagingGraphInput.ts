/**
 * 组装画布 graph_json 输入（保存、脏检查、运行、设计请求与 confirm 共用）。
 * 边取 getEffectiveEdges：含 pendingEdges，避免边尚未灌入时把边表丢掉。
 */
import type { useFlowCanvasStore } from '../stores/flowCanvasStore';
import type { ToGraphJsonInput } from '../graphAdapter';

type FlowCanvasStore = ReturnType<typeof useFlowCanvasStore>;

/** 由画布 store 组装序列化 graph_json 所需的输入（节点、边、视口、场景配置、排版等） */
export function buildFlowGraphInput(store: FlowCanvasStore): ToGraphJsonInput {
  // 仅当跑前选中的入口是场景入口时，才把场景列表写回它；否则交给序列化逻辑回退到第一个场景入口
  const selected = store.selectedRunEntryNodeId;
  const scenarioEntryNodeId =
    selected && store.nodes.some((n) => n.id === selected && n.type === 'scenario-entry')
      ? selected
      : null;
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
