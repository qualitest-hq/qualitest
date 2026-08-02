/**
 * AI patch 到达与会话加载时的 Staging 灌入。
 *
 * 把 assistant 消息中的 patch 转成 Staging 单元并反映到画布；
 * 切换会话时先清空旧 Staging，再按当前会话消息重新灌入。
 * 已确认/已取消单元从 messageAcceptedMap / messageRejectedMap 恢复状态。
 */
import type { Ref } from 'vue';
import { ElMessage } from 'element-plus';

import type { AiDesignMessageView, FlowDesignPatch } from '../types/aiDesignTypes';
import { useAiStagingStore } from '../stores/aiStagingStore';
import { useFlowCanvasStore } from '../stores/flowCanvasStore';
import { clearAllStagingState } from '../utils/stagingCleanup';
import {
  acceptedStagingUnitIds,
  rejectedStagingUnitIds,
} from '../utils/stagingAcceptance';
import { preparePatchForStaging } from '../utils/preparePatchForStaging';
import {
  obstaclesFromCanvasNodes,
  spreadStagingAddPositions,
} from '../utils/spreadStagingAddPositions';

export function createAiStagingHydration(messages: Ref<AiDesignMessageView[]>) {
  const stagingStore = useAiStagingStore();

  function acceptanceOptionsForMessage(messageId: string) {
    return {
      confirmedUnitIds: acceptedStagingUnitIds(messageId),
      rejectedUnitIds: rejectedStagingUnitIds(messageId),
    };
  }

  /** 将单条消息的 patch 灌入 Staging store 并同步到画布 */
  async function hydrateStagingForMessage(messageId: string, patch: FlowDesignPatch) {
    const prepared = await preparePatchForStaging(patch);
    const store = useFlowCanvasStore();
    const excludeIds = new Set(
      (prepared.addNodes ?? []).map((n) => n.id).filter((id): id is string => !!id),
    );
    // 确认前避让：相对正式节点及其它 pending，错开本轮 addNodes 坐标
    const spread = spreadStagingAddPositions(
      prepared,
      obstaclesFromCanvasNodes(store.nodes, excludeIds),
    );
    stagingStore.hydrateStagingFromPatch(
      messageId,
      spread,
      {
        nodes: store.nodes,
        edges: store.edges,
        runConfig: store.runConfig,
      },
      {
        onConflict: (text: string) => ElMessage.info(text),
        ...acceptanceOptionsForMessage(messageId),
      },
    );
  }

  /** 遍历当前会话所有 assistant patch 消息，逐条灌入 Staging */
  async function syncAllStagingFromMessages() {
    for (const msg of messages.value) {
      if (msg.role !== 'assistant' || !msg.patch || msg.explainOnly) continue;
      await hydrateStagingForMessage(msg.id, msg.patch);
    }
  }

  /** 懒加载 patch 完成后的回调：补灌对应消息的 Staging */
  async function onPatchHydrated(messageId: string) {
    const msg = messages.value.find((m) => m.id === messageId);
    if (msg?.patch && !msg.explainOnly) {
      await hydrateStagingForMessage(messageId, msg.patch);
    }
  }

  /**
   * 会话切换或加载完成时调用：
   * 1. 清空上一会话留在画布和 store 里的 Staging
   * 2. 按新会话消息列表重新构建 Staging（恢复 accepted/rejected 进度）
   */
  async function onSessionLoaded() {
    clearAllStagingState();
    await syncAllStagingFromMessages();
  }

  return {
    hydrateStagingForMessage,
    syncAllStagingFromMessages,
    onPatchHydrated,
    onSessionLoaded,
  };
}
