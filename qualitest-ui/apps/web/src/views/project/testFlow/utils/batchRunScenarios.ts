/**
 * 从测试流列表行解析批量跑场景树节点。
 */

export interface BatchFlowScenarioNode {
  scenarioId: string;
  scenarioName: string;
  /** 是否为图 meta 活动场景 */
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

  const meta = (root.meta ?? {}) as Record<string, unknown>;
  const scenarios = Array.isArray(meta.scenarios) ? (meta.scenarios as GraphScenarioLike[]) : [];
  const activeId = String(activeScenarioId ?? meta.activeScenarioId ?? '').trim();

  const out: BatchFlowScenarioNode[] = [];
  for (const sc of scenarios) {
    const scenarioId = String(sc?.id ?? '').trim();
    if (!scenarioId) continue;
    const scenarioName = String(sc?.name ?? '').trim() || scenarioId;
    out.push({
      scenarioId,
      scenarioName,
      active: activeId ? scenarioId === activeId : out.length === 0,
    });
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
  runScenarioId?: string;
  scenarioName: string;
  /** 树勾选键：flowId 或 flowId::scenarioId */
  key: string;
}

/** 场景勾选键 */
export function scenarioCheckKey(testFlowId: string, scenarioId: string): string {
  return `${testFlowId}::${scenarioId}`;
}

/** 无场景流的勾选键（仅流） */
export function flowOnlyCheckKey(testFlowId: string): string {
  return testFlowId;
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
      const key = scenarioCheckKey(flow.testFlowId, sc.scenarioId);
      if (!checked.has(key)) continue;
      queue.push({
        testFlowId: flow.testFlowId,
        flowName: flow.flowName,
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
      keys.push(scenarioCheckKey(flow.testFlowId, sc.scenarioId));
    }
  }
  return keys;
}
