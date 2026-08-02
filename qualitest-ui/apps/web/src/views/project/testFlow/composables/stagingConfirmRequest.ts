/**
 * Staging 单元 confirm 的服务端请求、画布 hash 守卫与落盘。
 *
 * 从画布读取最新 draft，请求服务端合并单单元，校验通过后将 graph_json 写回画布。
 * 不在此处入撤销栈，由调用方在 Staging 状态更新后再 pushHistory。
 */
import { fromGraphJson } from '../graphAdapter';
import { useAiStagingStore } from '../stores/aiStagingStore';
import { useFlowCanvasStore } from '../stores/flowCanvasStore';
import {
  computeBaseGraphHash,
  shouldBlockConfirmByBaseGraphHash,
} from '../utils/computeBaseGraphHash';
import { requestConfirmFlowDesignUnit } from '../utils/confirmFlowDesignUnit';
import { buildFlowGraphInput } from '../utils/stagingGraphInput';
import {
  buildDraftFromCanvasEdge,
  buildDraftFromCanvasNode,
} from './useAiStagingCanvas';
import { buildDraftFromCanvasScenario } from './useAiStagingScenario';
import { objectIdFromUnitId } from '../utils/stagingUnitIds';
import { rejectedStagingUnitIds } from '../utils/stagingAcceptance';

export type ConfirmRequestResult = Awaited<ReturnType<typeof requestConfirmFlowDesignUnit>>;

function buildConfirmGraphInput(
  unitId: string,
  stagingStore: ReturnType<typeof useAiStagingStore>,
  canvasStore: ReturnType<typeof useFlowCanvasStore>,
) {
  return {
    ...buildFlowGraphInput(canvasStore),
    stagingFilter: stagingStore.buildConfirmPersistFilter(unitId),
  };
}

/**
 * 从画布当前状态提取单元 draft，供 confirm 请求携带用户编辑后的现值。
 * 画布上找不到对象时回退到 store 里缓存的 draft。
 */
export function resolveDraftOverride(
  unitId: string,
  stagingStore: ReturnType<typeof useAiStagingStore>,
  canvasStore: ReturnType<typeof useFlowCanvasStore>,
): Record<string, unknown> | undefined {
  const unit = stagingStore.getUnit(unitId);
  if (!unit) return undefined;

  if (unit.kind === 'addNode' || unit.kind === 'updateNode') {
    const nodeId = objectIdFromUnitId(unitId);
    const node = canvasStore.nodes.find((n) => n.id === nodeId);
    if (node) return buildDraftFromCanvasNode(node);
    return unit.draft;
  }

  if (unit.kind === 'addEdge' || unit.kind === 'updateEdge') {
    const edgeId = objectIdFromUnitId(unitId);
    const edge = canvasStore.edges.find((e) => e.id === edgeId);
    if (edge) return buildDraftFromCanvasEdge(edge);
    return unit.draft;
  }

  if (unit.kind === 'addScenario' || unit.kind === 'updateScenario') {
    const scenarioId = objectIdFromUnitId(unitId);
    const scenario = canvasStore.runConfig.scenarios.find((s) => s.id === scenarioId);
    if (scenario) return buildDraftFromCanvasScenario(scenario);
    return unit.draft;
  }

  if (unit.kind === 'setActiveScenario') {
    return { activeScenarioId: canvasStore.runConfig.activeScenarioId };
  }

  return unit.draft;
}

function listRejectedUnitIdsForConfirm(
  unitId: string,
  stagingStore: ReturnType<typeof useAiStagingStore>,
): string[] {
  const unit = stagingStore.getUnit(unitId);
  if (!unit) return [];
  return [...rejectedStagingUnitIds(unit.messageId)];
}

/** 发起一次 confirm 请求，并记录请求发出时的画布 hash 供后续比对 */
export async function requestConfirmOnce(
  unitId: string,
  patch: NonNullable<ReturnType<typeof useAiStagingStore>['getPatchForMessage']> extends infer P ? P : never,
  projectId: string,
  stagingStore: ReturnType<typeof useAiStagingStore>,
  canvasStore: ReturnType<typeof useFlowCanvasStore>,
) {
  const graphInput = buildConfirmGraphInput(unitId, stagingStore, canvasStore);
  const requestBaseHash = await computeBaseGraphHash(graphInput);
  const result = await requestConfirmFlowDesignUnit({
    ...graphInput,
    testProjectId: projectId,
    patch,
    unitId,
    draftOverride: resolveDraftOverride(unitId, stagingStore, canvasStore),
    confirmedUnitIds: stagingStore.listConfirmedUnitIds(),
    rejectedUnitIds: listRejectedUnitIdsForConfirm(unitId, stagingStore),
  });
  return { result, requestBaseHash, graphInput };
}

