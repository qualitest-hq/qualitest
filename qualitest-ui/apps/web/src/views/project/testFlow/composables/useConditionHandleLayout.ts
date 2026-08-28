/**
 * condition 节点多 handle 布局：按分支行 DOM 实测 top，供根级 Handle 定位。
 */
import { useVueFlow } from '@vue-flow/core';
import { type ComputedRef, type Ref, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import { FLOW_VUE_FLOW_ID } from '../constants/flowConfig';
import type { ConditionBranch } from '../utils/conditionUtils';
import { isTerminalBranch } from '../utils/conditionUtils';

/** 相对节点根元素测量各非 terminal 分支行的 handle 中心 top（px） */
export function measureConditionHandleTops(
  rootEl: HTMLElement,
  branches: ConditionBranch[],
): Record<string, number> {
  const rootRect = rootEl.getBoundingClientRect();
  const tops: Record<string, number> = {};
  branches.forEach((branch) => {
    if (isTerminalBranch(branch)) return;
    const row = rootEl.querySelector(`[data-branch-row="${branch.id}"]`);
    if (!row) return;
    const rowRect = row.getBoundingClientRect();
    tops[branch.id] = rowRect.top - rootRect.top + rowRect.height / 2;
  });
  return tops;
}

export function useConditionHandleLayout(
  nodeId: string,
  branches: Ref<ConditionBranch[]> | ComputedRef<ConditionBranch[]>,
  rootEl: Ref<HTMLElement | null | undefined>,
) {
  const handleTops = ref<Record<string, number>>({});
  const { updateNodeInternals } = useVueFlow(FLOW_VUE_FLOW_ID);
  let branchesObserver: ResizeObserver | null = null;

  function measure() {
    const root = rootEl.value;
    if (!root) return;
    handleTops.value = measureConditionHandleTops(root, branches.value);
    updateNodeInternals([nodeId]);
  }

  async function scheduleMeasure() {
    await nextTick();
    measure();
  }

  watch(branches, scheduleMeasure, { deep: true });

  onMounted(() => {
    scheduleMeasure();
    const root = rootEl.value;
    const branchesEl = root?.querySelector('.cond-node__branches');
    if (branchesEl) {
      branchesObserver = new ResizeObserver(() => measure());
      branchesObserver.observe(branchesEl);
    }
  });

  onBeforeUnmount(() => {
    branchesObserver?.disconnect();
  });

  return { handleTops, remeasure: scheduleMeasure };
}
