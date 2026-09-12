/**
 * 测试流持久化读写：对接后端 testFlow API，与画布 store 同步。
 *
 * 保存规则：只拦「无法解析 / 节点缺 id / 边缺端点」；
 * 结构细节、断言路径、鉴权缺失等允许带错落盘，方便继续改图。
 * 开跑前另做完整就绪检查。
 */
import { ElMessage } from 'element-plus';

import { getTestFlow, updateTestFlow, type TestFlowRecord } from '@/api/project/testFlow';
import { validateGraphJson } from '@/utils/flow/graphValidate';

import { fromGraphJson, toGraphJson } from '../graphAdapter';
import { refreshSavedBaseline } from '../utils/reconcileFlowDirty';
import { isBlockWhenStagingPending } from '../utils/aiDesignPreferences';
import { promptStagingPendingSave } from '../utils/promptStagingPendingSave';
import { collectRunBlockingErrors } from '../utils/runReadiness';
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
  /** true：跳过「尚有未确认 Staging」提示框（确认后自动保存等场景） */
  skipPendingWarning?: boolean;
  /** true：不弹成功/风险 toast（开跑前静默落盘等场景） */
  quiet?: boolean;
  /**
   * true：保存成功后不做运行风险刷新与「暂不可运行」计数。
   * 开跑路径会紧接着自己做一次完整就绪检查，避免重复请求。
   */
  skipRunRiskRefresh?: boolean;
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
    const raw = data.graphJson ? JSON.parse(data.graphJson) : null
    if (raw && typeof raw === 'object' && !Array.isArray(raw)) {
      raw.edges = recoverLoginFlowEdgesIfMissing(raw.nodes, raw.edges);
    }
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

  /**
   * 序列化当前画布并提交保存。
   * - 有未确认 Staging 时默认弹窗：去确认 / 仅保存已确认 / 取消
   * - 序列化时排除未确认的 Staging 对象
   * - 仅落库地板失败才阻断；其余问题可落盘，并提示尚不可运行
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
      edges: store.getEffectiveEdges(),
      viewport: store.viewport,
      runConfig: store.runConfig,
      flowOutputs: store.flowOutputs,
      stagingFilter: stagingStore.buildPersistFilter(),
    });
    const floor = validateGraphJson(graph, { persistMinimalOnly: true });
    if (!floor.ok) {
      if (!options?.quiet) {
        ElMessage.error(floor.errors[0] ?? '图校验失败');
      }
      return false;
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

      if (!options?.skipRunRiskRefresh && !options?.quiet) {
        // 刷新运行风险条：只把鉴权/必填写入 store；结构/断言仍由实时结构校验展示
        const { errors, precheckErrors } = await collectRunBlockingErrors({
          graph,
          testProjectId: store.testProjectId,
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
      if (!options?.quiet) {
        ElMessage.error(e instanceof Error ? e.message : '保存失败');
      }
      return false;
    } finally {
      store.loading = false;
    }
  }

  /**
   * 导入 graph_json 覆盖当前画布。
   * 导入走完整结构校验；失败不改 store。
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
