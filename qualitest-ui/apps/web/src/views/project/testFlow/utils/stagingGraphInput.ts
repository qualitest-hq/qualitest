/**
 * 组装画布 graph_json 输入（设计请求与 confirm 共用）。
 */
import type { useFlowCanvasStore } from '../stores/flowCanvasStore';
import type { ToGraphJsonInput } from '../graphAdapter';

type FlowCanvasStore = ReturnType<typeof useFlowCanvasStore>;

export function buildFlowGraphInput(store: FlowCanvasStore): ToGraphJsonInput {
  return {
    nodes: store.nodes,
    edges: store.edges,
    viewport: store.viewport,
    runConfig: store.runConfig,
    flowOutputs: store.flowOutputs,
  };
}
