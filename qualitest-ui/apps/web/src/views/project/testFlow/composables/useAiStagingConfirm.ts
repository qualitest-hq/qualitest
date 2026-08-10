/**
 * Staging 单元确认与取消的编排入口。
 *
 * 负责依赖检查、服务端校验、画布落盘、撤销历史入栈、自动保存，以及 reject 时的画布回滚。
 * 另提供「确认全部就绪」波次串行批量确认（失败停顿、可跳过继续）。
 */
import { computed, ref } from 'vue';
import { ElMessage } from 'element-plus';

import { useAiStagingStore } from '../stores/aiStagingStore';
import { useFlowCanvasStore } from '../stores/flowCanvasStore';
import { isAutoSaveAfterConfirm } from '../utils/aiDesignPreferences';
import { listReadyPendingUnitIds } from '../utils/listReadyPendingUnits';
import { collectStagingConfirmHighlightIds } from '../utils/mergeHighlight';
import { resolveStagingConfirmDependency } from '../utils/stagingDependencyHints';
import { revertStagingUnitOnCanvas } from '../utils/stagingCanvasRevert';
import { resolveNextStagingFocusUnit } from '../utils/stagingFocusNavigation';
import { isDeleteStagingUnit, objectIdFromUnitId } from '../utils/stagingUnitIds';
import { findFirstPendingGraphUnit } from './useStagingNavigation';
import { useFlowViewport } from './useFlowViewport';
import {
  applyConfirmResultWithHashGuard,
} from './stagingConfirmRequest';
import {
  CONFIRM_UNIT_AUTO_RETRY_MAX,
  requestConfirmWithAutoRetry,
} from '../utils/stagingConfirmRetry';
import { removeStagingEdgeFromCanvas } from './useAiStagingCanvas';
import { useFlowGraph } from './useFlowGraph';
import { useFlowHistory } from './useFlowHistory';
import {
  recordStagingUnitConfirmed,
  recordStagingUnitRejected,
} from '../utils/stagingAcceptance';

/** 批量 confirm 占用单飞时的占位 unitId */
const BATCH_BUSY_TOKEN = '__batch__';

/**
 * 全局 confirm 落盘队列尾指针。
 * 多个单元快速连续确认时，后续请求必须等前一次 API + 画布写入完成，否则会基于过期画布互相覆盖。
 */
let confirmApplyTail: Promise<void> = Promise.resolve();

/**
 * 当前占坑中的 confirm unitId；非 null 时拒绝其它 unit 进入。
 * 全局单飞：避免连点多个 ✓ 并行进入（昔日 MessageBox 叠层闪烁同源）。
 * 须在首个 await 之前同步登记。
 */
let confirmBusyUnitId: string | null = null;

/** 批量确认进行中（与 confirmBusyUnitId 同步占坑，供 UI 禁用按钮） */
export const stagingBatchConfirmBusy = ref(false);

/** 批量确认因失败停顿的 unitId；非 null 时可「跳过失败继续」 */
export const stagingBatchPausedOnUnitId = ref<string | null>(null);

/** 批量确认跳过集（含用户选择跳过的失败项） */
let batchSkipUnitIds = new Set<string>();

type ConfirmUnitCoreOptions = {
  quiet?: boolean;
  skipViewportFocus?: boolean;
  skipAutoSave?: boolean;
};

type ConfirmUnitCoreResult = 'ok' | 'failed' | 'aborted';

/** Vitest：清空全局 confirm 单飞状态，避免用例间串扰 */
export function resetStagingConfirmGatesForTests() {
  confirmBusyUnitId = null;
  confirmApplyTail = Promise.resolve();
  stagingBatchConfirmBusy.value = false;
  stagingBatchPausedOnUnitId.value = null;
  batchSkipUnitIds = new Set();
}

/** 将一次 confirm 的完整流程（请求、落盘、入历史、自动保存）排进串行队列 */
function enqueueConfirmApply<T>(fn: () => Promise<T>): Promise<T> {
  const next = confirmApplyTail.then(() => fn());
  confirmApplyTail = next.then(
    () => undefined,
    () => undefined,
  );
  return next;
}

