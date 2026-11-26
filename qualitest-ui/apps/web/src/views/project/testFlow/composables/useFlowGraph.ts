/**
 * 测试流持久化读写：对接后端 testFlow API，与画布 store 同步。
 *
 * 保存规则：图校验错误与路由冲突都不拦保存（只要求请求体可解析 + 版本/锁等落库条件）；
 * 画布提示错误，开跑前另做完整就绪检查并硬拦。
 *
 * 半自动下 Staging ✓ 后须调用本模块的 saveFlow 才写库；
 * 若仍有 pending，先弹门禁（去确认 / 仅保存已确认 / 取消）。
 *
 * 写图请求体携带本地图版本号；冲突时仅布局脏自动套回排版再存，内容冲突弹窗分流。
 */
import { ElMessage, ElMessageBox } from 'element-plus';

import { getTestFlow, updateTestFlow, type TestFlowRecord } from '@/api/project/testFlow';
import { validateGraphJson } from '@/utils/flow/graphValidate';

import { fromGraphJson } from '../graphAdapter';
import { buildCanvasPersistGraph } from './buildCanvasPersistGraph';
import { refreshSavedBaseline } from '../utils/reconcileFlowDirty';
import { promptStagingPendingSave } from '../utils/promptStagingPendingSave';
import { collectRunBlockingErrors } from '../utils/runReadiness';
import { suppressExternalGraphSync, acknowledgeLocalWebSave } from '../utils/externalGraphSyncState';
import { getFlowEditLeaseToken } from '../utils/flowEditLeaseState';
import { isLayoutOnlyDirtyFromStore } from '../utils/isLayoutOnlyDirty';
import { holdCanvasLayout, reapplyHeldCanvasLayout } from '../utils/nodePositions';
import {
  extractGraphRevisionFromResponse,
  parseGraphRevisionConflict,
} from '../utils/graphRevisionConflict';
import { clearAllStagingState } from '../utils/stagingCleanup';
import { resetStagingAcceptanceMaps } from '../utils/stagingAcceptance';
import { useFlowHistory } from './useFlowHistory';
import { openPendingStagingReview } from './useStagingNavigation';
import { useFlowCanvasStore } from '../stores/flowCanvasStore';
import { useAiStagingStore } from '../stores/aiStagingStore';
import { useRunRiskStore } from '../stores/runRiskStore';
import {
  applyAdaptedGraphToStore,
  finalizeCanvasHistoryBaseline,
} from './useCanvasGraphHydration';
import { recoverLoginFlowEdgesIfMissing } from '../../testProjectTemplate/utils/templateCanvasHydrate';

export interface SaveFlowOptions {
  /** true：不弹成功/风险 toast（开跑前静默落盘等场景） */
  quiet?: boolean;
  /**
   * true：保存成功后不做运行风险刷新与「暂不可运行」计数。
   * 开跑路径会紧接着自己做一次完整就绪检查，避免重复请求。
   */
  skipRunRiskRefresh?: boolean;
  /** 内部：版本冲突自动重试中，避免死循环 */
  _conflictRetry?: boolean;
}