/**
 * 确认落盘时保留仍 pending 的 Staging 增项。
 *
 * 单单元 confirm 的 graph_json 不含其它未确认 addNode/addEdge；若整表替换画布，
 * 未确认节点会在 inFlight 期间短暂消失，配合视口聚焦会看起来像「节点平移」。
 */
export function mergePendingStagingIntoConfirmedGraph(
  applied: ReturnType<typeof fromGraphJson>,
  canvasStore: ReturnType<typeof useFlowCanvasStore>,
  stagingStore: ReturnType<typeof useAiStagingStore>,
) {
  const confirmedNodeIds = new Set(applied.nodes.map((n) => n.id));
  const confirmedEdgeIds = new Set(applied.edges.map((e) => e.id));

  const pendingNodeIds = new Set<string>();
  const pendingEdgeIds = new Set<string>();
  for (const unit of Object.values(stagingStore.unitsById)) {
    if (unit.status !== 'pending') continue;
    if (unit.kind === 'addNode') {
      pendingNodeIds.add(objectIdFromUnitId(unit.unitId));
    } else if (unit.kind === 'addEdge') {
      pendingEdgeIds.add(objectIdFromUnitId(unit.unitId));
    }
  }

  const preservedNodes = canvasStore.nodes.filter(
    (n) => pendingNodeIds.has(n.id) && !confirmedNodeIds.has(n.id),
  );
  const preservedEdges = canvasStore.edges.filter(
    (e) => pendingEdgeIds.has(e.id) && !confirmedEdgeIds.has(e.id),
  );

  return {
    nodes: [...applied.nodes, ...preservedNodes],
    edges: [...applied.edges, ...preservedEdges],
  };
}

/** 将服务端返回的 graph_json 写入画布 store，保留用户当前视口与未确认 Staging */
export async function applyConfirmedGraph(
  graphJson: NonNullable<ConfirmRequestResult['graphJson']>,
  canvasStore: ReturnType<typeof useFlowCanvasStore>,
  stagingStore: ReturnType<typeof useAiStagingStore> = useAiStagingStore(),
) {
  // 进入灌入模式：暂停脏标记，避免中间态触发「未保存」
  canvasStore.beginCanvasHydration();
  const applied = fromGraphJson(graphJson);
  const merged = mergePendingStagingIntoConfirmedGraph(applied, canvasStore, stagingStore);
  // 先写 nodes，边暂存 pendingEdges，待节点就绪后由灌入逻辑写入
  canvasStore.setPendingEdges(merged.edges);
  canvasStore.nodes = merged.nodes;
  canvasStore.edges = [];
  canvasStore.runConfig = applied.runConfig;
  canvasStore.flowOutputs = applied.flowOutputs;
  // 不写入 applied.viewport：确认落盘不应改变用户当前视角
  canvasStore.flushPendingEdges();
  canvasStore.bumpStagingEdgeFlushToken();
  canvasStore.endCanvasHydration();
  canvasStore.markDirty();
}

/**
 * 落盘前校验画布是否在请求期间被用户改动。
 * hash 不一致时自动重试一次请求；仍不一致则放弃落盘并回调 onHashConflict。
 */
export async function applyConfirmResultWithHashGuard(
  unitId: string,
  result: ConfirmRequestResult,
  requestBaseHash: string,
  patch: NonNullable<ReturnType<typeof useAiStagingStore>['getPatchForMessage']> extends infer P ? P : never,
  projectId: string,
  stagingStore: ReturnType<typeof useAiStagingStore>,
  canvasStore: ReturnType<typeof useFlowCanvasStore>,
  onHashConflict: () => void,
): Promise<boolean> {
  if (!result.validation.ok || !result.graphJson) {
    return false;
  }

  // 仅用请求发出时客户端计算的 hash 做并发编辑检测；服务端 Fastjson 序列化与前端 stableStringify 可能不一致。
  let expectedHash = requestBaseHash;
  let currentHash = await computeBaseGraphHash(buildConfirmGraphInput(unitId, stagingStore, canvasStore));

  if (shouldBlockConfirmByBaseGraphHash(expectedHash, currentHash)) {
    const retry = await requestConfirmOnce(unitId, patch, projectId, stagingStore, canvasStore);
    if (!retry.result.validation.ok || !retry.result.graphJson) {
      return false;
    }
    expectedHash = retry.requestBaseHash;
    currentHash = await computeBaseGraphHash(buildConfirmGraphInput(unitId, stagingStore, canvasStore));
    if (shouldBlockConfirmByBaseGraphHash(expectedHash, currentHash)) {
      onHashConflict();
      return false;
    }
    await applyConfirmedGraph(retry.result.graphJson, canvasStore, stagingStore);
    return true;
  }

  await applyConfirmedGraph(result.graphJson, canvasStore, stagingStore);
  return true;
}