export function useAiStagingConfirm() {
  const store = useFlowCanvasStore();
  const stagingStore = useAiStagingStore();
  const { pushHistory } = useFlowHistory();
  const { saveFlow } = useFlowGraph();
  const viewport = useFlowViewport();

  /** 本批 Staging 无待确认项时，启动统一熄灭计时 */
  function maybeFinalizeStagingHighlight() {
    if (stagingStore.pendingCount === 0) {
      store.finalizeAiConfirmHighlight();
    }
  }

  function markConfirmFailure(
    unitId: string,
    dependencyHints: string[] | undefined,
    errors: string[],
    warnings: string[] = [],
  ) {
    stagingStore.markConfirmFailed(
      unitId,
      { ok: false, errors, warnings },
      { dependencyHints },
    );
  }

  /**
   * 用户开启「确认后自动保存」时，在 confirm 成功后触发保存。
   * 跳过「尚有待确认项」提示，因为本次保存意图就是落盘刚确认的内容。
   */
  async function maybeAutoSaveAfterConfirm() {
    if (!isAutoSaveAfterConfirm()) return;
    const saved = await saveFlow({ skipPendingWarning: true });
    if (!saved) {
      ElMessage.warning('已确认变更，但自动保存失败，请手动保存');
    }
  }

  /**
   * 确认删除节点后，自动拒绝同消息下仍挂在该节点上的 pending 新增边。
   * 避免节点已删但悬空边仍留在画布上。
   */
  function rejectRelatedAddEdges(messageId: string, nodeId: string) {
    const patch = stagingStore.getPatchForMessage(messageId);
    for (const edge of patch?.addEdges ?? []) {
      if (edge.source === nodeId || edge.target === nodeId) {
        const edgeUnitId = `addEdge:${edge.id}`;
        const edgeUnit = stagingStore.getUnit(edgeUnitId);
        if (edgeUnit?.status === 'pending') {
          removeStagingEdgeFromCanvas(edge.id);
          stagingStore.markRejected(edgeUnitId);
          recordStagingUnitRejected(messageId, edgeUnitId);
        }
      }
    }
  }

  async function focusUnitOnCanvas(unitId: string) {
    const unit = stagingStore.getUnit(unitId);
    if (!unit) return;
    const patch = stagingStore.getPatchForMessage(unit.messageId);
    const nodeIds = collectStagingConfirmHighlightIds(
      unitId,
      unit.kind,
      store.edges,
      patch,
    );
    if (!nodeIds.length) return;
    await viewport.waitForCanvasReady();
    await viewport.focusNodeIds(nodeIds);
  }

  /**
   * 单单元确认核心（须由调用方持有 busy 占坑）。
   * 返回 ok / failed / aborted（缺 patch、依赖阻断、缺项目 id 等未发起确认）。
   */
  async function confirmUnitCore(
    unitId: string,
    opts: ConfirmUnitCoreOptions = {},
  ): Promise<ConfirmUnitCoreResult> {
    const unit = stagingStore.getUnit(unitId);
    if (!unit || unit.status !== 'pending' || unit.confirmInFlight) return 'aborted';

    const patch = stagingStore.getPatchForMessage(unit.messageId);
    if (!patch) {
      if (!opts.quiet) ElMessage.warning('找不到对应的 AI patch');
      return 'aborted';
    }

    const confirmedIds = new Set(stagingStore.listConfirmedUnitIds());
    const dependency = resolveStagingConfirmDependency(unitId, patch, confirmedIds);
    if (dependency.blocked) {
      if (!opts.quiet) ElMessage.warning(dependency.blockTitle);
      return 'aborted';
    }

    stagingStore.setConfirmInFlight(unitId, true);

    try {
      const projectId = store.testProjectId?.trim();
      if (!projectId) {
        if (!opts.quiet) ElMessage.warning('缺少测试项目 id');
        return 'aborted';
      }

      let outcome: ConfirmUnitCoreResult = 'failed';

      await enqueueConfirmApply(async () => {
        const { result, requestBaseHash, attempts } = await requestConfirmWithAutoRetry(
          unitId,
          patch,
          projectId,
          stagingStore,
          store,
        );
        const dependencyHints = result.dependencyHints ?? [];

        if (!result.validation.ok || !result.graphJson) {
          const errors = dependencyHints.length
            ? dependencyHints
            : result.validation.errors.length
              ? result.validation.errors
              : ['确认失败'];
          if (attempts > 1 && !dependencyHints.length) {
            errors.push(`已自动重试 ${attempts} 次`);
          }
          markConfirmFailure(
            unitId,
            dependencyHints,
            errors,
            result.validation.warnings,
          );
          outcome = 'failed';
          return;
        }

        const applied = await applyConfirmResultWithHashGuard(
          unitId,
          result,
          requestBaseHash,
          patch,
          projectId,
          stagingStore,
          store,
          () => markConfirmFailure(
            unitId,
            undefined,
            ['画布在确认期间已变更，请再次点击确认或重试'],
          ),
        );
        if (!applied) {
          outcome = 'failed';
          return;
        }

        stagingStore.markConfirmed(unitId);
        recordStagingUnitConfirmed(unit.messageId, unitId);

        if (unit.kind === 'deleteNode') {
          rejectRelatedAddEdges(unit.messageId, objectIdFromUnitId(unitId));
        }

        const fitIds = collectStagingConfirmHighlightIds(unitId, unit.kind, store.edges);
        if (fitIds.length) {
          store.addAiConfirmHighlight(fitIds);
        }
        maybeFinalizeStagingHighlight();

        await viewport.waitForCanvasReady();

        if (!opts.skipViewportFocus) {
          const nextConfirmedIds = new Set(stagingStore.listConfirmedUnitIds());
          let nextUnit = resolveNextStagingFocusUnit(
            unit,
            patch,
            stagingStore.listUnitsForMessage(unit.messageId),
            nextConfirmedIds,
            store.edges,
          );
          if (!nextUnit) {
            nextUnit = findFirstPendingGraphUnit(Object.values(stagingStore.unitsById));
          }
          if (nextUnit) {
            const patchForNext = stagingStore.getPatchForMessage(nextUnit.messageId);
            const nodeIds = collectStagingConfirmHighlightIds(
              nextUnit.unitId,
              nextUnit.kind,
              store.edges,
              patchForNext,
            );
            if (nodeIds.length) {
              await viewport.focusNodeIds(nodeIds);
            }
          } else if (store.aiHighlightNodeIds.length) {
            await viewport.focusNodeIds([...store.aiHighlightNodeIds]);
          }
        }

        await viewport.waitForViewportSettled();
        pushHistory();

        if (!opts.skipAutoSave) {
          await maybeAutoSaveAfterConfirm();
        }

        if (!opts.quiet) {
          const doneLabel = isDeleteStagingUnit(unit) ? '已确认删除' : '已确认变更';
          if (attempts > 1) {
            ElMessage.success(`${doneLabel}（第 ${attempts} 次尝试成功）`);
          } else {
            ElMessage.success(doneLabel);
          }
        }

        outcome = 'ok';
      });

      return outcome;
    } catch (error) {
      const message = error instanceof Error ? error.message : '确认请求失败';
      markConfirmFailure(
        unitId,
        undefined,
        [message, `已自动重试 ${CONFIRM_UNIT_AUTO_RETRY_MAX} 次`],
      );
      return 'failed';
    } finally {
      stagingStore.setConfirmInFlight(unitId, false);
    }
  }

  /** 确认单个 Staging 单元：校验 → 自动重试 → 落盘 → 入撤销栈 → 可选自动保存 */
  async function submitConfirmUnit(unitId: string) {
    const unit = stagingStore.getUnit(unitId);
    if (!unit || unit.status !== 'pending' || unit.confirmInFlight) return;
    // 同步占坑：全局单飞，必须在任何 await 之前
    if (confirmBusyUnitId != null) return;
    confirmBusyUnitId = unitId;

    try {
      await confirmUnitCore(unitId);
    } finally {
      if (confirmBusyUnitId === unitId) confirmBusyUnitId = null;
    }
  }

  function collectPendingUnitRefs() {
    return Object.values(stagingStore.unitsById)
      .filter((u) => u.status === 'pending')
      .map((u) => ({ unitId: u.unitId, messageId: u.messageId }));
  }

  function currentReadyUnitIds(skip?: ReadonlySet<string>) {
    void stagingStore.unitsById;
    return listReadyPendingUnitIds({
      pendingUnits: collectPendingUnitRefs(),
      getPatchForMessage: (messageId) => stagingStore.getPatchForMessage(messageId),
      confirmedUnitIds: new Set(stagingStore.listConfirmedUnitIds()),
      skipUnitIds: skip ?? batchSkipUnitIds,
    });
  }

  /** 工具条角标：当前依赖已满足的 pending 数（不含批量 skip 集） */
  const readyPendingCount = computed(() => currentReadyUnitIds(new Set()).length);

  async function runConfirmAllReadyLoop() {
    let totalOk = 0;

    while (true) {
      const ready = currentReadyUnitIds(batchSkipUnitIds);
      if (!ready.length) break;

      let successThisWave = 0;
      for (const unitId of ready) {
        // 波次快照内可能已被上一拍间接 reject（如 deleteNode 清边），跳过
        const unit = stagingStore.getUnit(unitId);
        if (!unit || unit.status !== 'pending') continue;

        const outcome = await confirmUnitCore(unitId, {
          quiet: true,
          skipViewportFocus: true,
          skipAutoSave: true,
        });

        if (outcome === 'ok') {
          successThisWave += 1;
          totalOk += 1;
          continue;
        }

        if (outcome === 'failed') {
          stagingBatchPausedOnUnitId.value = unitId;
          await focusUnitOnCanvas(unitId);
          const label = unit.label || unitId;
          ElMessage.error(`确认失败：${label}（已确认 ${totalOk} 项，可跳过失败继续）`);
          return;
        }
      }

      if (successThisWave === 0) break;
    }

    stagingBatchPausedOnUnitId.value = null;
    if (totalOk > 0) {
      ElMessage.success(`已确认 ${totalOk} 项`);
      await maybeAutoSaveAfterConfirm();
    } else if (stagingStore.pendingCount > 0 && currentReadyUnitIds(batchSkipUnitIds).length === 0) {
      ElMessage.info('当前没有可确认项（可能仍有依赖未满足）');
    }
  }

  /** 占住全局单飞 + 批量 busy 标记，跑完后释放 */
  async function withBatchBusy(run: () => Promise<void>) {
    if (confirmBusyUnitId != null) return;
    confirmBusyUnitId = BATCH_BUSY_TOKEN;
    stagingBatchConfirmBusy.value = true;
    try {
      await run();
    } finally {
      stagingBatchConfirmBusy.value = false;
      if (confirmBusyUnitId === BATCH_BUSY_TOKEN) confirmBusyUnitId = null;
    }
  }

  /** 按依赖波次串行确认全部就绪 pending；失败停顿，不静默跳过 */
  async function confirmAllReady() {
    await withBatchBusy(async () => {
      stagingBatchPausedOnUnitId.value = null;
      batchSkipUnitIds = new Set();
      await runConfirmAllReadyLoop();
    });
  }

  /** 跳过上次失败单元，继续批量确认其余就绪项 */
  async function resumeConfirmAllReady() {
    const pausedId = stagingBatchPausedOnUnitId.value;
    if (!pausedId) return;
    await withBatchBusy(async () => {
      batchSkipUnitIds.add(pausedId);
      stagingBatchPausedOnUnitId.value = null;
      await runConfirmAllReadyLoop();
    });
  }

  const confirmUnit = submitConfirmUnit;
  /** 手动重试：与确认共用同一套自动重试链路 */
  const retryUnit = submitConfirmUnit;

  /** 拒绝单元：回滚画布视觉效果，标记为 rejected */
  function rejectUnit(unitId: string) {
    const unit = stagingStore.getUnit(unitId);
    if (!unit || unit.status !== 'pending' || unit.confirmInFlight) return;

    const objectId = objectIdFromUnitId(unitId);

    if (unit.kind === 'addNode') {
      rejectRelatedAddEdges(unit.messageId, objectId);
    }

    revertStagingUnitOnCanvas(unit);
    stagingStore.markRejected(unitId);
    recordStagingUnitRejected(unit.messageId, unitId);
    maybeFinalizeStagingHighlight();
  }

  return {
    confirmUnit,
    retryUnit,
    rejectUnit,
    confirmAllReady,
    resumeConfirmAllReady,
    readyPendingCount,
    batchConfirmBusy: stagingBatchConfirmBusy,
    batchPausedOnUnitId: stagingBatchPausedOnUnitId,
  };
}
