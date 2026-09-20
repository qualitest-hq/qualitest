/**
 * 画布「未保存」脏状态：用可比较快照对照上次加载/保存基线。
 * <p>
 * 比较时去掉视口：仅平移、缩放画布不算内容变更。
 * 真正改节点、边、场景后再保存时，当前视口仍会写入 graph_json。
 */
import type { GraphJson } from '@/utils/flow/graphTypes';

import { toGraphJson } from '../graphAdapter';
import { graphJsonToSnapshotString } from './graphFingerprint';
import type { useFlowCanvasStore } from '../stores/flowCanvasStore';

/** 画布 Pinia store 类型 */
type FlowCanvasStore = ReturnType<typeof useFlowCanvasStore>;

/**
 * 去掉 meta.viewport 后返回新图对象；无视口则原样返回。
 * 用于内容比较时忽略平移与缩放。
 *
 * @param graph 流程图对象
 * @returns 不含 viewport 的图（或原对象）
 */
export function stripViewportForCompare(graph: GraphJson): GraphJson {
  if (!graph?.meta || graph.meta.viewport == null) {
    return graph;
  }
  const meta = { ...graph.meta };
  delete meta.viewport;
  return { ...graph, meta };
}

/**
 * 将图对象规范序列化为可比较的 JSON 字符串（保留视口）。
 *
 * @param graph 流程图或类图对象；空则返回空串
 */
export function snapshotStringFromGraph(graph: GraphJson | Record<string, unknown> | null | undefined): string {
  if (!graph) return '';
  return graphJsonToSnapshotString(graph);
}

/**
 * 将图对象规范序列化为可比较的 JSON 字符串，且不含视口。
 * 平移、缩放不计入内容变更。
 *
 * @param graph 流程图或类图对象；空则返回空串
 */
export function snapshotStringWithoutViewport(graph: GraphJson | Record<string, unknown> | null | undefined): string {
  if (!graph) return '';
  return snapshotStringFromGraph(stripViewportForCompare(graph as GraphJson));
}

/**
 * 从画布 store 生成脏检查用快照字符串（不含视口）。
 * 先确保边已灌入，再序列化节点、边、运行配置与流输出。
 *
 * @param store 画布 store
 */
export async function snapshotStringFromStore(store: FlowCanvasStore): Promise<string> {
  await store.ensureEdgesHydrated();
  const graph = toGraphJson({
    nodes: store.nodes,
    edges: store.edges,
    viewport: store.viewport,
    runConfig: store.runConfig,
    flowOutputs: store.flowOutputs,
  });
  return snapshotStringWithoutViewport(graph);
}

/**
 * 把当前画布快照记为「已保存」基线，并清除未保存标记。
 * 快照由当前 store 现场序列化得到。
 *
 * @param store 画布 store
 */
export async function refreshSavedBaseline(store: FlowCanvasStore) {
  const snap = await snapshotStringFromStore(store);
  store.setSavedGraphSnapshot(snap);
  store.markClean();
}

/**
 * 仅在当前无未保存修改时刷新「已保存」基线。
 * 用于初始化阶段自动补全场景环境等、且用户尚未改图的情况。
 *
 * @param store 画布 store
 */
export async function refreshSavedBaselineIfPristine(store: FlowCanvasStore) {
  if (store.dirty) return;
  await refreshSavedBaseline(store);
}

/**
 * 对照已保存基线更新 dirty。
 * 当前快照与基线相同则标为已保存；不同则标为未保存。无基线时不改。
 *
 * @param store 画布 store
 */
export async function reconcileFlowDirtyState(store: FlowCanvasStore) {
  const baseline = store.savedGraphSnapshot;
  if (!baseline) return;

  const current = await snapshotStringFromStore(store);
  if (current === baseline) {
    store.markClean();
  } else {
    store.markDirty();
  }
}
