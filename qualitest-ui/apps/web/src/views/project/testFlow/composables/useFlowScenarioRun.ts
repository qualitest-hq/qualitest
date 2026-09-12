/**
 * 场景运行：触发正式 Run，拉取详情后按步骤动画高亮并写入运行库。
 *
 * 流程：脏图可先带错保存 → 运行就绪检查（结构/断言路径/鉴权必填）→
 * 通过后 POST 触发 → 拉详情 → 逐步高亮。
 */
import { computed } from 'vue';
import { ElMessage } from 'element-plus';

import { triggerTestFlowRun } from '@/api/project/testFlowRun';
import { validateSnapshotResetEndpointStatic } from '@/utils/flow/snapshotPreRunValidate';

import { SCENARIO_RUN_STEP_MS } from '../constants/flowConfig';
import { toGraphJson } from '../graphAdapter';
import { useFlowCanvasStore } from '../stores/flowCanvasStore';
import type { RunRecord } from '../stores/runLibraryStore';
import { useRunLibraryStore } from '../stores/runLibraryStore';
import { useRunRiskStore } from '../stores/runRiskStore';
import { abortableSleep } from '../utils/abortableSleep';
import { collectRunBlockingErrors } from '../utils/runReadiness';
import { endRunReplay } from './usePlayback';
import { useFlowGraph } from './useFlowGraph';
import { useFlowSimulate } from './useFlowSimulate';
import { useRunConfig } from './useRunConfig';

/**
 * 按 Run 步骤时间线高亮画布节点：已访问节点写入 runVisitedNodeIds，当前步写入 runHighlightNodeId。
 */
export function highlightRunStep(
  store: ReturnType<typeof useFlowCanvasStore>,
  record: RunRecord,
  stepIndex: number,
) {
  store.clearRunHighlight();
  for (let i = 0; i <= stepIndex; i++) {
    const s = record.steps[i];
    if (!s?.nodeId) continue;
    store.runVisitedNodeIds[s.nodeId] = s.status === 'failed' ? 'failed' : 'passed';
  }
  const step = record.steps[stepIndex];
  if (step?.nodeId) store.runHighlightNodeId = step.nodeId;
}

/**
 * 正式 Run 完成后按 SCENARIO_RUN_STEP_MS 逐步高亮，同步 Inspector 步骤索引。
 * 用户按 Esc 或点停止时通过 scenarioRunLive.abort 中断。
 */
async function animateRunSteps(detail: RunRecord) {
  const store = useFlowCanvasStore();
  const runLib = useRunLibraryStore();
  const steps = detail.steps;
  if (!steps.length) return;

  const recordId = detail.id;
  const shouldAbort = () => !!runLib.scenarioRunLive?.abort;

  runLib.scenarioRunLive = {
    abort: false,
    recordId,
    phase: 'animating',
    stepIndex: 0,
    stepTotal: steps.length,
  };
  runLib.inspectorStepIndex = 0;
  highlightRunStep(store, detail, 0);

  for (let i = 1; i < steps.length; i++) {
    if (shouldAbort()) break;
    await abortableSleep(SCENARIO_RUN_STEP_MS, shouldAbort);
    if (shouldAbort()) break;

    runLib.scenarioRunLive = {
      abort: false,
      recordId,
      phase: 'animating',
      stepIndex: i,
      stepTotal: steps.length,
    };
    runLib.inspectorStepIndex = i;
    highlightRunStep(store, detail, i);
  }
}

export function useFlowScenarioRun() {
  const store = useFlowCanvasStore();
  const runLib = useRunLibraryStore();
  const { saveFlow } = useFlowGraph();
  const { abortSimulate } = useFlowSimulate();
  const { getActiveScenario, loadProjectEnvs, envOptions } = useRunConfig();

  const isScenarioRunActive = computed(() => !!runLib.scenarioRunLive);

  /**
   * 运行当前选中场景。
   * 成功后打开右栏运行详情、切换左栏至运行库，并按步骤动画高亮。
   * 就绪检查未通过则 toast 首条错误并中止，不发起 Run。
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

    // 先静默落盘（允许半成品），再统一做一次就绪检查，避免保存里再跑一遍
    if (store.dirty) {
      const saved = await saveFlow({ quiet: true, skipRunRiskRefresh: true });
      if (!saved) {
        ElMessage.warning('请先保存测试流后再运行');
        return null;
      }
    }

    const graphForRun = toGraphJson({
      nodes: store.nodes,
      edges: store.getEffectiveEdges(),
      viewport: store.viewport,
      runConfig: store.runConfig,
      flowOutputs: store.flowOutputs,
    });
    const snapshotCheck = validateSnapshotResetEndpointStatic(graphForRun, env);
    if (!snapshotCheck.ok) {
      ElMessage.error(snapshotCheck.message);
      return null;
    }

    const { errors: blocking, precheckErrors } = await collectRunBlockingErrors({
      graph: graphForRun,
      testProjectId: store.testProjectId,
    });
    // 把鉴权/必填子集写入校验条；结构错误已由实时结构校验展示
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

      const runId = String(triggerRes?.data ?? '').trim();
      if (!runId || runId === 'null' || runId === 'undefined') {
        ElMessage.error('触发运行失败：未返回 runId');
        return null;
      }

      runLib.scenarioRunLive = { abort: false, recordId: runId, phase: 'running' };
      const detail = await runLib.fetchRunDetail(runId);
      if (!detail) return null;

      if (runLib.scenarioRunLive?.abort) return null;

      runLib.upsertRun(detail);
      store.showRunPanel();
      store.ui.leftTab = 'runs';

      await animateRunSteps(detail);

      if (!runLib.scenarioRunLive?.abort) {
        if (detail.status === 'failed') {
          ElMessage.error(detail.errorMessage ?? '运行失败');
        } else {
          ElMessage.success('运行完成');
        }
      }

      return detail;
    } catch (e) {
      ElMessage.error((e as Error)?.message ?? '运行失败');
      return null;
    } finally {
      const aborted = runLib.scenarioRunLive?.abort;
      runLib.scenarioRunLive = null;
      if (aborted) store.clearRunHighlight();
    }
  }

  /**
   * 中止运行或运行动画。后端为同步执行，进行中的 HTTP 请求无法取消，仅忽略后续高亮与提示。
   */
  function abortScenarioRun() {
    if (runLib.scenarioRunLive) runLib.scenarioRunLive.abort = true;
  }

  return {
    runActiveScenario,
    abortScenarioRun,
    isScenarioRunActive,
    highlightRunStep,
  };
}
