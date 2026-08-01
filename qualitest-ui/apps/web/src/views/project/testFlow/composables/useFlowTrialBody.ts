/**
 * 提供当前选中 Run 的最近 HTTP 响应 body，给断言/提取编辑器做路径试算。
 */
import { computed } from 'vue';

import { useRunLibraryStore } from '../stores/runLibraryStore';
import { trialBodyFromSelectedRun } from '../utils/jsonPathTrial';

export function useFlowTrialBody() {
  const runLib = useRunLibraryStore();
  /** 最近一次 HTTP 步的 response.body */
  const trialBody = computed(() => trialBodyFromSelectedRun(runLib.selectedRun));
  /** 是否已有可试算的 body */
  const hasTrialBody = computed(() => trialBody.value !== undefined && trialBody.value !== null);
  return { runLib, trialBody, hasTrialBody };
}
