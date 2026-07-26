/**
 * 从项目环境、素材条目构建 FlowRunContext。
 * 用于测试流场景运行、Mock Run 启动时注入 env / asset / flow 初值。
 */
import type { FlowRunContext } from './types';
import { extractEntryInner } from '@/views/project/testProject/utils/variableEntryUtils';
import { parseVariableEntries } from '@/views/project/testProject/utils/variableEntryUtils';
import { resolveEnvBaseUrlForRequest } from '@/views/project/testProject/utils/envConfigUtils';

/** 环境变量或素材库条目（key + assets 业务值） */
export interface VariableEntryLike {
  key?: string;
  assets?: Record<string, unknown>;
}

export interface BuildDebugFlowContextOptions {
  /** 环境 envUrl（模块 URL 或纯字符串） */
  envUrl?: string | null;
  /** 环境变量 JSON 数组字符串 */
  envVariables?: string | null;
  /** 项目素材列表 */
  assetEntries?: VariableEntryLike[];
  /** 已有 flow 变量（如从 session 恢复或场景 flowSeed） */
  flow?: Record<string, unknown>;
}

/** 将变量条目列表转为 key → 业务值 的 Map，供 {{env.*}} / {{asset.*}} 取值 */
function entriesToScopeMap(entries: VariableEntryLike[]): Record<string, unknown> {
  const map: Record<string, unknown> = {};
  for (const entry of entries) {
    const key = (entry.key ?? '').trim();
    if (!key) continue;
    const inner = extractEntryInner(entry);
    if (inner !== undefined) map[key] = inner;
  }
  return map;
}

/**
 * 组装单次 Run 用的运行时上下文。
 * - env：环境变量条目 + baseUrl
 * - asset：素材库条目
 * - flow：调用方传入或空对象
 * - lastResponse：初始为 null，http 步骤执行后由执行器更新
 */
export function buildDebugFlowContext(options: BuildDebugFlowContextOptions = {}): FlowRunContext {
  const envEntries = parseVariableEntries(options.envVariables);
  const env = entriesToScopeMap(envEntries);

  const baseUrl = resolveEnvBaseUrlForRequest(options.envUrl);
  if (baseUrl) env.baseUrl = baseUrl;

  const asset = entriesToScopeMap(options.assetEntries ?? []);

  return {
    env,
    flow: { ...(options.flow ?? {}) },
    asset,
    lastResponse: null,
  };
}
