/**
 * 属性面板试算用响应 body。
 *
 * 优先：当前选中 Run 里最近一次 HTTP 响应 body（真实跑过的数据）。
 * 否则：按当前节点解析 testProjectApiId，经 resolveCanvasApiDetail 取 responseConfig 首个 example
 *（模板画布只读 catalog，不打项目 Long 详情）。
 * 返回 trialBody（实际用于试算）、trialSource（run | api-example）、hasTrialBody。
 */
import { computed, ref, toValue, watch, type MaybeRefOrGetter } from 'vue';

import { useFlowCanvasStore } from '../stores/flowCanvasStore';
import { useRunLibraryStore } from '../stores/runLibraryStore';
import {
  extractResponseExample,
  resolveTrialApiId,
  trialBodyFromSelectedRun,
  type TrialBodySource,
} from '../utils/jsonPathTrial';
import { resolveCanvasApiDetail } from '../utils/resolveCanvasApiDetail';

/** 属性面板当前节点（用于回溯上游 HTTP / 取自身 apiId） */
type TrialNode = {
  id?: string;
  type?: string;
  data?: Record<string, unknown>;
} | null;

/** 按 testProjectApiId 缓存接口响应示例，避免切换节点重复请求 */
const apiExampleCache = new Map<string, unknown>();

/**
 * @param opts.node 当前属性面板节点；不传则只使用 Run body，不拉接口示例
 */
export function useFlowTrialBody(opts?: { node?: MaybeRefOrGetter<TrialNode> }) {
  const store = useFlowCanvasStore();
  const runLib = useRunLibraryStore();
  /** 接口示例 body；无 example 或请求失败时为 undefined */
  const apiExampleBody = ref<unknown>(undefined);

  const runBody = computed(() => trialBodyFromSelectedRun(runLib.selectedRun));

  /** 当前应拉取示例的接口 id（HTTP 自身，或 assert/condition 上游） */
  const targetApiId = computed(() => {
    const node = opts?.node != null ? toValue(opts.node) : null;
    if (!node?.id) return '';
    return resolveTrialApiId(node, store.nodes, store.edges);
  });

  // apiId 变化时拉详情；命中缓存则直接用；异步返回时校验仍是当前 apiId，避免错位
  watch(
    targetApiId,
    async (apiId) => {
      apiExampleBody.value = undefined;
      if (!apiId) return;
      if (apiExampleCache.has(apiId)) {
        apiExampleBody.value = apiExampleCache.get(apiId);
        return;
      }
      const detail = await resolveCanvasApiDetail(apiId);
      const example = extractResponseExample(detail?.responseConfig);
      apiExampleCache.set(apiId, example);
      if (targetApiId.value === apiId) {
        apiExampleBody.value = example;
      }
    },
    { immediate: true },
  );

  /** 最终试算 body：有 Run 用 Run，否则用接口示例 */
  const trialBody = computed(() => {
    if (runBody.value !== undefined && runBody.value !== null) return runBody.value;
    return apiExampleBody.value;
  });

  /** 标明数据来源，供面板提示「Run」或「接口示例」 */
  const trialSource = computed((): TrialBodySource | null => {
    if (runBody.value !== undefined && runBody.value !== null) return 'run';
    if (apiExampleBody.value !== undefined && apiExampleBody.value !== null) return 'api-example';
    return null;
  });

  const hasTrialBody = computed(() => trialBody.value !== undefined && trialBody.value !== null);

  return { runLib, trialBody, trialSource, hasTrialBody };
}
