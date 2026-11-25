/**
 * 从测试流列表行解析批量跑场景树节点。
 * 场景定义读取各图 scenario-entry 节点的 data.scenarios，并保留所属入口 id。
 */

export interface BatchFlowScenarioNode {
  scenarioId: string;
  scenarioName: string;
  /** 所属 scenario-entry 节点 id */
  entryNodeId: string;
  /** 是否为当前勾选的活动场景（未指定时默认首条） */
  active: boolean;
}

export interface BatchFlowTreeNode {
  testFlowId: string;
  flowName: string;
  scenarios: BatchFlowScenarioNode[];
}

type GraphScenarioLike = {
  id?: unknown;
  name?: unknown;
};

/**
 * 解析单条流的 graphJson，得到场景列表；无场景时返回空数组（调用方按「默认一项」处理）。
 * 多入口时逐入口展开，同 scenarioId 可在不同入口各出现一次（键含 entryNodeId）。
 */
export function parseFlowScenariosFromGraphJson(
  graphJson: unknown,
  activeScenarioId?: unknown,
): BatchFlowScenarioNode[] {
  let root: Record<string, unknown> | null = null;
  if (typeof graphJson === 'string') {
    const raw = graphJson.trim();
    if (!raw) return [];
    try {
      root = JSON.parse(raw) as Record<string, unknown>;
    } catch {
      return [];
    }
  } else if (graphJson && typeof graphJson === 'object') {
    root = graphJson as Record<string, unknown>;
  } else {
    return [];
  }

  const nodes = Array.isArray(root.nodes) ? root.nodes : [];
  const activeId = String(activeScenarioId ?? '').trim();
  const out: BatchFlowScenarioNode[] = [];

  for (const n of nodes) {
    if (!n || typeof n !== 'object') continue;
    if ((n as { type?: string }).type !== 'scenario-entry') continue;
    const entryNodeId = String((n as { id?: unknown }).id ?? '').trim();
    if (!entryNodeId) continue;
    const data = (n as { data?: { scenarios?: unknown } }).data;
    const scenarios = Array.isArray(data?.scenarios) ? (data.scenarios as GraphScenarioLike[]) : [];
    for (const sc of scenarios) {
      const scenarioId = String(sc?.id ?? '').trim();
      if (!scenarioId) continue;
      const scenarioName = String(sc?.name ?? '').trim() || scenarioId;
      out.push({
        scenarioId,
        scenarioName,
        entryNodeId,
        active: activeId ? scenarioId === activeId : out.length === 0,
      });
    }
  }
  if (out.length && !out.some((s) => s.active)) {
    out[0].active = true;
  }
  return out;
}

/**
 * 由列表选中行构建父子树；无场景的流 scenarios 为空。
 */
export function buildBatchFlowTree(
  rows: Array<{ testFlowId?: unknown; flowName?: unknown; graphJson?: unknown }>,
): BatchFlowTreeNode[] {
  return rows
    .map((row) => {
      const testFlowId = String(row?.testFlowId ?? '').trim();
      if (!testFlowId) return null;
      const flowName = String(row?.flowName ?? '').trim() || testFlowId;
      return {
        testFlowId,
        flowName,
        scenarios: parseFlowScenariosFromGraphJson(row?.graphJson),
      } satisfies BatchFlowTreeNode;
    })
    .filter((n): n is BatchFlowTreeNode => n != null);
}

export interface BatchQueueItem {
  testFlowId: string;
  flowName: string;
  entryNodeId?: string;
  runScenarioId?: string;
  scenarioName: string;
  /** 树勾选键：flowId 或 flowId::entryNodeId::scenarioId */
  key: string;
}

/** 场景勾选键（含入口归属） */
export function scenarioCheckKey(
  testFlowId: string,
  entryNodeId: string,
  scenarioId: string,
): string {
  return `${testFlowId}::${entryNodeId}::${scenarioId}`;
}

/** 无场景流的勾选键（仅流） */
export function flowOnlyCheckKey(testFlowId: string): string {
  return testFlowId;
}

/**
 * 在树中按流 + 场景 id 找回勾选键（兼容旧套件项未存 entryNodeId）。
 */
export function findScenarioCheckKey(
  tree: BatchFlowTreeNode[],
  testFlowId: string,
  runScenarioId: string,
  entryNodeId?: string | null,
): string | null {
  const flowId = String(testFlowId ?? '').trim();
  const scenarioId = String(runScenarioId ?? '').trim();
  if (!flowId || !scenarioId) return null;
  const preferredEntry = String(entryNodeId ?? '').trim();
  for (const flow of tree) {
    if (flow.testFlowId !== flowId) continue;
    for (const sc of flow.scenarios) {
      if (sc.scenarioId !== scenarioId) continue;
      if (preferredEntry && sc.entryNodeId !== preferredEntry) continue;
      return scenarioCheckKey(flow.testFlowId, sc.entryNodeId, sc.scenarioId);
    }
  }
  return null;
}

/**
 * 根据树与勾选集合生成串行队列（保树序）。
 */
export function buildBatchQueueFromChecks(
  tree: BatchFlowTreeNode[],
  checkedKeys: Iterable<string>,
): BatchQueueItem[] {
  const checked = new Set(Array.from(checkedKeys).map(String));
  const queue: BatchQueueItem[] = [];
  for (const flow of tree) {
    if (!flow.scenarios.length) {
      if (checked.has(flowOnlyCheckKey(flow.testFlowId))) {
        queue.push({
          testFlowId: flow.testFlowId,
          flowName: flow.flowName,
          scenarioName: '默认场景',
          key: flowOnlyCheckKey(flow.testFlowId),
        });
      }
      continue;
    }
    for (const sc of flow.scenarios) {
      const key = scenarioCheckKey(flow.testFlowId, sc.entryNodeId, sc.scenarioId);
      if (!checked.has(key)) continue;
      queue.push({
        testFlowId: flow.testFlowId,
        flowName: flow.flowName,
        entryNodeId: sc.entryNodeId,
        runScenarioId: sc.scenarioId,
        scenarioName: sc.scenarioName,
        key,
      });
    }
  }
  return queue;
}

/** 默认勾选：每流全部场景；无场景则勾流本身 */
export function defaultCheckedKeysForTree(tree: BatchFlowTreeNode[]): string[] {
  const keys: string[] = [];
  for (const flow of tree) {
    if (!flow.scenarios.length) {
      keys.push(flowOnlyCheckKey(flow.testFlowId));
      continue;
    }
    for (const sc of flow.scenarios) {
      keys.push(scenarioCheckKey(flow.testFlowId, sc.entryNodeId, sc.scenarioId));
    }
  }
  return keys;
}
