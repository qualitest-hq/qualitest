/**
 * 左栏参数库：聚合项目 env/asset 静态变量与图中 HTTP extracts 动态 flow 变量。
 * 支持按 scope 过滤、关键字搜索、复制 {{placeholder}}。
 */
import type { Node } from '@vue-flow/core';
import { ref } from 'vue';

import { ElMessage } from 'element-plus';

import { useFlowCanvasStore } from '../stores/flowCanvasStore';
import {
  fetchProjectAssetRows,
  fetchProjectEnvRows,
  parseJsonSafe,
} from './useProjectVariables';
import { formatExtractTargetDest } from '../utils/nodeDataUtils';

export interface ParamLibraryItem {
  scope: string;
  path: string;
  placeholder: string;
  remark: string;
  sample?: string;
  dynamic?: boolean;
}

function flattenNestedFields(
  scope: string,
  entryKey: string,
  obj: Record<string, unknown>,
  prefix: string,
  entryRemark: string,
): ParamLibraryItem[] {
  const items: ParamLibraryItem[] = [];
  Object.entries(obj || {}).forEach(([k, v]) => {
    const sub = prefix ? `${prefix}.${k}` : k;
    if (v && typeof v === 'object' && !Array.isArray(v)) {
      items.push(...flattenNestedFields(scope, entryKey, v as Record<string, unknown>, sub, entryRemark));
    } else {
      const path = scope === 'env' ? `env.${sub}` : `asset.${entryKey}.${sub}`;
      items.push({
        scope,
        path,
        placeholder: `{{${path}}}`,
        remark: entryRemark || '',
        sample: v == null ? '' : String(v),
      });
    }
  });
  return items;
}

export function useParamLibrary() {
  const store = useFlowCanvasStore();
  const scopeFilter = ref<'all' | 'flow' | 'env' | 'asset'>('all');
  const keyword = ref('');
  const envEntries = ref<Array<{ key: string; remark?: string; value?: unknown }>>([]);
  const assetEntries = ref<Array<{ key: string; remark?: string; assets?: Record<string, unknown> }>>([]);

  /** 拉取当前 testProjectId 下的环境与素材条目 */
  async function loadProjectVariables() {
    const projectId = store.testProjectId;
    if (!projectId) return;
    try {
      const [envRows, assetRows] = await Promise.all([
        fetchProjectEnvRows(projectId),
        fetchProjectAssetRows(projectId),
      ]);
      envEntries.value = envRows.map((row) => {
        const vars = parseJsonSafe(row.envVariables);
        const firstKey = Object.keys(vars)[0];
        return {
          key: String(row.envName ?? row.key ?? firstKey ?? ''),
          remark: String(row.remark ?? row.envName ?? ''),
          value: firstKey ? vars[firstKey] : undefined,
        };
      });
      assetEntries.value = assetRows.map((row) => ({
        key: String(row.assetKey ?? row.key ?? ''),
        remark: String(row.remark ?? row.assetName ?? ''),
        assets: parseJsonSafe(row.assetVariables ?? row.assets),
      }));
    } catch {
      envEntries.value = [];
      assetEntries.value = [];
    }
  }

  /** 扫描图中 http extracts 与 assign 写入项，生成 flow/asset 动态占位符条目 */
  function collectDynamicParamsFromGraph(nodes: Node[]): ParamLibraryItem[] {
    const items: ParamLibraryItem[] = [];
    const seen = new Set<string>();
    nodes.forEach((node) => {
      if (node.type === 'http') {
        const data = node.data as Record<string, unknown>;
        ((data.extracts as Array<Record<string, unknown>>) || []).forEach((t) => {
          const path = formatExtractTargetDest(t);
          if (!path || path.includes('?') || seen.has(path)) return;
          seen.add(path);
          items.push({
            scope: String(t.scope || 'flow'),
            path,
            placeholder: `{{${path}}}`,
            remark: `HTTP 提取 · ${data.name || 'HTTP 节点'}`,
            dynamic: true,
          });
        });
      }
      if (node.type === 'assign') {
        const data = node.data as Record<string, unknown>;
        ((data.assignments as Array<Record<string, unknown>>) || []).forEach((a) => {
          const name = String(a.name || '').trim();
          if (!name) return;
          const path = `flow.${name}`;
          if (seen.has(path)) return;
          seen.add(path);
          items.push({
            scope: 'flow',
            path,
            placeholder: `{{${path}}}`,
            remark: `Assign · ${data.name || '赋值节点'}`,
            dynamic: true,
          });
        });
      }
    });
    return items;
  }

  function buildEnvParamItems(): ParamLibraryItem[] {
    return envEntries.value.flatMap((entry) => {
      if (!entry.key) return [];
      return [{
        scope: 'env',
        path: `env.${entry.key}`,
        placeholder: `{{env.${entry.key}}}`,
        remark: entry.remark || entry.key,
        sample: entry.value == null ? '' : String(entry.value),
      }];
    });
  }

  function buildAssetParamItems(): ParamLibraryItem[] {
    return assetEntries.value.flatMap((entry) => {
      if (!entry.key) return [];
      const data = entry.assets;
      if (!data || !Object.keys(data).length) {
        return [{
          scope: 'asset',
          path: `asset.${entry.key}`,
          placeholder: `{{asset.${entry.key}}}`,
          remark: entry.remark || entry.key,
          sample: '',
        }];
      }
      return flattenNestedFields('asset', entry.key, data, '', entry.remark || entry.key);
    });
  }

  function getAllItems(): ParamLibraryItem[] {
    const dynamic = collectDynamicParamsFromGraph(store.nodes);
    const dynamicPaths = new Set(dynamic.map((i) => i.path));
    const staticItems = [...buildEnvParamItems(), ...buildAssetParamItems()].filter(
      (i) => !dynamicPaths.has(i.path),
    );
    return [...dynamic, ...staticItems];
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

  async function copyParamText(text: string, label: string) {
    try {
      await navigator.clipboard.writeText(text);
      ElMessage.success(`已复制 ${label}`);
    } catch {
      const ta = document.createElement('textarea');
      ta.value = text;
      ta.style.position = 'fixed';
      ta.style.opacity = '0';
      document.body.appendChild(ta);
      ta.select();
      document.execCommand('copy');
      ta.remove();
      ElMessage.success(`已复制 ${label}`);
    }
  }

  return {
    scopeFilter,
    keyword,
    loadProjectVariables,
    filteredItems,
    copyParamText,
    getAllItems,
  };
}
