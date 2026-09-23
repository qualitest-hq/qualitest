/**
 * 左栏参数库：只聚合当前场景 env 与项目 asset 的配置态叶子。
 * 模板画布读 templateParamContext，不请求项目变量接口。
 * 支持按 scope 过滤、关键字搜索、复制 {{placeholder}} / 样例值。
 */
import { ref } from 'vue';

import { ElMessage } from 'element-plus';

import { getTestProjectEnv } from '@/api/project/testProjectEnv';
import { copyTextSync } from '@/utils/clipboard';
import { extractEntryInner } from '../../testProject/utils/variableEntryUtils';
import {
  normalizeEnvVariableEntries,
  templateAssetParamEntries,
} from '../../testProjectTemplate/utils/templateParamUtils';
import { useFlowCanvasStore } from '../stores/flowCanvasStore';
import {
  fetchProjectAssetRows,
  parseJsonSafe,
} from './useProjectVariables';

export interface ParamLibraryItem {
  scope: string;
  path: string;
  placeholder: string;
  remark: string;
  sample?: string;
}

type VariableEntry = {
  key: string;
  remark?: string;
  assets?: Record<string, unknown>;
  value?: unknown;
};

function getActiveScenario(store: ReturnType<typeof useFlowCanvasStore>) {
  const { runConfig } = store;
  return runConfig.scenarios?.find((s: { id?: string }) => s.id === runConfig.activeScenarioId)
    ?? runConfig.scenarios?.[0];
}

/** 叶子样例：非对象直接 String，对象/数组 JSON */
function formatLeafSample(value: unknown): string {
  if (value == null) return '';
  if (typeof value !== 'object') return String(value);
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
}

/**
 * 将任意配置值展平为叶子条目；中间 object 不建行。
 * @param pathPrefix 已含 scope 的路径前缀，如 env.baseUrl / asset.adminAuth
 */
function flattenValueLeaves(
  scope: string,
  pathPrefix: string,
  value: unknown,
  remark: string,
): ParamLibraryItem[] {
  if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
    const obj = value as Record<string, unknown>;
    const keys = Object.keys(obj);
    if (!keys.length) return [];
    return keys.flatMap((k) => {
      const next = pathPrefix ? `${pathPrefix}.${k}` : k;
      return flattenValueLeaves(scope, next, obj[k], remark);
    });
  }
  if (!pathPrefix) return [];
  return [{
    scope,
    path: pathPrefix,
    placeholder: `{{${pathPrefix}}}`,
    remark: remark || '',
    sample: formatLeafSample(value),
  }];
}

/** 变量条目：去包装后按叶子输出；空内层不输出 */
function flattenEntryLeaves(
  scope: 'env' | 'asset',
  entry: VariableEntry,
): ParamLibraryItem[] {
  const key = String(entry.key || '').trim();
  if (!key) return [];
  const remark = entry.remark || key;
  const inner = entry.assets != null
    ? extractEntryInner({ key, assets: entry.assets })
    : entry.value;
  if (inner === undefined) return [];
  return flattenValueLeaves(scope, `${scope}.${key}`, inner, remark);
}

function toAssetEntry(row: Record<string, unknown>): VariableEntry {
  const key = String(row.key ?? row.assetKey ?? '').trim();
  const rawAssets = row.assets ?? row.assetVariables;
  const assets = rawAssets && typeof rawAssets === 'object' && !Array.isArray(rawAssets)
    ? (rawAssets as Record<string, unknown>)
    : parseJsonSafe(rawAssets);
  return {
    key,
    remark: String(row.remark ?? row.assetName ?? key),
    assets,
  };
}

/** 解析接口响应中的环境详情行 */
function unwrapEnvRow(res: unknown): Record<string, unknown> {
  const row = (res as { data?: Record<string, unknown> })?.data
    ?? (res as Record<string, unknown>);
  return row && typeof row === 'object' ? row : {};
}

