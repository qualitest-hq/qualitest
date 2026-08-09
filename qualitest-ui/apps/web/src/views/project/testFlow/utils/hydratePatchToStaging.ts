/**
 * 将 FlowDesignPatch 灌入 Staging（造流 AI patch 与「刷新鉴权头」等非会话提案共用）。
 * 不写库；可选打开 AI 侧栏便于确认。
 */
import { ElMessage } from 'element-plus';

import type { FlowDesignPatch } from '../types/aiDesignTypes';
import { useAiStagingStore } from '../stores/aiStagingStore';
import { useFlowCanvasStore } from '../stores/flowCanvasStore';
import { preparePatchForStaging } from './preparePatchForStaging';
import {
  obstaclesFromCanvasNodes,
  spreadStagingAddPositions,
} from './spreadStagingAddPositions';

export type HydratePatchToStagingOptions = {
  /** 关联的消息 id（会话内 AI patch）；缺省由调用方传入临时 id */
  messageId: string;
  /** 恢复该消息已确认/已拒绝单元（会话 reload 用） */
  confirmedUnitIds?: ReadonlySet<string>;
  rejectedUnitIds?: ReadonlySet<string>;
  /** 灌入后打开 AI 侧栏（刷新鉴权头等非会话入口） */
  openAiPanel?: boolean;
};

/**
 * prepare → 避让坐标 → hydrateStagingFromPatch。
 * @returns 当前 pending Staging 单元数
 */
export async function hydratePatchToStaging(
  patch: FlowDesignPatch,
  options: HydratePatchToStagingOptions,
): Promise<number> {
  const prepared = await preparePatchForStaging(patch);
  const store = useFlowCanvasStore();
  const stagingStore = useAiStagingStore();
  const excludeIds = new Set(
    (prepared.addNodes ?? []).map((n) => n.id).filter((id): id is string => !!id),
  );
  const spread = spreadStagingAddPositions(
    prepared,
    obstaclesFromCanvasNodes(store.nodes, excludeIds),
  );
  stagingStore.hydrateStagingFromPatch(
    options.messageId,
    spread,
    {
      nodes: store.nodes,
      edges: store.edges,
      runConfig: store.runConfig,
    },
    {
      onConflict: (text: string) => ElMessage.info(text),
      confirmedUnitIds: options.confirmedUnitIds,
      rejectedUnitIds: options.rejectedUnitIds,
    },
  );
  if (options.openAiPanel) {
    store.openAiDesignPanel();
  }
  return stagingStore.pendingCount;
}

/** patch 是否含任何可灌入 Staging 的变更 */
export function flowDesignPatchHasChanges(patch: FlowDesignPatch | undefined | null): boolean {
  if (!patch) return false;
  return (patch.updateNodes?.length ?? 0) > 0
    || (patch.addNodes?.length ?? 0) > 0
    || (patch.addEdges?.length ?? 0) > 0
    || (patch.updateEdges?.length ?? 0) > 0
    || (patch.suggestedDeletes?.nodeIds?.length ?? 0) > 0
    || (patch.suggestedDeletes?.edgeIds?.length ?? 0) > 0
    || Boolean(patch.scenarioPatch);
}
