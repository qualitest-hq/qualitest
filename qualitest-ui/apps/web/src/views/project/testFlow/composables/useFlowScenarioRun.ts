/**
 * 场景运行：触发正式 Run，执行中轮询详情并高亮画布上最新已完成步骤。
 *
 * 流程：脏图可先带错保存 → 运行就绪检查 → POST 触发（立刻得到 runId）→
 * 短间隔拉详情并高亮 → 终态 toast。
 * watchRunLive 也可由 AI 设计流在收到 runStarted 后调用。
 */
import { computed } from 'vue';
import { ElMessage } from 'element-plus';

import { triggerTestFlowRun } from '@/api/project/testFlowRun';
import { validateSnapshotResetEndpointStatic } from '@/utils/flow/snapshotPreRunValidate';

import { LIVE_RUN_POLL_MS } from '../constants/flowConfig';
import { toGraphJson } from '../graphAdapter';
import { useFlowCanvasStore } from '../stores/flowCanvasStore';
import type { RunRecord, RunRecordStatus } from '../stores/runLibraryStore';
import { useRunLibraryStore } from '../stores/runLibraryStore';
import { useRunRiskStore } from '../stores/runRiskStore';
import { abortableSleep } from '../utils/abortableSleep';
import { collectRunBlockingErrors } from '../utils/runReadiness';
import { endRunReplay } from './usePlayback';
import { useFlowGraph } from './useFlowGraph';
import { useFlowSimulate } from './useFlowSimulate';
import { useRunConfig } from './useRunConfig';

/** Run 已结束、可停止轮询的状态 */
const TERMINAL_STATUSES = new Set<RunRecordStatus>([
  'passed',
  'failed',
  'paused',
  'aborted',
  'cancelled',
]);

/**
 * 按 Run 步骤时间线高亮画布：0..stepIndex 写入已访问样式，当前步写入高亮节点。
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

/** 取最后一个带画布 nodeId 的步骤下标（跳过纯 run_config 等引导步） */
function lastGraphStepIndex(record: RunRecord): number {
  const steps = record.steps ?? [];
  for (let i = steps.length - 1; i >= 0; i--) {
    if (steps[i]?.nodeId) return i;
  }
  return steps.length > 0 ? steps.length - 1 : -1;
}

export function useFlowScenarioRun() {
  const store = useFlowCanvasStore();
  const runLib = useRunLibraryStore();
  const { saveFlow } = useFlowGraph();
  const { abortSimulate } = useFlowSimulate();
  const { getActiveScenario, loadProjectEnvs, envOptions } = useRunConfig();

  const isScenarioRunActive = computed(() => !!runLib.scenarioRunLive);

  /**
   * 执行中轮询 Run 详情，用最新已完成步骤高亮画布；到达终态后 toast 并停止。
   * @param takeOver false 时若已有其它 live 会话，只把本 run 写入运行库，不抢当前高亮
   */
  async function watchRunLive(
    runId: string,
    options?: { takeOver?: boolean },
  ): Promise<RunRecord | null> {
    const takeOver = options?.takeOver !== false;
    const live = runLib.scenarioRunLive;
    if (
      !takeOver
      && live
      && live.recordId
      && !String(live.recordId).startsWith('pending-')
      && live.recordId !== runId
    ) {
      const detail = await runLib.fetchRunDetail(runId);
      if (detail) runLib.upsertRun(detail);
      return detail;
    }

    runLib.scenarioRunLive = { abort: false, recordId: runId, phase: 'running' };
    store.showRunPanel();
    store.ui.leftTab = 'runs';

    let lastDetail: RunRecord | null = null;
    try {
      while (runLib.scenarioRunLive && !runLib.scenarioRunLive.abort) {
        const detail = await runLib.fetchRunDetail(runId);
        if (!detail) break;
        lastDetail = detail;
        runLib.upsertRun(detail);
        runLib.selectedRunId = runId;

        const stepIdx = lastGraphStepIndex(detail);
        if (stepIdx >= 0) {
          const aborted = !!runLib.scenarioRunLive?.abort;
          runLib.scenarioRunLive = {
            abort: aborted,
            recordId: runId,
            phase: 'running',
            stepIndex: stepIdx,
            stepTotal: detail.steps.length,
          };
          runLib.inspectorStepIndex = stepIdx;
          highlightRunStep(store, detail, stepIdx);
        }

        if (TERMINAL_STATUSES.has(detail.status)) {
          if (!runLib.scenarioRunLive?.abort) {
            if (detail.status === 'failed') {
              ElMessage.error(detail.errorMessage ?? '运行失败');
            } else if (detail.status === 'paused') {
              ElMessage.warning(detail.errorMessage ?? '运行已暂停');
            } else if (detail.status === 'passed') {
              ElMessage.success('运行完成');
            }
          }
          break;
        }

        await abortableSleep(LIVE_RUN_POLL_MS, () => !!runLib.scenarioRunLive?.abort);
      }
      return lastDetail;
    } finally {
      const aborted = !!runLib.scenarioRunLive?.abort;
      if (runLib.scenarioRunLive?.recordId === runId) {
        runLib.scenarioRunLive = null;
      }
      if (aborted) store.clearRunHighlight();
    }
  }

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

      return await watchRunLive(runId, { takeOver: true });
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
    watchRunLive,
    isScenarioRunActive,
    highlightRunStep,
  };
}
