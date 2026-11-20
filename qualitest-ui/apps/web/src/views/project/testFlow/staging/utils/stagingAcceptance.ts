/**
 * Staging 单元确认/取消进度（messageAcceptedMap / messageRejectedMap）。
 *
 * 与 useAiChatSession 的 messageAcceptedMap 绑定，供面板重开或 onSessionLoaded 重灌时恢复单元状态。
 */
import type { Ref } from 'vue';

let acceptedMapRef: Ref<Record<string, Set<string>>> | null = null;
let rejectedMapRef: Ref<Record<string, Set<string>>> | null = null;

export function bindStagingAcceptanceMaps(
  accepted: Ref<Record<string, Set<string>>>,
  rejected: Ref<Record<string, Set<string>>>,
) {
  acceptedMapRef = accepted;
  rejectedMapRef = rejected;
}

export function acceptedStagingUnitIds(messageId: string): ReadonlySet<string> {
  return acceptedMapRef?.value[messageId] ?? new Set();
}

export function rejectedStagingUnitIds(messageId: string): ReadonlySet<string> {
  return rejectedMapRef?.value[messageId] ?? new Set();
}

export function recordStagingUnitConfirmed(messageId: string, unitId: string) {
  if (!acceptedMapRef) return;
  const confirmed = new Set(acceptedMapRef.value[messageId] ?? []);
  confirmed.add(unitId);
  acceptedMapRef.value = { ...acceptedMapRef.value, [messageId]: confirmed };

  if (rejectedMapRef?.value[messageId]?.has(unitId)) {
    const rejected = new Set(rejectedMapRef.value[messageId]);
    rejected.delete(unitId);
    rejectedMapRef.value = { ...rejectedMapRef.value, [messageId]: rejected };
  }
}

export function recordStagingUnitRejected(messageId: string, unitId: string) {
  if (!rejectedMapRef) return;
  const rejected = new Set(rejectedMapRef.value[messageId] ?? []);
  rejected.add(unitId);
  rejectedMapRef.value = { ...rejectedMapRef.value, [messageId]: rejected };

  if (acceptedMapRef?.value[messageId]?.has(unitId)) {
    const confirmed = new Set(acceptedMapRef.value[messageId]);
    confirmed.delete(unitId);
    acceptedMapRef.value = { ...acceptedMapRef.value, [messageId]: confirmed };
  }
}

export function ensureStagingAcceptanceEntry(messageId: string) {
  if (!acceptedMapRef || !rejectedMapRef) return;
  if (!acceptedMapRef.value[messageId]) {
    acceptedMapRef.value = { ...acceptedMapRef.value, [messageId]: new Set() };
  }
  if (!rejectedMapRef.value[messageId]) {
    rejectedMapRef.value = { ...rejectedMapRef.value, [messageId]: new Set() };
  }
}

export function resetStagingAcceptanceMaps() {
  if (acceptedMapRef) acceptedMapRef.value = {};
  if (rejectedMapRef) rejectedMapRef.value = {};
}

export function migrateStagingAcceptanceMaps(idMap: Map<string, string>) {
  if (!idMap.size) return;

  if (acceptedMapRef) {
    const next = { ...acceptedMapRef.value };
    for (const [oldId, newId] of idMap) {
      if (next[oldId]) {
        next[newId] = next[oldId];
        delete next[oldId];
      }
    }
    acceptedMapRef.value = next;
  }

  if (rejectedMapRef) {
    const next = { ...rejectedMapRef.value };
    for (const [oldId, newId] of idMap) {
      if (next[oldId]) {
        next[newId] = next[oldId];
        delete next[oldId];
      }
    }
    rejectedMapRef.value = next;
  }
}

export function pruneStagingAcceptanceForMessages(removedIds: ReadonlySet<string>) {
  if (!removedIds.size) return;

  if (acceptedMapRef) {
    const next = { ...acceptedMapRef.value };
    for (const id of removedIds) delete next[id];
    acceptedMapRef.value = next;
  }

  if (rejectedMapRef) {
    const next = { ...rejectedMapRef.value };
    for (const id of removedIds) delete next[id];
    rejectedMapRef.value = next;
  }
}
