/**
 * 对比当前编辑态与上次加载/保存的基线，同步「未保存」状态。
 * <p>
 * 脏检查忽略 meta.viewport：仅平移/缩放画布不算内容变更，
 * 避免出现「未保存却无法撤销」的体验。
 * 真正改节点/边/场景后再保存时，仍会把当前视口写入 graph_json。
 */
import type { GraphJson } from '@/utils/flow/graphTypes';

import { toGraphJson } from '../graphAdapter';
import { graphJsonToSnapshotString } from './graphFingerprint';
import type { useFlowCanvasStore } from '../stores/flowCanvasStore';

type FlowCanvasStore = ReturnType<typeof useFlowCanvasStore>;

/**
 * 去掉 meta.viewport 后再做脏比较，使视口变化不影响「未保存」标记。
 */
function stripViewportForDirtyCompare(graph: GraphJson): GraphJson {
  if (!graph?.meta || graph.meta.viewport == null) {
    return graph;
  }
  const meta = { ...graph.meta };
  delete meta.viewport;
  return { ...graph, meta };
}

/** 将 graph 对象规范化为可比较的持久化快照字符串（含视口，供其它指纹场景用） */
export function snapshotStringFromGraph(graph: GraphJson | Record<string, unknown> | null | undefined): string {
  if (!graph) return '';
  return graphJsonToSnapshotString(graph);
}

/**
 * 序列化当前画布为脏检查用快照（忽略视口）。
 * 与保存路径相同的 toGraphJson，但比较前剥离 meta.viewport。
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
  return snapshotStringFromGraph(stripViewportForDirtyCompare(graph));
}

/**
 * 在加载/初始化完成后记录「已保存」基线。
 * 必须从灌入后的 store 生成，不能直接用服务端原始 JSON（字段规范化会有差异）。
 */
export async function refreshSavedBaseline(store: FlowCanvasStore) {
  const snap = await snapshotStringFromStore(store);
  store.setSavedGraphSnapshot(snap);
  store.markClean();
}

/**
 * 仅在当前无未保存修改时刷新基线（场景环境自动补全等初始化补全用）。
 */
export async function refreshSavedBaselineIfPristine(store: FlowCanvasStore) {
  if (store.dirty) return;
  await refreshSavedBaseline(store);
}

/**
 * 根据与已保存基线是否一致，更新 dirty 标记。
 * 撤销后若节点/边/场景回到加载或保存时的状态，则清除「未保存」（视口差异忽略）。
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
