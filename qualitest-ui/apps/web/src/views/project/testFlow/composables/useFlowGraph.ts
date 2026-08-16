/**
 * 测试流持久化读写：对接后端 testFlow API，与 flowCanvasStore 同步。
 * 保存前执行图结构校验与断言路径 schema 门禁，errors 阻断提交。
 */
import { nextTick } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';

import { getTestProjectApi } from '@/api/project/testProjectApi';
import { getTestFlow, updateTestFlow, upgradeTestFlowGraph, type TestFlowRecord } from '@/api/project/testFlow';
import {
  rewriteStartNodeErrorForPendingEdges,
  validateGraphJson,
} from '@/utils/flow/graphValidate';

import { fromGraphJson, toGraphJson, type FromGraphJsonResult } from '../graphAdapter';
import { refreshSavedBaseline, refreshSavedBaselineIfPristine } from '../utils/reconcileFlowDirty';
import { isBlockWhenStagingPending } from '../utils/aiDesignPreferences';
import { promptStagingPendingSave } from '../utils/promptStagingPendingSave';
import { hasPendingStagingEdgeUnits } from '../utils/stagingUnitIds';
import { useFlowHistory } from './useFlowHistory';
import { openPendingStagingReview } from './useStagingNavigation';
import { useFlowCanvasStore } from '../stores/flowCanvasStore';
import { useAiStagingStore } from '../stores/aiStagingStore';
import {
  collectAssertPathDesignIssues,
  extractResponseSchemaPaths,
  resolveTrialApiId,
} from '../utils/jsonPathTrial';

export interface SaveFlowOptions {
  /** 为 true 时跳过 pending 保存门禁（用于确认后自动保存） */
  skipPendingWarning?: boolean;
}

/** 将 fromGraphJson 结果写入 store：先 nodes、待灌边，节点就绪后再 flush edges */
async function applyAdaptedGraphToStore(store: ReturnType<typeof useFlowCanvasStore>, adapted: FromGraphJsonResult) {
  store.setPendingEdges(adapted.edges);
  store.viewport = adapted.viewport;
  store.runConfig = adapted.runConfig;
  store.flowOutputs = adapted.flowOutputs;
  store.nodes = adapted.nodes;
  store.edges = [];
  store.selected = null;
  store.ui.rightMode = 'props';
  store.clearRunHighlight();
  await nextTick();
  store.flushPendingEdges();
  await nextTick();
  if (store.pendingEdges?.length && store.edges.length === 0) {
    store.flushPendingEdges();
  } else if (store.edges.length > 0) {
    store.pendingEdges = null;
  }
}

