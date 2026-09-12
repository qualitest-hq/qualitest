/**
 * AI Staging 单元状态：CRUD、画布 parallel map、保存过滤与会话摘要。
 */
import { computed, ref } from 'vue';

import type { GraphValidationResult } from '@/utils/flow/graphValidate';

import type {
  AiStagingCanvasMark,
  AiStagingMessageSummary,
  AiStagingUnit,
  ScenarioStagingPersistFilter,
  StagingBuildContext,
  StagingPersistFilter,
} from '../types/aiStagingTypes';
import type { FlowDesignPatch } from '../types/aiDesignTypes';
import { buildStagingUnits } from '../utils/buildStagingUnits';
import { filterPatchForConfirm } from '../utils/filterPatchForConfirm';
import { normalizeFlowDesignPatchIdsWithWarnings } from '../utils/patchIdNormalize';
import {
  graphObjectIdFromUnit,
  isScenarioKind,
  objectIdFromUnitId,
  stagingKindToCanvasMode,
} from '../utils/stagingUnitIds';
import { revertStagingUnitOnCanvas } from '../utils/stagingCanvasRevert';

function buildStagingParallelMap(
  units: Record<string, AiStagingUnit>,
  pickId: (unit: AiStagingUnit) => string | undefined,
): Record<string, AiStagingCanvasMark> {
  const map: Record<string, AiStagingCanvasMark> = {};
  for (const unit of Object.values(units)) {
    if (unit.status !== 'pending') continue;
    const mode = stagingKindToCanvasMode(unit.kind);
    const id = pickId(unit);
    if (!mode || !id) continue;
    map[id] = { unitId: unit.unitId, messageId: unit.messageId, mode };
  }
  return map;
}

function buildNodePersistFilter(units: AiStagingUnit[]) {
  const excludeNodeIds = new Set<string>();
  const nodeBaselines = new Map<
    string,
    { type?: string; position?: { x: number; y: number }; data?: Record<string, unknown> }
  >();

  for (const unit of units) {
    const { nodeId } = graphObjectIdFromUnit(unit);
    if (unit.kind === 'addNode' && nodeId) excludeNodeIds.add(nodeId);
    if (unit.kind === 'updateNode' && nodeId && unit.baseline) {
      nodeBaselines.set(nodeId, {
        type: unit.baseline.type as string | undefined,
        position: unit.baseline.position as { x: number; y: number } | undefined,
        data: unit.baseline.data as Record<string, unknown> | undefined,
      });
    }
  }

  return { excludeNodeIds, nodeBaselines };
}

function buildEdgePersistFilter(units: AiStagingUnit[]) {
  const excludeEdgeIds = new Set<string>();
  const edgeBaselines = new Map<string, { source?: string; target?: string; label?: string }>();

  for (const unit of units) {
    const { edgeId } = graphObjectIdFromUnit(unit);
    if (unit.kind === 'addEdge' && edgeId) excludeEdgeIds.add(edgeId);
    if (unit.kind === 'updateEdge' && edgeId && unit.baseline) {
      edgeBaselines.set(edgeId, {
        source: unit.baseline.source as string | undefined,
        target: unit.baseline.target as string | undefined,
        label: unit.baseline.label as string | undefined,
      });
    }
  }

  return { excludeEdgeIds, edgeBaselines };
}

function buildScenarioPersistFilter(units: AiStagingUnit[]): ScenarioStagingPersistFilter | undefined {
  const filter: ScenarioStagingPersistFilter = {
    excludeScenarioIds: new Set<string>(),
    scenarioBaselines: new Map<string, Record<string, unknown>>(),
  };

  for (const unit of units) {
    if (unit.kind === 'addScenario') {
      filter.excludeScenarioIds.add(objectIdFromUnitId(unit.unitId));
    }
    if (unit.kind === 'updateScenario') {
      const id = objectIdFromUnitId(unit.unitId);
      if (unit.baseline) filter.scenarioBaselines.set(id, unit.baseline);
    }
  }

  if (!filter.excludeScenarioIds.size && !filter.scenarioBaselines.size) {
    return undefined;
  }
  return filter;
}

