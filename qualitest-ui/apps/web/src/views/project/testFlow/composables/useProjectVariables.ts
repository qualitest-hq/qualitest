import { listTestProjectAsset } from '@/api/project/testProjectAsset';
import { listTestProjectEnv } from '@/api/project/testProjectEnv';

/** 安全解析 JSON 对象字段 */
export function parseJsonSafe(raw: unknown): Record<string, unknown> {
  if (raw == null) return {};
  if (typeof raw === 'object' && !Array.isArray(raw)) return raw as Record<string, unknown>;
  if (typeof raw === 'string') {
    try {
      const p = JSON.parse(raw);
      return typeof p === 'object' && p && !Array.isArray(p) ? (p as Record<string, unknown>) : {};
    } catch {
      return {};
    }
  }
  return {};
}

/** 从 envVariables 数组结构解析变量名列表 */
export function parseEnvVarKeys(envVariables: unknown): string[] {
  if (!envVariables) return [];
  let arr: unknown[] = [];
  if (typeof envVariables === 'string') {
    try {
      const parsed = JSON.parse(envVariables);
      arr = Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  } else if (Array.isArray(envVariables)) {
    arr = envVariables;
  }
  const keys: string[] = [];
  for (const item of arr) {
    if (item && typeof item === 'object') {
      const name = (item as Record<string, unknown>).name ?? (item as Record<string, unknown>).key;
      if (name != null && String(name).trim()) keys.push(String(name).trim());
    }
  }
  return keys;
}

/** 从 assetVariables 对象或数组解析变量名列表 */
export function parseAssetVarKeys(assetVariables: unknown): string[] {
  const obj = typeof assetVariables === 'string'
    ? (() => {
        try {
          return JSON.parse(assetVariables) as Record<string, unknown>;
        } catch {
          return {};
        }
      })()
    : (assetVariables as Record<string, unknown>) ?? {};
  if (Array.isArray(obj)) {
    return obj
      .map((item) => {
        if (item && typeof item === 'object') {
          const name = (item as Record<string, unknown>).name ?? (item as Record<string, unknown>).key;
          return name != null ? String(name).trim() : '';
        }
        return '';
      })
      .filter(Boolean);
  }
  return Object.keys(obj ?? {});
}

/** 拉取项目环境列表原始行 */
export async function fetchProjectEnvRows(projectId: string): Promise<Record<string, unknown>[]> {
  const res = await listTestProjectEnv({ testProjectId: projectId, pageNum: 1, pageSize: 200 });
  const rows = res?.rows ?? res?.data ?? [];
  return Array.isArray(rows) ? rows : [];
}

/** 拉取项目素材列表原始行 */
export async function fetchProjectAssetRows(projectId: string): Promise<Record<string, unknown>[]> {
  const res = await listTestProjectAsset({ testProjectId: projectId, pageNum: 1, pageSize: 200 });
  const rows = res?.rows ?? res?.data ?? [];
  return Array.isArray(rows) ? rows : [];
}
