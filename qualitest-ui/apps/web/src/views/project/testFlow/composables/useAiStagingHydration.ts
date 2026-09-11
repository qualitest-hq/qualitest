/**
 * AI patch 到达与会话加载时的 Staging 灌入。
 *
 * 把 assistant 消息中的 patch 转成 Staging 单元并反映到画布；
 * 切换会话时先清空旧 Staging，再按当前会话消息重新灌入。
 * 已确认/已取消单元从 messageAcceptedMap / messageRejectedMap 恢复状态。
 */
import type { Ref } from 'vue';

import type { AiDesignMessageView, FlowDesignPatch } from '../types/aiDesignTypes';
import { clearAllStagingState } from '../utils/stagingCleanup';
import {
  acceptedStagingUnitIds,
  rejectedStagingUnitIds,
} from '../utils/stagingAcceptance';
import { hydratePatchToStaging } from '../utils/hydratePatchToStaging';

export function createAiStagingHydration(messages: Ref<AiDesignMessageView[]>) {
  /** 将单条消息的 patch 灌入 Staging store 并同步到画布 */
  async function hydrateStagingForMessage(messageId: string, patch: FlowDesignPatch) {
    await hydratePatchToStaging(patch, {
      messageId,
      confirmedUnitIds: acceptedStagingUnitIds(messageId),
      rejectedUnitIds: rejectedStagingUnitIds(messageId),
    });
  }

  /** 遍历当前会话所有助手消息：跳过 explainOnly，有 patch 的逐条灌入 Staging */
  async function syncAllStagingFromMessages() {
    for (const msg of messages.value) {
      if (msg.role !== 'assistant' || !msg.patch || msg.explainOnly) continue;
      await hydrateStagingForMessage(msg.id, msg.patch);
    }
  }

  /** 懒加载完整 patch 完成后：非答疑消息才补灌 Staging */
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
