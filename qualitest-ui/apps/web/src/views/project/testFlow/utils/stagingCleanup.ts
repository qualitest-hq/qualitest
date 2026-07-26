/**
 * AI Staging 会话级清理工具。
 *
 * 负责在消息截断、切换会话、新建对话时，把画布和运行场景上尚未确认的 Staging 视觉效果撤掉，
 * 并在需要时清空 Staging 内存状态。
 */
import { useAiStagingStore } from '../stores/aiStagingStore';
import { revertStagingUnitOnCanvas } from './stagingCanvasRevert';

/**
 * 按消息 id 集合回滚画布上的 pending Staging。
 * 用于编辑/重新生成导致的消息截断：先恢复画布，再由调用方删除 store 中的单元记录。
 */
export function revertPendingStagingForMessageIds(messageIds: ReadonlySet<string>) {
  const stagingStore = useAiStagingStore();
  for (const unit of Object.values(stagingStore.unitsById)) {
    if (unit.status === 'pending' && messageIds.has(unit.messageId)) {
      revertStagingUnitOnCanvas(unit);
    }
  }
}

/**
 * 回滚当前所有 pending 单元在画布和 runConfig 上的视觉效果。
 * 只动画布，不修改 Staging store。
 */
export function revertAllPendingStagingOnCanvas() {
  const stagingStore = useAiStagingStore();
  for (const unit of Object.values(stagingStore.unitsById)) {
    if (unit.status === 'pending') {
      revertStagingUnitOnCanvas(unit);
    }
  }
}

/**
 * 完整清空 Staging：先撤掉画布上的 pending 痕迹，再重置 store（单元表与 patch 缓存）。
 * 用于新建对话、删除当前会话、加载另一会话之前，避免上一会话的 Staging 残留在画布上。
 */
export function clearAllStagingState() {
  revertAllPendingStagingOnCanvas();
  useAiStagingStore().reset();
}

/**
 * 判断从 fromIndex 起（含）的聊天消息里，是否还有 pending 的 Staging 单元。
 * 用于截断确认弹窗：仅有 pending、没有已合并标记时也要提示用户。
 */
export function hasPendingStagingFromMessageIndex(
  messageIds: readonly { id: string }[],
  fromIndex: number,
): boolean {
  const stagingStore = useAiStagingStore();
  const targetIds = new Set(messageIds.slice(fromIndex).map((m) => m.id));
  if (!targetIds.size) return false;

  return Object.values(stagingStore.unitsById).some(
    (unit) => unit.status === 'pending' && targetIds.has(unit.messageId),
  );
}
