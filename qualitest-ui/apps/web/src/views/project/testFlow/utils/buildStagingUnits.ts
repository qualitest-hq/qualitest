/**
 * 将 AI 返回的 FlowDesignPatch 转为 Staging 单元列表。
 *
 * unitId 与 Merger 键名一致（如 addNode:9001、scenario:activeScenarioId）。
 */
import type { Edge, Node } from '@vue-flow/core';

import type { GraphRunScenario, GraphScenarioConfig } from '@/utils/flow/graphTypes';

import type { AiStagingUnit, StagingBuildContext } from '../types/aiStagingTypes';
import type { DiffItemKind, FlowDesignPatch } from '../types/aiDesignTypes';

interface StagingUnitSeed {
  id: string;
  kind: DiffItemKind;
  label: string;
}

function cloneRecord<T extends Record<string, unknown>>(value: T | undefined): Record<string, unknown> | undefined {
  if (!value) return undefined;
  return JSON.parse(JSON.stringify(value)) as Record<string, unknown>;
}

function nodeDisplayName(
  nodeId: string,
  ctx: StagingBuildContext,
  fallbackData?: Record<string, unknown>,
): string {
  const onCanvas = ctx.nodes.find((n) => n.id === nodeId);
  const name = (onCanvas?.data?.name as string) || (fallbackData?.name as string);
  if (name?.trim()) return name.trim();
  const type = onCanvas?.type || (fallbackData?.type as string);
  return type ? `${type} · ${nodeId}` : nodeId;
}

function edgeEndpointLabel(nodeId: string, ctx: StagingBuildContext): string {
  const node = ctx.nodes.find((n) => n.id === nodeId);
  const name = node?.data?.name as string | undefined;
  return name?.trim() ? `${name}(${nodeId})` : nodeId;
}

function findScenario(scenarios: GraphRunScenario[], id: string) {
  return scenarios.find((s) => s.id === id);
}

function enumeratePatchUnits(patch: FlowDesignPatch, ctx: StagingBuildContext): StagingUnitSeed[] {
  const items: StagingUnitSeed[] = [];

  for (const node of patch.addNodes ?? []) {
    const name = (node.data?.name as string) || node.type;
    items.push({
      id: `addNode:${node.id}`,
      kind: 'addNode',
      label: `新增节点：${name}`,
    });
  }

  for (const node of patch.updateNodes ?? []) {
    const canvasName = ctx.nodes.find((n) => n.id === node.id)?.data?.name as string | undefined;
    const name = canvasName?.trim() || (node.data?.name as string) || node.type;
    items.push({
      id: `updateNode:${node.id}`,
      kind: 'updateNode',
      label: `修改节点：${name || node.id}`,
    });
  }

  for (const edge of patch.addEdges ?? []) {
    items.push({
      id: `addEdge:${edge.id}`,
      kind: 'addEdge',
      label: `新增连线：${edgeEndpointLabel(edge.source, ctx)} → ${edgeEndpointLabel(edge.target, ctx)}`,
    });
  }

  for (const edge of patch.updateEdges ?? []) {
    const src = edge.source ?? ctx.edges.find((e) => e.id === edge.id)?.source ?? '?';
    const tgt = edge.target ?? ctx.edges.find((e) => e.id === edge.id)?.target ?? '?';
    items.push({
      id: `updateEdge:${edge.id}`,
      kind: 'updateEdge',
      label: `修改连线：${edgeEndpointLabel(src, ctx)} → ${edgeEndpointLabel(tgt, ctx)}`,
    });
  }

  for (const nodeId of patch.suggestedDeletes?.nodeIds ?? []) {
    items.push({
      id: `deleteNode:${nodeId}`,
      kind: 'deleteNode',
      label: `删除节点：${nodeDisplayName(nodeId, ctx)}`,
    });
  }

  for (const edgeId of patch.suggestedDeletes?.edgeIds ?? []) {
    const edge = ctx.edges.find((e) => e.id === edgeId);
    const label = edge
      ? `${edgeEndpointLabel(edge.source, ctx)} → ${edgeEndpointLabel(edge.target, ctx)}`
      : edgeId;
    items.push({
      id: `deleteEdge:${edgeId}`,
      kind: 'deleteEdge',
      label: `删除连线：${label}`,
    });
  }

  const meta = patch.scenarioPatch;
  if (meta?.activeScenarioId) {
    const name = findScenario(ctx.runConfig?.scenarios ?? [], meta.activeScenarioId)?.name;
    items.push({
      id: 'scenario:activeScenarioId',
      kind: 'setActiveScenario',
      label: `切换默认场景：${name || meta.activeScenarioId}`,
    });
  }

  for (const scenario of meta?.addScenarios ?? []) {
    items.push({
      id: `addScenario:${scenario.id}`,
      kind: 'addScenario',
      label: `新增场景：${scenario.name || scenario.id}`,
    });
  }

  for (const scenario of meta?.updateScenarios ?? []) {
    const name =
      scenario.name || findScenario(ctx.runConfig?.scenarios ?? [], scenario.id)?.name || scenario.id;
    items.push({
      id: `updateScenario:${scenario.id}`,
      kind: 'updateScenario',
      label: `修改场景：${name}`,
    });
  }

  for (const scenarioId of meta?.deleteScenarioIds ?? []) {
    const name = findScenario(ctx.runConfig?.scenarios ?? [], scenarioId)?.name;
    items.push({
      id: `deleteScenario:${scenarioId}`,
      kind: 'deleteScenario',
      label: `删除场景：${name || scenarioId}`,
    });
  }

  return items;
}

