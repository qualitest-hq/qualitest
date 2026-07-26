/**
 * 运行场景配置：管理 graph_json.meta.run 中的场景列表与当前激活场景。
 * 提供场景增删改查、项目环境下拉数据、flowSeed 键值行转换。
 */
import { computed, ref } from 'vue';
import { ElMessage } from 'element-plus';

import { listTestProjectEnv } from '@/api/project/testProjectEnv';
import type { GraphFlowOutput, GraphRunScenario } from '@/utils/flow/graphTypes';
import { nextSnowflakeId } from '@/utils/flow/snowflakeId';

import { useFlowCanvasStore } from '../stores/flowCanvasStore';

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

  /** 拉取当前项目环境列表，写入 envOptions */
  async function loadProjectEnvs(force = false) {
    const projectId = store.testProjectId;
    if (!projectId) {
      resetProjectEnvs();
      return;
    }
    if (!force && loadedProjectId === projectId && envOptions.value.length) {
      ensureActiveScenarioEnv();
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
      ensureActiveScenarioEnv();
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
   * 若当前场景未绑定环境且项目环境列表非空，自动选中第一条。
   * 在场景面板挂载或环境列表加载后调用。
   */
  /** 场景未绑定环境时自动选第一条，属于加载期补全，不触发未保存状态 */
  function ensureActiveScenarioEnv() {
    const sc = getActiveScenario();
    if (!sc || sc.testProjectEnvId || !envOptions.value.length) return;
    sc.testProjectEnvId = envOptions.value[0].testProjectEnvId;
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
