/**
 * 列出当前可确认的 Staging pending 单元（依赖已满足、且不在 skip 集）。
 *
 * 排序：跨消息按 messageId；同消息内按 patch 枚举序（节点先于边）。
 */
import type { FlowDesignPatch } from '../types/aiDesignTypes';
import { enumeratePatchUnitIds } from './stagingFocusNavigation';
import { isStagingConfirmBlockedByDependencies } from './stagingDependencyHints';

export type ReadyPendingUnitRef = {
  unitId: string;
  messageId: string;
};

export type ListReadyPendingUnitIdsOptions = {
  pendingUnits: ReadonlyArray<ReadyPendingUnitRef>;
  getPatchForMessage: (messageId: string) => FlowDesignPatch | undefined;
  confirmedUnitIds: ReadonlySet<string>;
  skipUnitIds?: ReadonlySet<string>;
};

/** 当前依赖已满足、可发起 confirm 的 pending unitId 列表 */
export function listReadyPendingUnitIds(options: ListReadyPendingUnitIdsOptions): string[] {
  const skip = options.skipUnitIds ?? new Set<string>();
  const candidates = options.pendingUnits.filter((unit) => {
    if (skip.has(unit.unitId)) return false;
    const patch = options.getPatchForMessage(unit.messageId);
    if (!patch) return false;
    return !isStagingConfirmBlockedByDependencies(
      unit.unitId,
      patch,
      options.confirmedUnitIds,
    );
  });

  const orderCache = new Map<string, string[]>();
  function patchOrder(messageId: string, unitId: string): number {
    let ids = orderCache.get(messageId);
    if (!ids) {
      const patch = options.getPatchForMessage(messageId);
      ids = patch ? enumeratePatchUnitIds(patch) : [];
      orderCache.set(messageId, ids);
    }
    const idx = ids.indexOf(unitId);
    return idx >= 0 ? idx : Number.MAX_SAFE_INTEGER;
  }

  return candidates
    .slice()
    .sort((a, b) => {
      const byMessage = a.messageId.localeCompare(b.messageId);
      if (byMessage !== 0) return byMessage;
      return patchOrder(a.messageId, a.unitId) - patchOrder(b.messageId, b.unitId);
    })
    .map((unit) => unit.unitId);
}
