/**
 * 将 FlowDesignPatch 灌入 Staging。
 * 先过滤非法项并 toast，再规范化 HTTP/坐标，最后写入 Staging store；不写库。
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
import { validatePatchForHydrate } from './validatePatchForHydrate';

export type HydratePatchToStagingOptions = {
  /** 关联的助手消息 id；非会话入口可传临时 id */
  messageId: string;
  /** 恢复该消息已确认的单元 id */
  confirmedUnitIds?: ReadonlySet<string>;
  /** 恢复该消息已拒绝的单元 id */
  rejectedUnitIds?: ReadonlySet<string>;
  /** 灌入后打开 AI 侧栏 */
  openAiPanel?: boolean;
};

/**
 * 过滤 → prepare → 避让坐标 → 写入 Staging。
 * @returns 当前 pending Staging 单元数
 */
export async function hydratePatchToStaging(
  patch: FlowDesignPatch,
  options: HydratePatchToStagingOptions,
): Promise<number> {
  const store = useFlowCanvasStore();
  const stagingStore = useAiStagingStore();

  const existingNodeIds = new Set(store.nodes.map((n) => String(n.id)));
  const existingEdgeIds = new Set(store.edges.map((e) => String(e.id)));
  const existingScenarioIds = new Set(
    (store.runConfig?.scenarios ?? []).map((s) => String(s.id)).filter(Boolean),
  );

  const gated = validatePatchForHydrate(patch, {
    existingNodeIds,
    existingEdgeIds,
    existingScenarioIds,
  });
  for (const w of gated.warnings) {
    ElMessage.warning(w);
  }

  const prepared = await preparePatchForStaging(gated.patch);
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
      onRewireWarning: (text: string) => ElMessage.warning(text),
      confirmedUnitIds: options.confirmedUnitIds,
      rejectedUnitIds: options.rejectedUnitIds,
    },
  );
  if (options.openAiPanel) {
    store.openAiDesignPanel();
  }
  return stagingStore.pendingCount;
}

/** patch 是否含任何可灌入 Staging 的增删改项 */
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
