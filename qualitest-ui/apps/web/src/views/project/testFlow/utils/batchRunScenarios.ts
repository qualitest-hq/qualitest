/**
 * 从测试流列表行解析批量跑场景树节点。
 * 场景定义读取各图 scenario-entry 节点的 data.scenarios，不从图元数据取。
 */

export interface BatchFlowScenarioNode {
  scenarioId: string;
  scenarioName: string;
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
  const entry = nodes.find(
    (n) => n && typeof n === 'object' && (n as { type?: string }).type === 'scenario-entry',
  ) as { data?: { scenarios?: unknown } } | undefined;
  const entryData = entry?.data;
  const scenarios = Array.isArray(entryData?.scenarios)
    ? (entryData.scenarios as GraphScenarioLike[])
    : [];
  const activeId = String(activeScenarioId ?? '').trim();

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
