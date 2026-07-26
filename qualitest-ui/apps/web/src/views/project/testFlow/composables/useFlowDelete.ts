/**
 * 画布选中项删除：支持删除当前选中的节点或边。
 * 删除节点时一并移除关联边；condition 节点的分支 target 同步清空。
 */
import { useFlowConnect } from './useFlowConnect';
import { useFlowHistory } from './useFlowHistory';
import { useFlowCanvasStore } from '../stores/flowCanvasStore';

export function useFlowDelete() {
  const store = useFlowCanvasStore();
  const { clearBranchTarget } = useFlowConnect();
  const { pushHistory } = useFlowHistory();

  /** 按 id 删除节点及其所有入边/出边 */
  function deleteNodeById(nodeId: string) {
    const node = store.nodes.find((n) => n.id === nodeId);
    if (!node) return;

    const relatedEdges = store.edges.filter((e) => e.source === nodeId || e.target === nodeId);
    relatedEdges.forEach((e) => {
      if (e.source === nodeId) {
        clearBranchTarget(nodeId, e.target);
      }
    });

    store.nodes = store.nodes.filter((n) => n.id !== nodeId);
    store.edges = store.edges.filter((e) => e.source !== nodeId && e.target !== nodeId);

    if (store.selected?.kind === 'node' && store.selected.id === nodeId) {
      store.clearSelection();
    }
    store.markDirty();
    pushHistory();
  }

  /** 按 id 删除边，condition 源节点分支 target 同步清理 */
  function deleteEdgeById(edgeId: string) {
    const edge = store.edges.find((e) => e.id === edgeId);
    if (!edge) return;

    if (edge.source) {
      const srcNode = store.nodes.find((n) => n.id === edge.source);
      if (srcNode?.type === 'condition' && edge.target) {
        clearBranchTarget(edge.source, edge.target);
      }
    }

    store.edges = store.edges.filter((e) => e.id !== edgeId);
    if (store.selected?.kind === 'edge' && store.selected.id === edgeId) {
      store.clearSelection();
    }
    store.markDirty();
    pushHistory();
  }

  /** 删除 store.selected 指向的节点或边；无选中时静默返回 */
  function deleteSelection() {
    const sel = store.selected;
    if (!sel) return;

    if (sel.kind === 'node') {
      deleteNodeById(sel.id);
    } else {
      deleteEdgeById(sel.id);
    }
  }

  return {
    deleteSelection,
    deleteNodeById,
    deleteEdgeById,
  };
}
