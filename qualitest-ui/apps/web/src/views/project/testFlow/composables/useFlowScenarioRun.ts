/**
 * 场景运行：脏图可先保存 → 就绪检查 → POST 触发。
 * 触发回执若为 queued，则先等待排队开跑拿到 runId，再交给 followRun 跟随。
 */
import { computed } from 'vue';
import { ElMessage } from 'element-plus';

import { triggerTestFlowRun } from '@/api/project/testFlowRun';
import { validateSnapshotResetEndpointStatic } from '@/utils/flow/snapshotPreRunValidate';

import { toGraphJson } from '../graphAdapter';
import { buildFlowGraphInput } from '../utils/stagingGraphInput';
import { useFlowCanvasStore } from '../stores/flowCanvasStore';
import { useRunLibraryStore } from '../stores/runLibraryStore';
import { useRunRiskStore } from '../stores/runRiskStore';
import { collectRunBlockingErrors } from '../utils/runReadiness';
import { followRun } from './followActiveRun';
import { endRunReplay } from './usePlayback';
import { useFlowGraph } from './useFlowGraph';
import { useFlowSimulate } from './useFlowSimulate';
import { useRunConfig } from './useRunConfig';
import { resolveRunIdAfterTrigger } from './waitQueuedRun';

export function useFlowScenarioRun() {
  const store = useFlowCanvasStore();
  const runLib = useRunLibraryStore();
  const { saveFlow } = useFlowGraph();
  const { abortSimulate } = useFlowSimulate();
  const { getActiveScenario, loadProjectEnvs, envOptions } = useRunConfig();

  const isScenarioRunActive = computed(() => !!runLib.scenarioRunLive);

  /**
   * 运行当前选中场景。
   * 触发接口立刻返回 runId 后开始轮询高亮；就绪检查未通过则 toast 首条错误并中止。
   */
  async function runActiveScenario() {
    if (runLib.scenarioRunLive) return null;

    await store.ensureEdgesHydrated();
    await loadProjectEnvs();
    const scenario = getActiveScenario();
    const envId = scenario?.testProjectEnvId;
    const env = envId
      ? envOptions.value.find((e) => e.testProjectEnvId === String(envId))
      : undefined;

    if (store.dirty) {
      const saved = await saveFlow({ quiet: true, skipRunRiskRefresh: true });
      if (!saved) {
        ElMessage.warning('请先保存测试流后再运行');
        return null;
      }
    }

    // 运行前的图带上当前排版方式，避免随后保存的运行快照丢掉这项
    const graphForRun = toGraphJson(buildFlowGraphInput(store));
    const snapshotCheck = validateSnapshotResetEndpointStatic(graphForRun, env);
    if (!snapshotCheck.ok) {
      ElMessage.error(snapshotCheck.message);
      return null;
    }

    const { errors: blocking, precheckErrors } = await collectRunBlockingErrors({
      graph: graphForRun,
      testProjectId: store.testProjectId,
    });
    useRunRiskStore().setWarnings(precheckErrors);
    if (blocking.length) {
      ElMessage.error(`${blocking[0]}（详见左上角校验条）`);
      return null;
    }

    if (!store.testFlowId) {
      ElMessage.error('缺少 testFlowId');
      return null;
    }

    abortSimulate();
    endRunReplay();

    const placeholderId = `pending-${Date.now()}`;
    runLib.scenarioRunLive = { abort: false, recordId: placeholderId, phase: 'running' };

    try {
      const triggerRes = await triggerTestFlowRun({
        testFlowId: store.testFlowId,
        testProjectEnvId: scenario?.testProjectEnvId || undefined,
        runScenarioId: scenario?.id || undefined,
        triggerType: 'manual',
      });

      if (runLib.scenarioRunLive?.abort) return null;

      const { runId, outcome } = await resolveRunIdAfterTrigger(triggerRes?.data, {
        testProjectId: store.testProjectId,
        shouldAbort: () => !!runLib.scenarioRunLive?.abort,
        onQueued: (o) => ElMessage.info(o.message || '环境有任务在跑，已排队等待…'),
      });
      if (!runId) {
        ElMessage.error(outcome.message || '触发运行失败：未返回 runId');
        return null;
      }

      return await followRun(runId, { replacePending: true });
    } catch (e) {
      ElMessage.error((e as Error)?.message ?? '运行失败');
      runLib.scenarioRunLive = null;
      return null;
    }
  }

  /**
   * 中止画布侧轮询/高亮。后台图执行无法取消，仅忽略后续 UI 更新。
   */
  function abortScenarioRun() {
    if (runLib.scenarioRunLive) runLib.scenarioRunLive.abort = true;
  }

  return {
    runActiveScenario,
    abortScenarioRun,
    isScenarioRunActive,
  };
}
