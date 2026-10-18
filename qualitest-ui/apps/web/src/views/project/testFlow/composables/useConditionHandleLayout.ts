/**
 * condition 节点多 handle：行高或排版方向变化后刷新 Vue Flow handleBounds，使连线跟锚点。
 * 横向时锚点挂在分支行右侧；纵向时锚点挂在卡片底边并按分支均分。
 */
import { useVueFlow } from '@vue-flow/core';
import { type ComputedRef, type Ref, nextTick, onBeforeUnmount, onMounted, watch } from 'vue';

import { FLOW_VUE_FLOW_ID } from '../constants/flowConfig';
import { useFlowCanvasStore } from '../stores/flowCanvasStore';
import type { ConditionBranch } from '../utils/conditionUtils';
import { waitDoubleAnimationFrame } from '../utils/waitDoubleAnimationFrame';

export function useConditionHandleLayout(
  nodeId: string,
  branches: Ref<ConditionBranch[]> | ComputedRef<ConditionBranch[]>,
  rootEl: Ref<HTMLElement | null | undefined>,
) {
  const { updateNodeInternals } = useVueFlow(FLOW_VUE_FLOW_ID);
  const store = useFlowCanvasStore();
  let branchesObserver: ResizeObserver | null = null;

  async function scheduleRefresh() {
    await nextTick();
    await waitDoubleAnimationFrame();
    updateNodeInternals([nodeId]);
  }

  watch(branches, scheduleRefresh, { deep: true });
  watch(() => store.layoutAxis, scheduleRefresh);

  onMounted(() => {
    scheduleRefresh();
    const branchesEl = rootEl.value?.querySelector('.cond-node__branches');
    if (branchesEl) {
      branchesObserver = new ResizeObserver(() => {
        void scheduleRefresh();
      });
      branchesObserver.observe(branchesEl);
    }
  });

  onBeforeUnmount(() => {
    branchesObserver?.disconnect();
  });

  return { remeasure: scheduleRefresh };
}
