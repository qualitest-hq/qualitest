/**
 * 运行场景配置：管理 graph_json.meta.scenarios 中的场景列表与当前激活场景。
 * 提供场景增删改查、项目环境下拉、flowSeed 键值行转换。
 * 打开画布拉环境后，会给未绑定环境的激活场景自动补上列表第一条，并同步「已保存」基线。
 */
import { computed, ref } from 'vue';
import { ElMessage } from 'element-plus';

import { listTestProjectEnv } from '@/api/project/testProjectEnv';
import type { GraphFlowOutput, GraphRunScenario } from '@/utils/flow/graphTypes';
import { nextSnowflakeId } from '@/utils/flow/snowflakeId';

import { useFlowCanvasStore } from '../stores/flowCanvasStore';
import { refreshSavedBaselineIfPristine } from '../utils/reconcileFlowDirty';

/** 项目环境条目，供场景配置下拉使用 */
export interface ProjectEnvOption {
  testProjectEnvId: string;
  envName: string;
  envUrl?: string;
  allowDestructiveReset?: number;
}

/** flowSeed 编辑行 */
export interface FlowSeedRow {
  key: string;
  value: string;
}

/** 将 flowSeed 对象转为键值行数组，用于表单展示 */
export function flowSeedToRows(obj: Record<string, unknown> | null | undefined): FlowSeedRow[] {
  if (!obj || typeof obj !== 'object') return [{ key: '', value: '' }];
  const entries = Object.entries(obj);
  if (!entries.length) return [{ key: '', value: '' }];
  return entries.map(([key, value]) => ({
    key,
    value: value == null ? '' : String(value),
  }));
}

/** 将键值行数组转为 flowSeed 对象，忽略空键行 */
export function rowsToFlowSeed(rows: FlowSeedRow[]): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  rows.forEach((row) => {
    const key = String(row.key ?? '').trim();
    if (!key) return;
    out[key] = row.value ?? '';
  });
  return out;
}

/** flowOutputs 编辑行 */
export interface FlowOutputRow {
  name: string;
  description: string;
}

/** 将 flowOutputs 数组转为编辑行 */
export function flowOutputsToRows(outputs: GraphFlowOutput[] | null | undefined): FlowOutputRow[] {
  if (!Array.isArray(outputs) || !outputs.length) {
    return [{ name: '', description: '' }];
  }
  return outputs.map((item) => ({
    name: String(item?.name ?? ''),
    description: String(item?.description ?? ''),
  }));
}

/** 将编辑行转为 flowOutputs 数组，忽略空 name 行 */
export function rowsToFlowOutputs(rows: FlowOutputRow[]): GraphFlowOutput[] {
  return rows
    .filter((row) => String(row.name ?? '').trim())
    .map((row) => {
      const item: GraphFlowOutput = { name: String(row.name).trim() };
      const desc = String(row.description ?? '').trim();
      if (desc) item.description = desc;
      return item;
    });
}

/** 项目环境列表（模块级共享，避免多处 useRunConfig() 各自缓存导致「未知环境」） */
const envOptions = ref<ProjectEnvOption[]>([]);
const envLoading = ref(false);
let loadedProjectId = '';

/** 切换测试流/项目时清空环境缓存 */
export function resetProjectEnvs() {
  envOptions.value = [];
  loadedProjectId = '';
}

