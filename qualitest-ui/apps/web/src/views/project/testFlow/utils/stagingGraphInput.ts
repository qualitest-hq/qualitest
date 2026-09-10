/**
 * 组装画布 graph_json 输入（设计请求与 confirm 共用）。
 * 边取 getEffectiveEdges：含 pendingEdges，避免确认窗口 edges 为空时把完整边表丢掉。
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
  };
}