export function useParamLibrary() {
  const store = useFlowCanvasStore();
  const scopeFilter = ref<'all' | 'env' | 'asset'>('all');
  const keyword = ref('');
  const envEntries = ref<VariableEntry[]>([]);
  const assetEntries = ref<VariableEntry[]>([]);
  /** 当前场景环境 URL，有则合成 env.baseUrl 叶子 */
  const envBaseUrl = ref('');

  function clearEntries() {
    envEntries.value = [];
    assetEntries.value = [];
    envBaseUrl.value = '';
  }

  /** 拉取当前场景 env 与项目 asset；模板模式用水合上下文 */
  async function loadProjectVariables() {
    if (store.canvasMode === 'template') {
      hydrateFromTemplateContext();
      return;
    }
    const projectId = store.testProjectId;
    if (!projectId) {
      clearEntries();
      return;
    }
    try {
      const envId = getActiveScenario(store)?.testProjectEnvId;
      const [envRow, assetRows] = await Promise.all([
        envId
          ? getTestProjectEnv(envId).then(unwrapEnvRow).catch(() => ({} as Record<string, unknown>))
          : Promise.resolve({} as Record<string, unknown>),
        fetchProjectAssetRows(projectId),
      ]);

      envEntries.value = normalizeEnvVariableEntries(envRow.envVariables).map(
        (e: { key: string; remark?: string; assets?: Record<string, unknown> }) => ({
          key: e.key,
          remark: e.remark || e.key,
          assets: e.assets && typeof e.assets === 'object' ? e.assets : {},
        }),
      );
      envBaseUrl.value = String(envRow.envUrl ?? '').trim();
      assetEntries.value = assetRows.map(toAssetEntry);
    } catch {
      clearEntries();
    }
  }

  /** 模板画布：用 templateParamContext 填充 env/asset 预览 */
  function hydrateFromTemplateContext() {
    const ctx = store.templateParamContext;
    if (!ctx) {
      clearEntries();
      return;
    }
    // 模板预览已含合成的 baseUrl 条目
    envBaseUrl.value = '';
    envEntries.value = (ctx.env || []).map((row: { name: string; remark?: string; value?: unknown }) => ({
      key: row.name,
      remark: row.remark || row.name,
      value: row.value,
    }));
    assetEntries.value = templateAssetParamEntries(ctx.asset || []) as VariableEntry[];
  }

  function buildEnvParamItems(): ParamLibraryItem[] {
    const items = envEntries.value.flatMap((entry) => flattenEntryLeaves('env', entry));
    if (envBaseUrl.value && !items.some((i) => i.path === 'env.baseUrl')) {
      items.unshift({
        scope: 'env',
        path: 'env.baseUrl',
        placeholder: '{{env.baseUrl}}',
        remark: '环境前置 URL',
        sample: envBaseUrl.value,
      });
    }
    return items;
  }

  function getAllItems(): ParamLibraryItem[] {
    const seen = new Set<string>();
    const out: ParamLibraryItem[] = [];
    for (const item of [
      ...buildEnvParamItems(),
      ...assetEntries.value.flatMap((entry) => flattenEntryLeaves('asset', entry)),
    ]) {
      if (seen.has(item.path)) continue;
      seen.add(item.path);
      out.push(item);
    }
    return out;
  }

  function filteredItems(): ParamLibraryItem[] {
    const kw = keyword.value.trim().toLowerCase();
    const scope = scopeFilter.value;
    return getAllItems().filter((item) => {
      if (scope !== 'all' && item.scope !== scope) return false;
      if (!kw) return true;
      const hay = `${item.path} ${item.placeholder} ${item.remark} ${item.sample || ''}`.toLowerCase();
      return hay.includes(kw);
    });
  }

  function copyParamText(text: string, label: string) {
    if (copyTextSync(text)) {
      ElMessage.success(`已复制 ${label}`);
      return;
    }
    ElMessage.error('复制失败');
  }

  return {
    scopeFilter,
    keyword,
    loadProjectVariables,
    filteredItems,
    copyParamText,
  };
}
