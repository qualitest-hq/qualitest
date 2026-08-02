/**
 * Staging 单元确认与取消的编排入口。
 *
 * 负责依赖检查、服务端校验、画布落盘、撤销历史入栈、自动保存，以及 reject 时的画布回滚。
 */
import { ElMessage } from 'element-plus';

import { useAiStagingStore } from '../stores/aiStagingStore';
import { useFlowCanvasStore } from '../stores/flowCanvasStore';
import { isAutoSaveAfterConfirm } from '../utils/aiDesignPreferences';
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

/** Vitest：清空全局 confirm 单飞状态，避免用例间串扰 */
export function resetStagingConfirmGatesForTests() {
  confirmBusyUnitId = null;
  confirmApplyTail = Promise.resolve();
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

  /** 确认单个 Staging 单元：校验 → 自动重试 → 落盘 → 入撤销栈 → 可选自动保存 */
  async function submitConfirmUnit(unitId: string) {
    const unit = stagingStore.getUnit(unitId);
    if (!unit || unit.status !== 'pending' || unit.confirmInFlight) return;
    // 同步占坑：全局单飞，必须在任何 await 之前
    if (confirmBusyUnitId != null) return;
    confirmBusyUnitId = unitId;

    try {
      const patch = stagingStore.getPatchForMessage(unit.messageId);
      if (!patch) {
        ElMessage.warning('找不到对应的 AI patch');
        return;
      }

      const confirmedIds = new Set(stagingStore.listConfirmedUnitIds());
      const dependency = resolveStagingConfirmDependency(unitId, patch, confirmedIds);
      if (dependency.blocked) {
        ElMessage.warning(dependency.blockTitle);
        return;
      }

      stagingStore.setConfirmInFlight(unitId, true);

      try {
        const projectId = store.testProjectId?.trim();
        if (!projectId) {
          ElMessage.warning('缺少测试项目 id');
          return;
        }

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

          // --- 视口聚焦编排：等画布稳定 → 聚焦下一单元 → 等动画结束 → 入撤销栈 ---
          // 必须在节点尺寸测量与边灌入完成后再移动视角，否则后续布局变化会干扰视口。
          await viewport.waitForCanvasReady();

          const confirmedIds = new Set(stagingStore.listConfirmedUnitIds());
          // 优先聚焦同消息内下一个待确认单元（节点→连线→节点顺序）
          let nextUnit = resolveNextStagingFocusUnit(
            unit,
            patch,
            stagingStore.listUnitsForMessage(unit.messageId),
            confirmedIds,
            store.edges,
          );
          // 本消息已无图单元时，退而聚焦全局第一个 pending 图单元
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
            // 无下一单元但仍有紫色高亮时，保持当前高亮区域在视野内
            await viewport.focusNodeIds([...store.aiHighlightNodeIds]);
          }

          // 动画结束后再 pushHistory，确保撤销栈记录的是最终视口
          await viewport.waitForViewportSettled();
          pushHistory();

          await maybeAutoSaveAfterConfirm();
          const doneLabel = isDeleteStagingUnit(unit) ? '已确认删除' : '已确认变更';
          if (attempts > 1) {
            ElMessage.success(`${doneLabel}（第 ${attempts} 次尝试成功）`);
          } else {
            ElMessage.success(doneLabel);
          }
        });
      } catch (error) {
        const message = error instanceof Error ? error.message : '确认请求失败';
        markConfirmFailure(
          unitId,
          undefined,
          [message, `已自动重试 ${CONFIRM_UNIT_AUTO_RETRY_MAX} 次`],
        );
      } finally {
        stagingStore.setConfirmInFlight(unitId, false);
      }
    } finally {
      if (confirmBusyUnitId === unitId) confirmBusyUnitId = null;
    }
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

  return { confirmUnit, retryUnit, rejectUnit };
}
