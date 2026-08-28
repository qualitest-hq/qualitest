/**
 * condition 节点多 handle：行高变化后刷新 Vue Flow handleBounds，使连线跟锚点。
 * 锚点挂在分支行内（Vue Flow 默认 top:50% 相对行），不再用像素 top 偏移。
 */
import { useVueFlow } from '@vue-flow/core';
import { type ComputedRef, type Ref, nextTick, onBeforeUnmount, onMounted, watch } from 'vue';

import { FLOW_VUE_FLOW_ID } from '../constants/flowConfig';
import type { ConditionBranch } from '../utils/conditionUtils';
import { waitDoubleAnimationFrame } from '../utils/waitDoubleAnimationFrame';

export function useConditionHandleLayout(
  nodeId: string,
  branches: Ref<ConditionBranch[]> | ComputedRef<ConditionBranch[]>,
  rootEl: Ref<HTMLElement | null | undefined>,
) {
  const { updateNodeInternals } = useVueFlow(FLOW_VUE_FLOW_ID);
  let branchesObserver: ResizeObserver | null = null;

  async function scheduleRefresh() {
    await nextTick();
    await waitDoubleAnimationFrame();
    updateNodeInternals([nodeId]);
  }

  watch(branches, scheduleRefresh, { deep: true });

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
