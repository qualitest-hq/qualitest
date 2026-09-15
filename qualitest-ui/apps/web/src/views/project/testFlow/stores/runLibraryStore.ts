/**
 * 运行库 Pinia Store：管理正式 Run 列表、选中项与 Inspector 步骤索引。
 *
 * 数据源为 test_flow_run API；列表项默认不含 steps，选中或场景运行完成后懒加载/刷新详情。
 */
import { ElMessage } from 'element-plus';

import {
  delTestFlowRun,
  getTestFlowRun,
  listTestFlowRun,
  type RunPauseInfo,
} from '@/api/project/testFlowRun';

import { RUN_LIBRARY_MAX_RUNS } from '../constants/flowConfig';
import {
  mapRunDetail,
  mapRunListItem,
  type ApiTestFlowRunDetail,
} from '../utils/runRecordMapper';

/** 单步状态（与 test_flow_run_step.status 对应） */
export type RunStepStatus = 'passed' | 'failed' | 'skipped';
/** Run 终态（与 test_flow_run.status 对应） */
export type RunRecordStatus = 'passed' | 'failed' | 'running' | 'paused' | 'aborted' | 'cancelled';

export type { RunPauseInfo };

/** 单步结果，写入 RunRecord.steps 供时间线与 Inspector 展示 */
export interface RunStepDetail {
  nodeId: string;
  nodeType: string;
  nodeName: string;
  /** 进入该步所经的边 id */
  edgeId?: string;
  status: RunStepStatus;
  durationMs: number;
  /** 该步执行后的 flow 变量快照 */
  flowAfter: Record<string, unknown>;
  error?: { code: string; message: string };
  http?: {
    method?: string;
    url?: string;
    status?: number;
    callMode?: string;
    request?: Record<string, unknown>;
    response?: Record<string, unknown>;
    /** 业务码校验结果（HTTP 2xx 之后写入） */
    bizCheck?: {
      /** 业务码字段路径，如 code */
      codePath?: string;
      /** 响应中实际读到的业务码 */
      actualCode?: unknown;
      /** 视为成功的业务码列表 */
      successValues?: number[];
      /** 是否落在成功白名单内 */
      passed?: boolean;
      /** 响应中的错误消息文案 */
      message?: string;
    };
  };
  assert?: {
    rules: Array<Record<string, unknown> & { passed?: boolean }>;
  };
  extracts?: Array<{ name: string; scope: string; value: unknown }>;
  /** condition 节点命中分支 */
  branchTaken?: { branchId: string; kind: string };
  assigns?: Array<Record<string, unknown>>;
  /** script 节点执行详情 */
  script?: {
    language?: string;
    logs?: string[];
    writes?: Array<{ key?: string; value?: unknown; before?: unknown }>;
  };
  scenarioLoaded?: {
    scenarioId?: string;
    scenarioName?: string;
    testProjectEnvId?: string;
    envName?: string;
    flowSeed?: Record<string, unknown>;
    envSnapshot?: Record<string, unknown>;
  };
}

/** 一次正式 Run 的完整记录 */
export interface RunRecord {
  id: string;
  /** 与 id 相同，对应 testFlowRunId */
  testFlowRunId: string;
  source: 'server';
  status: RunRecordStatus;
  startedAt: string;
  finishedAt: string | null;
  durationMs: number;
  graphFingerprint: string;
  /** 触发时固化的 graph_json 字符串，回放高亮以其中 node_id 为准 */
  graphJsonSnapshot?: string;
  envLabel: string;
  flowSnapshot: Record<string, unknown>;
  steps: RunStepDetail[];
  scenarioId?: string;
  scenarioName?: string;
  triggerType?: string;
  errorCode?: string;
  errorMessage?: string;
  pauseInfo?: RunPauseInfo;
  pausedAt?: string;
}

/** 右栏运行 Inspector 当前 Tab */
export type InspectorTab = 'summary' | 'http' | 'flow';

/** 正式 Run 进行中的会话：控制轮询中止与底栏步骤文案 */
export interface ScenarioRunLiveSession {
  /** true 时停止后续轮询/高亮更新 */
  abort: boolean;
  /** 当前关注的 testFlowRunId；触发前可用 pending-* 占位 */
  recordId: string;
  /** 执行中固定为 running */
  phase?: 'running';
  /** 当前已高亮到的步骤下标（0-based） */
  stepIndex?: number;
  /** 详情里已返回的步骤总数 */
  stepTotal?: number;
}