export function useFlowGraph() {
  const store = useFlowCanvasStore();
  const stagingStore = useAiStagingStore();
  const runRisk = useRunRiskStore();
  const { scheduleHistoryReset, resetHistory } = useFlowHistory();

  async function finalizeHistoryBaseline() {
    await finalizeCanvasHistoryBaseline(store, resetHistory);
  }

  /** 将接口返回的测试流记录灌入画布 store */
  async function hydrateFlowRecord(data: TestFlowRecord) {
    store.testFlowId = String(data.testFlowId);
    store.testProjectId = String(data.testProjectId);
    store.flowName = data.flowName ?? '';
    store.setGraphRevision(data.graphRevision ?? 0);
    const raw = data.graphJson ? JSON.parse(data.graphJson) : null
    if (raw && typeof raw === 'object' && !Array.isArray(raw)) {
      raw.edges = recoverLoginFlowEdgesIfMissing(raw.nodes, raw.edges);
    }
    const adapted = fromGraphJson(raw);
    await applyAdaptedGraphToStore(store, adapted);
    store.markClean();
    scheduleHistoryReset();
    await finalizeHistoryBaseline();
    return adapted;
  }

  /** 按 testFlowId 拉取图并灌入画布 */
  async function loadFlow(testFlowId: string) {
    store.loading = true;
    store.beginCanvasHydration();
    try {
      const res = await getTestFlow(testFlowId);
      const data = res.data;
      if (!data) {
        throw new Error('测试流不存在');
      }
      return await hydrateFlowRecord(data);
    } catch (error) {
      store.endCanvasHydration();
      throw error;
    } finally {
      store.loading = false;
    }
  }

  /** 拉取库中当前图版本号 */
  async function fetchLatestGraphRevision(flowId: string): Promise<number> {
    const res = await getTestFlow(flowId);
    const n = Number(res.data?.graphRevision);
    return Number.isFinite(n) ? n : 0;
  }

  /**
   * 只改了排版时碰到版本冲突：拉最新图，再套回本地坐标、视口和排版方式，然后自动重存。
   */
  async function retrySaveAfterLayoutOnlyConflict(options?: SaveFlowOptions) {
    const flowId = String(store.testFlowId || '');
    if (!flowId) return false;
    const held = holdCanvasLayout(store);
    await loadFlow(flowId);
    reapplyHeldCanvasLayout(store, held);
    return saveFlow({ ...options, _conflictRetry: true });
  }

  /**
   * 内容脏时的版本冲突：放弃本地并拉取，或二次确认后强制用本地图覆盖。
   */
  async function resolveContentRevisionConflict(
    serverRevision: number,
    options?: SaveFlowOptions,
  ): Promise<boolean> {
    try {
      await ElMessageBox.confirm(
        '画布已被外部更新，本地有未保存的内容修改。可放弃本地并拉取最新图，或强制用本地图覆盖服务器。',
        '版本冲突',
        {
          type: 'warning',
          confirmButtonText: '放弃本地并拉取',
          cancelButtonText: '强制覆盖',
          distinguishCancelAndClose: true,
          closeOnClickModal: false,
        },
      );
      clearAllStagingState();
      resetStagingAcceptanceMaps();
      store.markClean();
      const flowId = String(store.testFlowId || '');
      if (flowId) {
        await loadFlow(flowId);
        if (!options?.quiet) {
          ElMessage.success('已放弃本地修改并拉取最新画布');
        }
      }
      return false;
    } catch (action) {
      if (action !== 'cancel') {
        return false;
      }
      try {
        await ElMessageBox.confirm(
          '强制覆盖将用本地图覆盖服务器上的最新内容，可能导致他人已写入的节点丢失。确定继续？',
          '确认强制覆盖',
          {
            type: 'warning',
            confirmButtonText: '确定覆盖',
            cancelButtonText: '取消',
            closeOnClickModal: false,
          },
        );
      } catch {
        return false;
      }
      const flowId = String(store.testFlowId || '');
      let base = serverRevision;
      if (!Number.isFinite(base) || base < 0) {
        try {
          base = await fetchLatestGraphRevision(flowId);
        } catch {
          if (!options?.quiet) {
            ElMessage.error('获取最新版本失败');
          }
          return false;
        }
      }
      store.setGraphRevision(base);
      return saveFlow({ ...options, _conflictRetry: true });
    }
  }

  /**
   * 序列化当前画布并提交保存。
   * - 有未确认 Staging 时弹窗：去确认 / 仅保存已确认 / 取消
   * - 序列化时排除未确认的 Staging 对象；「仅保存已确认」即走此路径
   * - 仅落库地板失败才阻断；其余问题可落盘，并提示尚不可运行
   * - 写图携带本地图版本号作基准；冲突按仅布局脏 / 内容脏分流
   */
  async function saveFlow(options?: SaveFlowOptions) {
    await store.ensureEdgesHydrated();

    const pendingUnits = Object.values(stagingStore.unitsById).filter((u) => u.status === 'pending');
    const excludedPending = pendingUnits.length;
    if (excludedPending > 0) {
      const choice = await promptStagingPendingSave({
        pendingLabels: pendingUnits.map((u) => u.label),
      });
      if (choice === 'focus') {
        openPendingStagingReview(store.edges);
        return false;
      }
      if (choice === 'cancel') {
        return false;
      }
    }

    const graph = buildCanvasPersistGraph();
    // 保存不再因图校验 / 路由冲突拦截：错误留给画布提示与开跑门禁；
    // 仅无法解析的请求体、版本冲突等落库问题仍会失败。

    store.loading = true;
    /** 冲突分流已接管后续异步流程时，finally 不再清 loading */
    let conflictHandoff = false;
    try {
      // 须在发请求前抑制：服务端变更通知可能早于 HTTP 返回到达本页
      suppressExternalGraphSync();
      // 请求头带上本标签写锁 token；body 带图版本号作乐观锁基准
      const res = await updateTestFlow(
        {
          testFlowId: store.testFlowId,
          testProjectId: store.testProjectId,
          flowName: store.flowName,
          graphJson: JSON.stringify(graph),
          graphRevision: store.graphRevision,
        },
        getFlowEditLeaseToken(String(store.testFlowId || '')),
      );
      // 清掉抑制窗口前误亮的「其它端保存」条幅
      acknowledgeLocalWebSave();
      const nextRev = extractGraphRevisionFromResponse(res);
      store.setGraphRevision(nextRev != null ? nextRev : store.graphRevision + 1);
      await refreshSavedBaseline(store);

      if (!options?.skipRunRiskRefresh && !options?.quiet) {
        // 刷新运行风险条：只把鉴权/必填写入 store；结构/断言仍由实时结构校验展示
        const { errors, precheckErrors } = await collectRunBlockingErrors({
          graph,
          testProjectId: store.testProjectId,
          testFlowId: store.testFlowId,
        });
        runRisk.setWarnings(precheckErrors);
        if (excludedPending > 0) {
          ElMessage.success(`已保存（已排除 ${excludedPending} 项未确认 Staging）`);
        } else if (errors.length) {
          ElMessage.warning(
            `已保存，尚有 ${errors.length} 项问题暂不可运行（见左上角校验条）`,
          );
        } else {
          ElMessage.success('保存成功');
        }
      } else if (!options?.quiet) {
        if (excludedPending > 0) {
          ElMessage.success(`已保存（已排除 ${excludedPending} 项未确认 Staging）`);
        } else {
          ElMessage.success('保存成功');
        }
      }
      return true;
    } catch (e: unknown) {
      // 图版本冲突：仅布局脏则套本地坐标重存；内容脏则弹窗让用户选择
      const conflictRev = parseGraphRevisionConflict(e);
      if (conflictRev != null && !options?._conflictRetry) {
        conflictHandoff = true;
        store.loading = false;
        if (isLayoutOnlyDirtyFromStore(store, stagingStore.pendingCount)) {
          return retrySaveAfterLayoutOnlyConflict(options);
        }
        return resolveContentRevisionConflict(conflictRev, options);
      }
      // 写锁 / 跨流路由冲突等：quiet 也展示服务端文案（含占用方）
      ElMessage.error(e instanceof Error ? e.message : '保存失败');
      return false;
    } finally {
      if (!conflictHandoff) {
        store.loading = false;
      }
    }
  }

  /**
   * 导入 graph_json 覆盖当前画布。
   * 与保存对齐：只拦落盘地板；挂图异常与路由冲突可导入，导入后由校验条标红。
   */
  async function importGraph(raw: unknown): Promise<boolean> {
    store.beginCanvasHydration();
    try {
      const floor = validateGraphJson(raw, { persistMinimalOnly: true });
      if (!floor.ok) {
        store.endCanvasHydration();
        ElMessage.error(floor.errors[0] ?? '图校验失败');
        return false;
      }
      const adapted = fromGraphJson(raw);
      await applyAdaptedGraphToStore(store, adapted);
      store.markDirty();
      scheduleHistoryReset();
      await finalizeHistoryBaseline();
      return true;
    } catch (e: unknown) {
      store.endCanvasHydration();
      ElMessage.error(e instanceof Error ? e.message : '导入失败');
      return false;
    }
  }

  return { loadFlow, saveFlow, importGraph };
}
