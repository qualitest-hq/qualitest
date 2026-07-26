/**
 * 测试流画布配置常量：布局尺寸、场景运行上限、参数库作用域、HTTP Method 徽章。
 */

/** 画布节点与 condition 分支行的布局尺寸（像素） */
export const COND_NODE_W = 340;
export const NODE_W = 300;
export const NODE_MIN_H = 108;
export const NODE_HEAD_H = 52;
export const COND_ROW_H = 40;

/** VueFlow 实例 id，供画布外组件通过 useVueFlow(id) 访问 */
export const FLOW_VUE_FLOW_ID = 'flow-canvas-main';

/** 路径模拟与场景运行的步数、间隔上限 */
export const SIMULATE_MAX_PATH_STEPS = 50;
export const SIMULATE_MAX_LOOP_EDGE_USES = 1;
export const SIMULATE_STEP_MS = 650;
export const SIMULATE_PATH_GAP_MS = 500;
export const RUN_EXEC_MAX_STEPS = 50;
export const SCENARIO_RUN_STEP_MS = 480;
/** 运行库回放自动步进间隔（毫秒） */
export const RUN_REPLAY_STEP_MS = 550;

/** AI 确认成功后节点紫色高亮保持时长（毫秒） */
export const AI_CONFIRM_HIGHLIGHT_CLEAR_MS = 4000;

/** Staging 确认后自动导航时的缩放倍率上限（100%） */
export const STAGING_FOCUS_ZOOM = 1;

/** AI 侧栏覆盖画布的最大像素宽度 */
export const AI_DOCK_WIDTH_MAX_PX = 480;
/** AI 侧栏覆盖画布宽度占视口宽度的比例 */
export const AI_DOCK_WIDTH_VW = 0.38;

/** 运行库 sessionStorage 键名与最大保留条数 */
export const RUN_LIBRARY_STORAGE_KEY = 'qualitest-flow-run-library-v1';
export const RUN_LIBRARY_MAX_RUNS = 30;

/** 画布撤销历史栈最大步数 */
export const HISTORY_MAX_STEPS = 30;

/** script 节点默认超时、上限与源码大小（字节） */
export const SCRIPT_DEFAULT_TIMEOUT_MS = 5000;
export const SCRIPT_MAX_TIMEOUT_MS = 30000;
export const SCRIPT_MAX_SOURCE_BYTES = 32768;

/** 参数库 flow/env/asset 作用域显示文案 */
export const SCOPE_LABELS: Record<string, string> = {
  flow: 'flow',
  env: 'env',
  asset: 'asset',
};

export const SCOPES = ['flow', 'env', 'asset'] as const;
export type ParamScope = (typeof SCOPES)[number];

import { getFlowHttpMethodBadgeClass } from '@/views/project/testProject/utils/httpMethodMeta';

/** 流程画布 HTTP 方法徽章 CSS 类（委托 httpMethodMeta） */
export function getMethodBadgeClass(method: string | null | undefined): string {
  return getFlowHttpMethodBadgeClass(method);
}