export const useRunLibraryStore = defineStore('flowRunLibrary', () => {
  /** 当前测试流下的 Run 列表（最新在前） */
  const runs = ref<RunRecord[]>([]);
  /** 左栏/右栏当前选中的 testFlowRunId */
  const selectedRunId = ref<string | null>(null);
  const inspectorTab = ref<InspectorTab>('summary');
  /** 右栏 Inspector 当前查看的步骤索引 */
  const inspectorStepIndex = ref(0);
  /** 正式 Run 执行中会话：abort 为 true 时中止后续 UI 轮询更新 */
  const scenarioRunLive = ref<ScenarioRunLiveSession | null>(null);
  const listLoading = ref(false);
  const detailLoading = ref(false);
  /** 最近一次 loadRuns 对应的 testFlowId */
  const currentTestFlowId = ref('');

  /** 从服务端拉取当前测试流的 Run 列表 */
  async function loadRuns(testFlowId: string) {
    if (!testFlowId) return;
    currentTestFlowId.value = testFlowId;
    listLoading.value = true;
    try {
      const res = await listTestFlowRun({
        testFlowId,
        pageNum: 1,
        pageSize: RUN_LIBRARY_MAX_RUNS,
      });
      const rows = res?.rows ?? res?.data ?? [];
      runs.value = (Array.isArray(rows) ? rows : []).map(mapRunListItem);
      if (selectedRunId.value && !runs.value.some((r) => r.id === selectedRunId.value)) {
        selectedRunId.value = runs.value[0]?.id ?? null;
        inspectorStepIndex.value = 0;
      }
    } catch (e) {
      ElMessage.error((e as Error)?.message ?? '加载运行库失败');
    } finally {
      listLoading.value = false;
    }
  }

  /** 拉取单条 Run 详情并合并 steps */
  async function fetchRunDetail(runId: string): Promise<RunRecord | null> {
    detailLoading.value = true;
    try {
      const res = await getTestFlowRun(runId);
      const data = (res?.data ?? res) as ApiTestFlowRunDetail;
      const mapped = mapRunDetail(data);
      if (!mapped) return null;
      const idx = runs.value.findIndex((r) => r.id === runId);
      if (idx >= 0) {
        runs.value[idx] = mapped;
      } else {
        runs.value.unshift(mapped);
      }
      return mapped;
    } catch {
      // 业务错误已由 axios 拦截器提示，此处避免重复弹窗
      return null;
    } finally {
      detailLoading.value = false;
    }
  }

  /** 选中 Run；steps 为空时自动拉详情 */
  async function selectRun(id: string | null) {
    selectedRunId.value = id;
    inspectorStepIndex.value = 0;
    if (!id) return;
    const existing = runs.value.find((r) => r.id === id);
    if (existing && existing.steps.length === 0) {
      await fetchRunDetail(id);
    }
  }

  /** 运行详情 Inspector：切换到指定步骤索引 */
  function selectInspectorStep(index: number) {
    const run = runs.value.find((r) => r.id === selectedRunId.value);
    if (run && index >= 0 && index < run.steps.length) {
      inspectorStepIndex.value = index;
    }
  }

  /** 插入或更新列表头部的 Run 记录 */
  function upsertRun(record: RunRecord) {
    const idx = runs.value.findIndex((r) => r.id === record.id);
    if (idx >= 0) {
      runs.value[idx] = { ...record };
    } else {
      runs.value.unshift(record);
      while (runs.value.length > RUN_LIBRARY_MAX_RUNS) runs.value.pop();
    }
    selectedRunId.value = record.id;
  }

  /** 删除单条 Run（服务端逻辑删除） */
  async function deleteRun(id: string) {
    try {
      await delTestFlowRun(id);
      runs.value = runs.value.filter((r) => r.id !== id);
      if (selectedRunId.value === id) {
        selectedRunId.value = runs.value[0]?.id ?? null;
        inspectorStepIndex.value = 0;
      }
    } catch (e) {
      ElMessage.error((e as Error)?.message ?? '删除失败');
    }
  }

  /** 批量删除当前列表全部 Run */
  async function clearRuns() {
    if (!runs.value.length) return;
    const ids = runs.value.map((r) => r.id).join(',');
    try {
      await delTestFlowRun(ids);
      runs.value = [];
      selectedRunId.value = null;
      inspectorStepIndex.value = 0;
    } catch (e) {
      ElMessage.error((e as Error)?.message ?? '清空失败');
    }
  }

  const selectedRun = computed(() => runs.value.find((r) => r.id === selectedRunId.value) ?? null);

  return {
    runs,
    selectedRunId,
    inspectorTab,
    inspectorStepIndex,
    scenarioRunLive,
    listLoading,
    detailLoading,
    currentTestFlowId,
    selectedRun,
    loadRuns,
    fetchRunDetail,
    selectRun,
    selectInspectorStep,
    upsertRun,
    deleteRun,
    clearRuns,
  };
});