export const useAiStagingStore = defineStore('aiStaging', () => {
  const unitsById = ref<Record<string, AiStagingUnit>>({});
  const patchesByMessageId = ref<Record<string, FlowDesignPatch>>({});

  const stagingByNodeId = computed(() =>
    buildStagingParallelMap(unitsById.value, (u) => graphObjectIdFromUnit(u).nodeId),
  );

  const stagingByEdgeId = computed(() =>
    buildStagingParallelMap(unitsById.value, (u) => graphObjectIdFromUnit(u).edgeId),
  );

  const stagingByScenarioId = computed(() =>
    buildStagingParallelMap(unitsById.value, (u) => graphObjectIdFromUnit(u).scenarioId),
  );

  const pendingCount = computed(() => Object.values(unitsById.value).filter((u) => u.status === 'pending').length);

  function getUnit(unitId: string): AiStagingUnit | undefined {
    return unitsById.value[unitId];
  }

  function getPatchForMessage(messageId: string): FlowDesignPatch | undefined {
    return patchesByMessageId.value[messageId];
  }

  function listUnitsForMessage(messageId: string): AiStagingUnit[] {
    return Object.values(unitsById.value).filter((u) => u.messageId === messageId);
  }

  function listConfirmedUnitIds(messageId?: string): string[] {
    return Object.values(unitsById.value)
      .filter((u) => u.status === 'confirmed' && (!messageId || u.messageId === messageId))
      .map((u) => u.unitId);
  }

  function patchUnit(unitId: string, patch: Partial<AiStagingUnit>) {
    const current = unitsById.value[unitId];
    if (!current) return;
    unitsById.value = { ...unitsById.value, [unitId]: { ...current, ...patch } };
  }

  function updateDraft(unitId: string, draft: Record<string, unknown>) {
    patchUnit(unitId, { draft: { ...draft } });
  }

  function setConfirmInFlight(unitId: string, inFlight: boolean) {
    patchUnit(unitId, { confirmInFlight: inFlight });
  }

  function markConfirmFailed(
    unitId: string,
    validation: GraphValidationResult,
    options?: { dependencyHints?: string[] },
  ) {
    patchUnit(unitId, {
      lastValidation: validation,
      lastDependencyHints: options?.dependencyHints?.length ? [...options.dependencyHints] : undefined,
      confirmInFlight: false,
      status: 'pending',
    });
  }

  function markConfirmed(unitId: string) {
    const unit = unitsById.value[unitId];
    patchUnit(unitId, {
      status: 'confirmed',
      lastValidation: undefined,
      lastDependencyHints: undefined,
      confirmInFlight: false,
      confirmedAt: Date.now(),
    });
    if (unit) {
      pruneUnitFromMessagePatch(unit.messageId, unitId);
    }
  }

  function markRejected(unitId: string) {
    const unit = unitsById.value[unitId];
    patchUnit(unitId, {
      status: 'rejected',
      lastValidation: undefined,
      lastDependencyHints: undefined,
      confirmInFlight: false,
    });
    if (unit) {
      pruneUnitFromMessagePatch(unit.messageId, unitId);
    }
  }

  function removeMessageUnits(messageId: string, statuses?: AiStagingUnit['status'][]) {
    const next = { ...unitsById.value };
    for (const [id, unit] of Object.entries(next)) {
      if (unit.messageId !== messageId) continue;
      if (statuses && !statuses.includes(unit.status)) continue;
      delete next[id];
    }
    unitsById.value = next;
    const patches = { ...patchesByMessageId.value };
    delete patches[messageId];
    patchesByMessageId.value = patches;
  }

  /**
   * 将一条助手 patch 灌入 Staging。
   * 规范化 id 与边端点（改写必回调 onRewireWarning）；
   * 同消息旧 pending 先清再重建；
   * 与其它消息 pending 占用同一 node/edge/scenario id 时，回滚旧单元画布效果并替换。
   */
  function hydrateStagingFromPatch(
    messageId: string,
    patch: FlowDesignPatch,
    ctx: StagingBuildContext,
    options?: {
      onConflict?: (message: string) => void;
      /** 边端点启发式改写时的警告文案 */
      onRewireWarning?: (message: string) => void;
      confirmedUnitIds?: ReadonlySet<string>;
      rejectedUnitIds?: ReadonlySet<string>;
    },
  ) {
    const { patch: normalizedPatch, rewireWarnings } = normalizeFlowDesignPatchIdsWithWarnings(patch);
    for (const w of rewireWarnings) {
      options?.onRewireWarning?.(w);
    }
    patchesByMessageId.value = { ...patchesByMessageId.value, [messageId]: normalizedPatch };

    const next = { ...unitsById.value };
    for (const [id, unit] of Object.entries(next)) {
      if (unit.messageId === messageId && unit.status === 'pending') {
        delete next[id];
      }
    }

    const newUnits = buildStagingUnits(normalizedPatch, messageId, ctx, {
      confirmedUnitIds: options?.confirmedUnitIds,
      rejectedUnitIds: options?.rejectedUnitIds,
    });
    for (const unit of newUnits) {
      const existing = next[unit.unitId];
      if (existing && existing.status !== 'pending') {
        continue;
      }

      const { nodeId, edgeId, scenarioId } = graphObjectIdFromUnit(unit);
      if (nodeId) {
        const conflict = Object.values(next).find(
          (u) =>
            u.status === 'pending' &&
            u.messageId !== messageId &&
            graphObjectIdFromUnit(u).nodeId === nodeId &&
            (u.kind === 'updateNode' || u.kind === 'deleteNode' || u.kind === 'addNode'),
        );
        if (conflict) {
          // 新 patch 占用同一节点：撤掉旧单元在画布上的展示，再写入新单元
          revertStagingUnitOnCanvas(conflict);
          delete next[conflict.unitId];
          options?.onConflict?.(`节点 ${nodeId} 有新的 AI 建议，已覆盖上一批待确认变更`);
        }
      }
      if (edgeId) {
        const conflict = Object.values(next).find(
          (u) =>
            u.status === 'pending' &&
            u.messageId !== messageId &&
            graphObjectIdFromUnit(u).edgeId === edgeId,
        );
        if (conflict) {
          revertStagingUnitOnCanvas(conflict);
          delete next[conflict.unitId];
          options?.onConflict?.(`连线 ${edgeId} 有新的 AI 建议，已覆盖上一批待确认变更`);
        }
      }
      if (scenarioId) {
        const conflict = Object.values(next).find(
          (u) =>
            u.status === 'pending' &&
            u.messageId !== messageId &&
            graphObjectIdFromUnit(u).scenarioId === scenarioId &&
            isScenarioKind(u.kind),
        );
        if (conflict) {
          revertStagingUnitOnCanvas(conflict);
          delete next[conflict.unitId];
          options?.onConflict?.(`场景 ${scenarioId} 有新的 AI 建议，已覆盖上一批待确认变更`);
        }
      }
      next[unit.unitId] = unit;
    }

    unitsById.value = next;
  }

  /**
   * 单元确认或拒绝后，从该消息缓存的 patch 里去掉该单元，减小后续 confirm 请求体。
   * 只保留同消息仍 pending 的其它单元。
   */
  function pruneUnitFromMessagePatch(messageId: string, unitId: string) {
    const patch = patchesByMessageId.value[messageId];
    if (!patch) return;
    const keep = new Set(
      Object.values(unitsById.value)
        .filter((u) => u.messageId === messageId && u.unitId !== unitId && u.status === 'pending')
        .map((u) => u.unitId),
    );
    patchesByMessageId.value = {
      ...patchesByMessageId.value,
      [messageId]: filterPatchForConfirm(patch, keep),
    };
  }

  function buildMessageSummary(messageId: string): AiStagingMessageSummary {
    const units = listUnitsForMessage(messageId);
    const countByKind = (kind: AiStagingUnit['kind']) => units.filter((u) => u.kind === kind).length;

    return {
      messageId,
      pending: units.filter((u) => u.status === 'pending').length,
      confirmed: units.filter((u) => u.status === 'confirmed').length,
      rejected: units.filter((u) => u.status === 'rejected').length,
      addNodeCount: countByKind('addNode'),
      updateNodeCount: countByKind('updateNode'),
      deleteNodeCount: countByKind('deleteNode'),
      addEdgeCount: countByKind('addEdge'),
      updateEdgeCount: countByKind('updateEdge'),
      deleteEdgeCount: countByKind('deleteEdge'),
      scenarioCount: units.filter((u) => isScenarioKind(u.kind)).length,
    };
  }

  /**
   * 生成保存/校验/AI 请求用的过滤规则：
   * - pending 的 add 节点/边不入图
   * - pending 的 update 按 baseline 输出
   * - pending 的 delete 仍显示在画布，由业务层单独处理
   */
  function buildPersistFilter(): StagingPersistFilter {
    const pending = Object.values(unitsById.value).filter((u) => u.status === 'pending');
    return buildPersistFilterFromPending(pending);
  }

  /**
   * 单单元 confirm 时组装 graph_json：排除其它 pending Staging，仅保留本次确认对象。
   * 避免多个待新增节点（如两个开始节点）互相导致全图校验失败。
   */
  function buildConfirmPersistFilter(confirmingUnitId: string): StagingPersistFilter {
    const pending = Object.values(unitsById.value).filter(
      (u) => u.status === 'pending' && u.unitId !== confirmingUnitId,
    );
    return buildPersistFilterFromPending(pending);
  }

  function buildPersistFilterFromPending(pending: AiStagingUnit[]): StagingPersistFilter {
    const nodePart = buildNodePersistFilter(pending);
    const edgePart = buildEdgePersistFilter(pending);
    const scenarioFilter = buildScenarioPersistFilter(pending);

    return {
      ...nodePart,
      ...edgePart,
      scenarioFilter,
    };
  }

  /** 清空全部 Staging 单元与 patch 缓存（切换会话前由外部先回滚画布） */
  function reset() {
    unitsById.value = {};
    patchesByMessageId.value = {};
  }

  function migrateMessageIds(idMap: Map<string, string>) {
    if (!idMap.size) return;

    const nextUnits = { ...unitsById.value };
    for (const [unitId, unit] of Object.entries(nextUnits)) {
      const newMessageId = idMap.get(unit.messageId);
      if (newMessageId) {
        nextUnits[unitId] = { ...unit, messageId: newMessageId };
      }
    }
    unitsById.value = nextUnits;

    const nextPatches = { ...patchesByMessageId.value };
    for (const [oldId, newId] of idMap) {
      if (nextPatches[oldId]) {
        nextPatches[newId] = nextPatches[oldId];
        delete nextPatches[oldId];
      }
    }
    patchesByMessageId.value = nextPatches;
  }

  return {
    unitsById,
    patchesByMessageId,
    stagingByNodeId,
    stagingByEdgeId,
    stagingByScenarioId,
    pendingCount,
    getUnit,
    getPatchForMessage,
    listUnitsForMessage,
    listConfirmedUnitIds,
    updateDraft,
    setConfirmInFlight,
    markConfirmFailed,
    markConfirmed,
    markRejected,
    removeMessageUnits,
    hydrateStagingFromPatch,
    buildMessageSummary,
    buildPersistFilter,
    buildConfirmPersistFilter,
    migrateMessageIds,
    reset,
  };
});
