/**
 * 运行场景配置：管理 scenario-entry 中的场景列表与当前激活场景。
 * 提供场景增删改查、项目环境下拉、inSeed 键值行转换。
 */
import { computed, ref } from 'vue';
import { ElMessage } from 'element-plus';

import { listTestProjectEnv } from '@/api/project/testProjectEnv';
import type { GraphRunScenario } from '@/utils/flow/graphTypes';
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

/** inSeed 编辑行 */
export interface InSeedRow {
  key: string;
  value: string;
}

/** 将 inSeed 对象转为键值行数组，用于表单展示 */
export function inSeedToRows(obj: Record<string, unknown> | null | undefined): InSeedRow[] {
  if (!obj || typeof obj !== 'object') return [{ key: '', value: '' }];
  const entries = Object.entries(obj);
  if (!entries.length) return [{ key: '', value: '' }];
  return entries.map(([key, value]) => ({
    key,
    value: value == null ? '' : String(value),
  }));
}

/** 将键值行数组转为 inSeed 对象，忽略空键行 */
export function rowsToInSeed(rows: InSeedRow[]): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  rows.forEach((row) => {
    const key = String(row.key ?? '').trim();
    if (!key) return;
    out[key] = row.value ?? '';
  });
  return out;
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

  function resolveEnvName(envId: string | undefined): string {
    if (!envId) return '未选环境';
    const id = String(envId).trim();
    const hit = envOptions.value.find((e) => e.testProjectEnvId === id);
    return hit?.envName ?? '未知环境';
  }

  function scenarioCardMeta(sc: GraphRunScenario): string {
    const parts: string[] = [];
    const envLabel = resolveEnvName(sc.envId);
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

  async function loadProjectEnvs(force = false) {
    const projectId = store.testProjectId;
    if (!projectId) {
      resetProjectEnvs();
      return;
    }
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
      await ensureActiveScenarioEnv();
    } catch {
      envOptions.value = [];
      loadedProjectId = '';
    } finally {
      envLoading.value = false;
    }
  }

  function getActiveScenario(): GraphRunScenario | undefined {
    const { runConfig } = store;
    return runConfig.scenarios.find((s) => s.id === runConfig.activeScenarioId) ?? runConfig.scenarios[0];
  }

  const activeScenarioName = computed(
    () => getActiveScenario()?.name?.trim() || '未命名场景',
  );

  function selectScenario(id: string) {
    store.runConfig.activeScenarioId = id;
    store.markDirty();
    store.showScenarioPanel();
  }

  function addScenario() {
    const active = getActiveScenario();
    const id = nextSnowflakeId();
    const envId = active?.envId ?? envOptions.value[0]?.testProjectEnvId ?? '';
    const scenario: GraphRunScenario = {
      id,
      name: `新场景 ${store.runConfig.scenarios.length + 1}`,
      envId,
      inSeed: {},
      remark: '',
    };
    store.runConfig.scenarios.push(scenario);
    store.runConfig.activeScenarioId = id;
    store.markDirty();
    store.showScenarioPanel();
    ElMessage.success('已添加场景');
  }

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

  function patchActiveScenario(patch: Partial<GraphRunScenario>) {
    const sc = getActiveScenario();
    if (!sc) return;
    Object.assign(sc, patch);
    store.markDirty();
  }

  async function ensureActiveScenarioEnv() {
    const sc = getActiveScenario();
    if (!sc || sc.envId || !envOptions.value.length) return;
    sc.envId = envOptions.value[0].testProjectEnvId;
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
    inSeedToRows,
    rowsToInSeed,
  };
}