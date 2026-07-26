/**
 * 画布连线与删边的副作用处理。
 *
 * 普通节点：仅追加边记录。
 * condition 节点：从 out-<branchId> 锚点连出时写入 branches[].target；
 * 删边或重连时同步清空/替换对应 target。
 */
import type { Connection, EdgeChange } from '@vue-flow/core';

import { useFlowHistory } from './useFlowHistory';
import { useFlowCanvasStore } from '../stores/flowCanvasStore';
import { getConditionBranches, resolveBranchFromHandle, bindConditionBranchTarget, clearConditionBranchTarget } from '../utils/conditionUtils';
import { nextSnowflakeId } from '@/utils/flow/snowflakeId';

export function useFlowConnect() {
  const store = useFlowCanvasStore();
  const { pushHistory } = useFlowHistory();

  /**
   * 清除 condition 节点 branches 中指向 target 的绑定。
   * 删边或同一分支改连其他节点前调用。
   */
  function clearBranchTarget(nodeId: string, target: string) {
    const node = store.nodes.find((n) => n.id === nodeId);
    if (!node || node.type !== 'condition') return;
    const data = node.data as Record<string, unknown>;
    if (clearConditionBranchTarget(data, target)) {
      Object.assign(node.data, { branches: data.branches });
      store.markDirty();
    }
  }

  /**
   * 将指定分支的 target 写入 condition 节点 data.branches。
   * 内部通过 bindConditionBranchTarget 完成，并写回 node.data。
   */
  function setBranchTarget(nodeId: string, branchId: string, target: string) {
    const node = store.nodes.find((n) => n.id === nodeId);
    if (!node || node.type !== 'condition') return false;
    const data = node.data as Record<string, unknown>;
    const ok = bindConditionBranchTarget(data, { target, sourceHandle: `out-${branchId}` });
    if (ok) Object.assign(node.data, { branches: data.branches });
    return ok;
  }

  /** 新建边：condition 源节点需同步 branches[].target 与 sourceHandle */
  function onConnect(connection: Connection) {
    if (!connection.source || !connection.target) return;

    const srcNode = store.nodes.find((n) => n.id === connection.source);
    const isCondition = srcNode?.type === 'condition';
    const branchId = isCondition ? resolveBranchFromHandle(connection.sourceHandle) : null;

    if (isCondition && branchId) {
      const branches = getConditionBranches(srcNode.data as Record<string, unknown>);
      const branch = branches.find((b) => b.id === branchId);
      if (branch?.target) {
        store.edges = store.edges.filter(
          (e) => !(e.source === connection.source && e.target === branch.target),
        );
      }
      setBranchTarget(connection.source, branchId, connection.target);
    }

    const id = nextSnowflakeId();
    const edge: Record<string, unknown> = {
      id,
      source: connection.source,
      target: connection.target,
      sourceHandle: connection.sourceHandle ?? undefined,
      targetHandle: connection.targetHandle ?? undefined,
    };

    if (isCondition && branchId) {
      edge.type = 'condition';
      edge.sourceHandle = `out-${branchId}`;
    }

    store.edges.push(edge);
    store.markDirty();
    pushHistory();
  }

  /** 边变更回调：删边时清空 condition 分支上对应的 target 字段 */
  function onEdgesChange(changes: EdgeChange[]) {
    let removed = false;
    for (const change of changes) {
      if (change.type !== 'remove') continue;
      removed = true;
      const { source, target } = change;
      if (!source || !target) continue;
      const srcNode = store.nodes.find((n) => n.id === source);
      if (srcNode?.type === 'condition') {
        clearBranchTarget(source, target);
      }
    }
    if (removed) {
      store.markDirty();
      pushHistory();
    }
  }

  return { onConnect, onEdgesChange, clearBranchTarget, setBranchTarget };
}
