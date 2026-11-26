/**
 * 列出当前 pending Staging 单元（依赖不挡），并稳定排序。
 *
 * 排序：跨消息按 messageId；同消息内按 patch 枚举序（节点先于边）。
 */
import type { FlowDesignPatch } from '../types/aiDesignTypes';
import { enumeratePatchUnitIds } from './stagingFocusNavigation';

export type PendingUnitRef = {
  unitId: string;
  messageId: string;
};

export type ListPendingUnitIdsOptions = {
  pendingUnits: ReadonlyArray<PendingUnitRef>;
  getPatchForMessage: (messageId: string) => FlowDesignPatch | undefined;
  skipUnitIds?: ReadonlySet<string>;
};

/** 当前 pending unitId 列表（可发起 confirm；依赖不挡） */
export function listPendingUnitIdsSorted(options: ListPendingUnitIdsOptions): string[] {
  const skip = options.skipUnitIds ?? new Set<string>();
  const candidates = options.pendingUnits.filter((unit) => {
    if (skip.has(unit.unitId)) return false;
    return !!options.getPatchForMessage(unit.messageId);
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