function nodeBaseline(
  nodeId: string,
  ctx: StagingBuildContext,
): Record<string, unknown> | undefined {
  const existing = ctx.nodes.find((n) => n.id === nodeId);
  if (!existing) return undefined;
  return {
    type: existing.type,
    position: existing.position ? { ...existing.position } : undefined,
    data: cloneRecord((existing.data ?? {}) as Record<string, unknown>),
  };
}

function edgeBaseline(
  edgeId: string,
  ctx: StagingBuildContext,
): Record<string, unknown> | undefined {
  const existing = ctx.edges.find((e) => e.id === edgeId);
  if (!existing) return undefined;
  return {
    source: existing.source,
    target: existing.target,
    label: existing.label,
  };
}

function scenarioBaseline(scenarioId: string, ctx: StagingBuildContext): Record<string, unknown> | undefined {
  const existing = ctx.runConfig?.scenarios.find((s) => s.id === scenarioId);
  if (!existing) return undefined;
  return cloneRecord(existing as Record<string, unknown>);
}

function extractPatchSlice(kind: DiffItemKind, unitId: string, patch: FlowDesignPatch): unknown {
  if (kind === 'addNode') {
    const id = unitId.slice('addNode:'.length);
    return patch.addNodes?.find((n) => n.id === id);
  }
  if (kind === 'updateNode') {
    const id = unitId.slice('updateNode:'.length);
    return patch.updateNodes?.find((n) => n.id === id);
  }
  if (kind === 'addEdge') {
    const id = unitId.slice('addEdge:'.length);
    return patch.addEdges?.find((e) => e.id === id);
  }
  if (kind === 'updateEdge') {
    const id = unitId.slice('updateEdge:'.length);
    return patch.updateEdges?.find((e) => e.id === id);
  }
  if (kind === 'deleteNode') {
    return { nodeId: unitId.slice('deleteNode:'.length) };
  }
  if (kind === 'deleteEdge') {
    return { edgeId: unitId.slice('deleteEdge:'.length) };
  }
  if (kind === 'setActiveScenario') {
    return { activeScenarioId: patch.scenarioPatch?.activeScenarioId };
  }
  if (kind === 'addScenario') {
    const id = unitId.slice('addScenario:'.length);
    return patch.scenarioPatch?.addScenarios?.find((s) => s.id === id);
  }
  if (kind === 'updateScenario') {
    const id = unitId.slice('updateScenario:'.length);
    return patch.scenarioPatch?.updateScenarios?.find((s) => s.id === id);
  }
  if (kind === 'deleteScenario') {
    return { scenarioId: unitId.slice('deleteScenario:'.length) };
  }
  return undefined;
}