export function useRunConfig() {
  const store = useFlowCanvasStore();

  /** 按 testProjectEnvId 解析环境显示名（不回落展示原始 ID） */
  function resolveEnvName(testProjectEnvId: string | undefined): string {
    if (!testProjectEnvId) return '未选环境';
    const id = String(testProjectEnvId).trim();
    const hit = envOptions.value.find((e) => e.testProjectEnvId === id);
    return hit?.envName ?? '未知环境';
  }

  /** 场景卡片副标题：环境名 + 备注，不展示任何 ID */
  function scenarioCardMeta(sc: GraphRunScenario): string {
    const parts: string[] = [];
    const envLabel = resolveEnvName(sc.testProjectEnvId);
    if (envLabel !== '未选环境' && envLabel !== '未知环境') {
      parts.push(envLabel);
    }
    const remark = String(sc.remark ?? '').trim();
    if (remark) {
      parts.push(remark);
    } else if (sc.id === store.runConfig.activeScenarioId) {
      parts.push('当前默认运行场景');
    }
    return parts.join(' · ');
  }

  /**
   * 拉取当前项目的运行环境列表，写入 envOptions。
   * 请求成功后给未绑环境的激活场景自动补绑列表第一条；若发生补绑，会把当前画布记为已保存基线。
   * 同一项目已有缓存且未 force 时跳过请求，仍会再跑一遍自动补绑。
   *
   * @param force true 时忽略本地缓存，强制重新请求
   */
  async function loadProjectEnvs(force = false) {
    const projectId = store.testProjectId;
    if (!projectId) {
      resetProjectEnvs();
      return;
    }
    // 同项目已有环境缓存：不重复请求，仍尝试补绑未设环境的场景
    if (!force && loadedProjectId === projectId && envOptions.value.length) {
      await ensureActiveScenarioEnv();
      return;
    }
    envLoading.value = true;
    try {
      const res = await listTestProjectEnv({ testProjectId: projectId, pageNum: 1, pageSize: 200 });
      const rows = res?.rows ?? res?.data ?? [];
      envOptions.value = (Array.isArray(rows) ? rows : []).map((row: Record<string, unknown>) => ({
        testProjectEnvId: String(row.testProjectEnvId ?? '').trim(),
        envName: String(row.envName ?? row.envKey ?? '未命名环境'),
        envUrl: String(row.envUrl ?? ''),
        allowDestructiveReset: Number(row.allowDestructiveReset ?? 0),
      }));
      loadedProjectId = projectId;
      // 列表就绪后再补绑，确保能取到第一条环境 id
      await ensureActiveScenarioEnv();
    } catch {
      envOptions.value = [];
      loadedProjectId = '';
    } finally {
      envLoading.value = false;
    }
  }

  /** 返回当前激活的运行场景 */
  function getActiveScenario(): GraphRunScenario | undefined {
    const { runConfig } = store;
    return runConfig.scenarios.find((s) => s.id === runConfig.activeScenarioId) ?? runConfig.scenarios[0];
  }

  /** 当前激活场景显示名，供运行按钮等 UI 共用 */
  const activeScenarioName = computed(
    () => getActiveScenario()?.name?.trim() || '未命名场景',
  );

  /** 切换激活场景并打开右栏场景配置 */
  function selectScenario(id: string) {
    store.runConfig.activeScenarioId = id;
    store.markDirty();
    store.showScenarioPanel();
  }

  /** 新增空白场景，继承当前场景的环境 id */
  function addScenario() {
    const active = getActiveScenario();
    const id = nextSnowflakeId();
    const scenario: GraphRunScenario = {
      id,
      name: `新场景 ${store.runConfig.scenarios.length + 1}`,
      testProjectEnvId: active?.testProjectEnvId ?? envOptions.value[0]?.testProjectEnvId ?? '',
      flowSeed: {},
      remark: '',
    };
    store.runConfig.scenarios.push(scenario);
    store.runConfig.activeScenarioId = id;
    store.markDirty();
    store.showScenarioPanel();
    ElMessage.success('已添加场景');
  }

  /** 复制当前场景，生成新 id 与「（副本）」后缀名称 */
  function duplicateScenario() {
    const src = getActiveScenario();
    if (!src) return;
    const id = nextSnowflakeId();
    const copy: GraphRunScenario = {
      ...JSON.parse(JSON.stringify(src)),
      id,
      name: `${src.name}（副本）`,
    };
    store.runConfig.scenarios.push(copy);
    store.runConfig.activeScenarioId = id;
    store.markDirty();
    store.showScenarioPanel();
    ElMessage.success('已复制场景');
  }

  /** 删除当前场景；至少保留一条 */
  function deleteScenario() {
    if (store.runConfig.scenarios.length <= 1) {
      ElMessage.warning('至少保留一个场景');
      return;
    }
    const id = store.runConfig.activeScenarioId;
    store.runConfig.scenarios = store.runConfig.scenarios.filter((s) => s.id !== id);
    store.runConfig.activeScenarioId = store.runConfig.scenarios[0]?.id ?? '';
    store.markDirty();
    store.showScenarioPanel();
    ElMessage.success('已删除场景');
  }

  /** 合并更新当前激活场景字段 */
  function patchActiveScenario(patch: Partial<GraphRunScenario>) {
    const sc = getActiveScenario();
    if (!sc) return;
    Object.assign(sc, patch);
    store.markDirty();
  }

  /**
   * 当前激活场景还没有 testProjectEnvId、且 envOptions 非空时，写入列表第一条的环境 id。
   * 自动补全不标记「未保存」。
   * 若本次确实写入了环境 id：在画布仍无用户改动时，把当前图快照记为已保存基线并清未保存标记。
   * 这样之后只拖节点或一键排版改坐标时，相对基线仍是仅布局变更，不会去长占画布写锁。
   */
  async function ensureActiveScenarioEnv() {
    const sc = getActiveScenario();
    // 无激活场景、已有环境、或列表为空：无需补绑
    if (!sc || sc.testProjectEnvId || !envOptions.value.length) return;
    sc.testProjectEnvId = envOptions.value[0].testProjectEnvId;
    // 补绑改了场景内容但不标脏；同步基线，避免后续仅改坐标被当成内容脏
    await refreshSavedBaselineIfPristine(store);
  }

  return {
    envOptions,
    envLoading,
    loadProjectEnvs,
    resolveEnvName,
    scenarioCardMeta,
    activeScenarioName,
    getActiveScenario,
    selectScenario,
    addScenario,
    duplicateScenario,
    deleteScenario,
    patchActiveScenario,
    ensureActiveScenarioEnv,
    flowSeedToRows,
    rowsToFlowSeed,
    flowOutputsToRows,
    rowsToFlowOutputs,
  };
}
