/**
 * 批量刷新 Vue Flow 节点 handleBounds / dimensions。
 */
import { useVueFlow } from '@vue-flow/core';

import { FLOW_VUE_FLOW_ID } from '../constants/flowConfig';

export function useFlowNodeInternalsRefresh() {
  const { updateNodeInternals } = useVueFlow(FLOW_VUE_FLOW_ID);

  function refreshNodeInternals(nodeId: string) {
    updateNodeInternals([nodeId]);
  }

  function refreshAllNodeInternals(nodeIds: string[]) {
    if (nodeIds.length) updateNodeInternals(nodeIds);
  }

  return { refreshNodeInternals, refreshAllNodeInternals };
}