function buildDraft(
  kind: DiffItemKind,
  unitId: string,
  patchSlice: unknown,
  baseline: Record<string, unknown> | undefined,
): Record<string, unknown> | undefined {
  if (kind === 'addNode' || kind === 'updateNode') {
    const slice = patchSlice as { type?: string; position?: { x: number; y: number }; data?: Record<string, unknown> } | undefined;
    if (!slice) return undefined;
    const draft: Record<string, unknown> = {};
    if (slice.type) draft.type = slice.type;
    if (slice.position) draft.position = { ...slice.position };
    const baseData = (baseline?.data as Record<string, unknown> | undefined) ?? {};
    draft.data = { ...baseData, ...(slice.data ?? {}) };
    return draft;
  }
  if (kind === 'addEdge' || kind === 'updateEdge') {
    const slice = patchSlice as { source?: string; target?: string; label?: string } | undefined;
    if (!slice) return undefined;
    return {
      source: slice.source ?? baseline?.source,
      target: slice.target ?? baseline?.target,
      label: slice.label ?? baseline?.label,
    };
  }
  if (kind === 'addScenario' || kind === 'updateScenario') {
    const slice = patchSlice as GraphRunScenario | undefined;
    if (!slice) return undefined;
    return { ...(baseline ?? {}), ...cloneRecord(slice as Record<string, unknown>) };
  }
  if (kind === 'setActiveScenario') {
    const slice = patchSlice as { activeScenarioId?: string } | undefined;
    if (!slice?.activeScenarioId) return undefined;
    return { activeScenarioId: slice.activeScenarioId };
  }
  return undefined;
}

function buildBaseline(
  kind: DiffItemKind,
  unitId: string,
  ctx: StagingBuildContext,
): Record<string, unknown> | undefined {
  if (kind === 'updateNode') {
    return nodeBaseline(unitId.slice('updateNode:'.length), ctx);
  }
  if (kind === 'updateEdge') {
    return edgeBaseline(unitId.slice('updateEdge:'.length), ctx);
  }
  if (kind === 'updateScenario') {
    return scenarioBaseline(unitId.slice('updateScenario:'.length), ctx);
  }
  if (kind === 'setActiveScenario') {
    return { activeScenarioId: ctx.runConfig?.activeScenarioId };
  }
  return undefined;
}

/**
 * 将 patch 转为 Staging 单元列表，默认 status=pending。
 */
export function buildStagingUnits(
  patch: FlowDesignPatch | undefined,
  messageId: string,
  ctx?: StagingBuildContext,
  options?: {
    confirmedUnitIds?: ReadonlySet<string>;
    rejectedUnitIds?: ReadonlySet<string>;
  },
): AiStagingUnit[] {
  if (!patch) return [];
  const context: StagingBuildContext = ctx ?? { nodes: [] as Node[], edges: [] as Edge[] };
  const seeds = enumeratePatchUnits(patch, context);
  const confirmed = options?.confirmedUnitIds;
  const rejected = options?.rejectedUnitIds;

  return seeds.map((item) => {
    const patchSlice = extractPatchSlice(item.kind, item.id, patch);
    const baseline = buildBaseline(item.kind, item.id, context);
    const draft = buildDraft(item.kind, item.id, patchSlice, baseline);

    let status: AiStagingUnit['status'] = 'pending';
    if (confirmed?.has(item.id)) status = 'confirmed';
    else if (rejected?.has(item.id)) status = 'rejected';

    return {
      unitId: item.id,
      messageId,
      kind: item.kind,
      status,
      label: item.label,
      patchSlice,
      baseline,
      draft,
      confirmedAt: status === 'confirmed' ? Date.now() : undefined,
    } satisfies AiStagingUnit;
  });
}
