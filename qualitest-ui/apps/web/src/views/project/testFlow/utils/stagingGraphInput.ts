/**
 * 组装画布 graph_json 输入（保存、脏检查、运行、设计请求与 confirm 共用）。
 * 边取 getEffectiveEdges：含 pendingEdges，避免边尚未灌入时把边表丢掉。
 */
import type { useFlowCanvasStore } from '../stores/flowCanvasStore';
import type { ToGraphJsonInput } from '../graphAdapter';

type FlowCanvasStore = ReturnType<typeof useFlowCanvasStore>;

export function buildFlowGraphInput(store: FlowCanvasStore): ToGraphJsonInput {
  return {
    nodes: store.nodes,
    edges: store.getEffectiveEdges(),
    viewport: store.viewport,
    runConfig: store.runConfig,
    flowOutputs: store.flowOutputs,
    // 当前排版方式，保存、运行和脏检查都带着它，避免落盘时被写成默认排版
    layoutPreset: store.layoutPresetId,
  };
}