export function useFlowGraph() {
  const store = useFlowCanvasStore();
  const stagingStore = useAiStagingStore();
  const { scheduleHistoryReset, resetHistory } = useFlowHistory();

  /** 空图画布无 onNodesInitialized，需立即建立撤销基线 */
  async function finalizeHistoryBaseline() {
    await nextTick();
    store.flushPendingEdges();
    if (!store.nodes.length) {
      resetHistory();
      store.pendingHistoryReset = false;
      store.endCanvasHydration();
      await refreshSavedBaselineIfPristine(store);
    }
  }

  /** 将 API 返回的测试流记录灌入画布 store */
  async function hydrateFlowRecord(data: TestFlowRecord) {
    store.testFlowId = String(data.testFlowId);
    store.testProjectId = String(data.testProjectId);
    store.flowName = data.flowName ?? '';
    const raw = data.graphJson ? JSON.parse(data.graphJson) : null;
    const adapted = fromGraphJson(raw);
    await applyAdaptedGraphToStore(store, adapted);
    store.markClean();
    scheduleHistoryReset();
    await finalizeHistoryBaseline();
    if (store.ui.leftTab === 'runConfig') {
      store.showScenarioPanel();
    }
    return adapted;
  }

  function formatUpgradeSummary(summary: string[] | undefined): string {
    if (!summary?.length) {
      return '将流程图升级到最新格式，以获得完整兼容性与校验支持。';
    }
    return summary.map((line) => `· ${line}`).join('\n');
  }

  /** 旧版 schema 时提示用户确认升级；确认后持久化并无感刷新画布 */
  async function promptGraphUpgradeIfNeeded(data: TestFlowRecord): Promise<TestFlowRecord> {
    if (!data.upgradeAvailable) {
      return data;
    }
    try {
      await ElMessageBox.confirm(formatUpgradeSummary(data.upgradeSummary), '升级流程图格式', {
        confirmButtonText: '立即升级',
        cancelButtonText: '暂不升级',
        type: 'info',
        distinguishCancelAndClose: true,
      });
    } catch {
      ElMessage.warning('当前为旧版图格式，建议升级以获得完整兼容性');
      return data;
    }
    const upgraded = await upgradeTestFlowGraph(data.testFlowId!);
    const next = upgraded.data as TestFlowRecord | undefined;
    if (!next?.graphJson) {
      throw new Error('图升级失败');
    }
    ElMessage.success('流程图已升级到最新格式');
    return next;
  }

  /** 按 testFlowId 拉取 graph_json 并灌入画布 store */
  async function loadFlow(testFlowId: string) {
    store.loading = true;
    store.beginCanvasHydration();
    try {
      const res = await getTestFlow(testFlowId);
      const data = res.data;
      if (!data) {
        throw new Error('测试流不存在');
      }
      const record = await promptGraphUpgradeIfNeeded(data);
      return await hydrateFlowRecord(record);
    } catch (error) {
      store.endCanvasHydration();
      throw error;
    } finally {
      store.loading = false;
    }
  }

  /**
   * 将当前画布序列化为 graph_json 并提交保存。
   * - pending>0 时默认弹门禁（去确认 / 仅保存已确认 / 取消），可 skipPendingWarning
   * - 序列化排除未 confirm 的 Staging 对象
   * - 校验失败阻断提交
   */
  async function saveFlow(options?: SaveFlowOptions) {
    await store.ensureEdgesHydrated();

    const pendingUnits = Object.values(stagingStore.unitsById).filter((u) => u.status === 'pending');
    const excludedPending = pendingUnits.length;
    if (excludedPending > 0 && !options?.skipPendingWarning) {
      const choice = await promptStagingPendingSave({
        pendingLabels: pendingUnits.map((u) => u.label),
        blockWhenStagingPending: isBlockWhenStagingPending(),
      });
      if (choice === 'focus') {
        openPendingStagingReview(store.edges);
        return false;
      }
      if (choice === 'cancel') {
        return false;
      }
    }

    const graph = toGraphJson({
      nodes: store.nodes,
      edges: store.edges,
      viewport: store.viewport,
      runConfig: store.runConfig,
      flowOutputs: store.flowOutputs,
      stagingFilter: stagingStore.buildPersistFilter(),
    });
    const validation = validateGraphJson(graph);
    if (!validation.ok) {
      const first = validation.errors[0] ?? '图校验失败';
      const message = hasPendingStagingEdgeUnits(Object.values(stagingStore.unitsById))
        ? rewriteStartNodeErrorForPendingEdges(first)
        : first;
      ElMessage.error(message);
      return false;
    }

    const assertPath = await loadAssertPathDesignIssues(graph);
    if (assertPath.errors.length) {
      ElMessage.error(assertPath.errors[0]);
      return false;
    }
    if (assertPath.warnings.length) {
      ElMessage.warning(assertPath.warnings[0]);
    }

    store.loading = true;
    try {
      await updateTestFlow({
        testFlowId: store.testFlowId,
        testProjectId: store.testProjectId,
        flowName: store.flowName,
        graphJson: JSON.stringify(graph),
      });
      await refreshSavedBaseline(store);
      if (excludedPending > 0) {
        ElMessage.success(`已保存（已排除 ${excludedPending} 项未确认 Staging）`);
      } else {
        ElMessage.success('保存成功');
      }
      return true;
    } catch (e: unknown) {
      ElMessage.error(e instanceof Error ? e.message : '保存失败');
      return false;
    } finally {
      store.loading = false;
    }
  }

  /**
   * 将校验通过的 graph_json 灌入画布 store，覆盖当前 nodes/edges/viewport/runConfig。
   * @returns 是否导入成功（校验失败返回 false）
   */
  async function importGraph(raw: unknown): Promise<boolean> {
    store.beginCanvasHydration();
    const validation = validateGraphJson(raw);
    if (!validation.ok) {
      store.endCanvasHydration();
      ElMessage.error(validation.errors[0] ?? '图校验失败');
      return false;
    }
    const adapted = fromGraphJson(raw);
    await applyAdaptedGraphToStore(store, adapted);
    store.markDirty();
    scheduleHistoryReset();
    await finalizeHistoryBaseline();
    return true;
  }

  return { loadFlow, saveFlow, importGraph };
}

/** 拉取 assert/condition 上游接口响应 schema，做保存前路径门禁（example 不参与硬拦） */
async function loadAssertPathDesignIssues(graph: {
  nodes?: Array<Record<string, unknown>>;
  edges?: Array<Record<string, unknown>>;
}): Promise<{ errors: string[]; warnings: string[] }> {
  const nodes = graph.nodes ?? [];
  const edges = graph.edges ?? [];
  const apiIds = new Set<string>();
  for (const node of nodes) {
    const type = String(node.type ?? '').trim().toLowerCase();
    if (type !== 'assert' && type !== 'condition') continue;
    const apiId = resolveTrialApiId(
      node as { id?: string; type?: string; data?: Record<string, unknown> },
      nodes as never,
      edges as never,
    );
    if (apiId) apiIds.add(apiId);
  }
  const schemaPathsByApiId = new Map<string, string[]>();
  await Promise.all(
    [...apiIds].map(async (apiId) => {
      try {
        const res = await getTestProjectApi(apiId);
        const detail = res?.data ?? res;
        schemaPathsByApiId.set(apiId, extractResponseSchemaPaths(detail?.responseConfig));
      } catch {
        schemaPathsByApiId.set(apiId, []);
      }
    }),
  );
  return collectAssertPathDesignIssues(graph, schemaPathsByApiId);
}
