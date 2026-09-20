/**
 * test_flow_run API 响应 → 画布运行库 RunRecord 的映射层。
 * 负责解析 step_details JSON 字符串、组装列表摘要与详情完整步骤。
 */
import type {
  ApiTestFlowRun,
  ApiTestFlowRunDetail,
  ApiTestFlowRunStep,
} from '@/api/project/testFlowRun'
import type { RunRecord, RunStepDetail } from '../stores/runLibraryStore'

/** 后端 Run 头字段（列表项或详情 run 节点） — 再导出供外部使用 */
export type { ApiTestFlowRun, ApiTestFlowRunDetail, ApiTestFlowRunStep }

/** 安全解析 JSON 字符串为对象，失败返回空对象 */
function parseJsonObject(raw: string | null | undefined): Record<string, unknown> {
  if (!raw) return {}
  try {
    const parsed = JSON.parse(raw)
    return typeof parsed === 'object' && parsed !== null ? (parsed as Record<string, unknown>) : {}
  } catch {
    return {}
  }
}

/**
 * 从触发时固化的 graph 快照中解析运行场景名称。
 * 列表与详情共用，避免列表只显示场景 ID。
 */
export function resolveScenarioNameFromSnapshot(
  graphJsonSnapshot: string | null | undefined,
  runScenarioId: string | null | undefined,
): string | undefined {
  if (!graphJsonSnapshot || !runScenarioId) return undefined
  try {
    const graph = JSON.parse(graphJsonSnapshot)
    const scenarios = graph?.meta?.scenarios
    if (!Array.isArray(scenarios)) return undefined
    const sc = scenarios.find(
      (s: { id?: string | number }) => s != null && String(s.id) === String(runScenarioId),
    )
    const name = sc?.name
    return typeof name === 'string' && name.trim() ? name.trim() : undefined
  } catch {
    return undefined
  }
}

/** 触发来源短标签 */
function resolveTriggerLabel(run: ApiTestFlowRun): string {
  if (run.triggerType === 'manual') return '手动'
  if (run.triggerType === 'ai') return 'ai'
  return run.triggerType?.trim() || '运行'
}

/**
 * 生成运行库列表项展示用的环境/场景标签。
 * 优先用快照中的场景名；没有快照时才退回「场景 {id}」。
 */
function resolveEnvLabel(run: ApiTestFlowRun, scenarioName?: string): string {
  const scenario =
    scenarioName
    || (run.runScenarioId ? `场景 ${run.runScenarioId}` : '默认场景')
  return `${resolveTriggerLabel(run)} · ${scenario}`
}

/**
 * 从 step_details 提取 RunStepDetail 扩展字段。
 * 索引列（nodeId/status 等）由 mapStep 从 API 行字段单独映射。
 */
function parseStepDetails(
  step: ApiTestFlowRunStep,
): Pick<
  RunStepDetail,
  'http' | 'assert' | 'extracts' | 'branchTaken' | 'assigns' | 'script' | 'flowAfter' | 'error' | 'scenarioLoaded'
> {
  const details = parseJsonObject(step.stepDetails)
  const out: Pick<
    RunStepDetail,
    'http' | 'assert' | 'extracts' | 'branchTaken' | 'assigns' | 'script' | 'flowAfter' | 'error' | 'scenarioLoaded'
  > = {}

  if (details.http) out.http = details.http as RunStepDetail['http']
  if (details.assert) out.assert = details.assert as RunStepDetail['assert']
  if (details.extracts) out.extracts = details.extracts as RunStepDetail['extracts']
  if (details.branchTaken) out.branchTaken = details.branchTaken as RunStepDetail['branchTaken']
  if (details.assigns) out.assigns = details.assigns as RunStepDetail['assigns']
  if (details.script) out.script = details.script as RunStepDetail['script']
  if (details.flowAfter) out.flowAfter = details.flowAfter as Record<string, unknown>
  if (details.error) out.error = details.error as RunStepDetail['error']
  if (details.scenarioLoaded) out.scenarioLoaded = details.scenarioLoaded as Record<string, unknown>

  return out
}

/** 单步 API 行 → RunStepDetail */
function mapStep(step: ApiTestFlowRunStep): RunStepDetail {
  const extras = parseStepDetails(step)
  return {
    nodeId: step.nodeId ?? '',
    nodeType: step.nodeType ?? '',
    nodeName: step.nodeName ?? '',
    edgeId: step.edgeId ?? undefined,
    status: (step.status as RunStepDetail['status']) ?? 'passed',
    durationMs: Number(step.durationMs) || 0,
    flowAfter: extras.flowAfter ?? {},
    ...extras,
  }
}

/**
 * 列表项映射为摘要 RunRecord。
 * steps 留空，选中记录时由 store 懒加载详情补全。
 * 场景名从本条 graphJsonSnapshot 解析，避免列表标题露出雪花 id。
 */
export function mapRunListItem(run: ApiTestFlowRun): RunRecord {
  const id = String(run.testFlowRunId ?? '')
  const scenarioName = resolveScenarioNameFromSnapshot(run.graphJsonSnapshot, run.runScenarioId)
  return {
    id,
    testFlowRunId: id,
    source: 'server',
    status: (run.status as RunRecord['status']) ?? 'passed',
    startedAt: run.startedAt ?? '',
    finishedAt: run.finishedAt ?? null,
    durationMs: Number(run.durationMs) || 0,
    graphFingerprint: run.graphFingerprint ?? '',
    graphJsonSnapshot: run.graphJsonSnapshot,
    envLabel: resolveEnvLabel(run, scenarioName),
    flowSnapshot: parseJsonObject(run.flowSnapshot),
    steps: [],
    scenarioId: run.runScenarioId,
    scenarioName,
    triggerType: run.triggerType,
    errorCode: run.errorCode,
    errorMessage: run.errorMessage,
    pausedAt: run.pausedAt,
  }
}

/**
 * 详情响应映射为完整 RunRecord（含按 stepIndex 排序的 steps）。
 * 场景名称优先从 graphJsonSnapshot.meta.scenarios 解析。
 */
export function mapRunDetail(detail: ApiTestFlowRunDetail): RunRecord | null {
  const run = detail.run
  if (!run?.testFlowRunId) return null

  const base = mapRunListItem(run)
  const steps = (detail.steps ?? [])
    .slice()
    .sort((a, b) => Number(a.stepIndex) - Number(b.stepIndex))
    .map(mapStep)

  // 首步 scenarioLoaded 可补全快照缺失时的场景名
  let scenarioName = base.scenarioName
  if (!scenarioName) {
    const loaded = steps.find((s) => s.scenarioLoaded?.scenarioName)?.scenarioLoaded
    if (loaded?.scenarioName) {
      scenarioName = loaded.scenarioName
    }
  }

  return {
    ...base,
    steps,
    scenarioName,
    envLabel: scenarioName ? resolveEnvLabel(run, scenarioName) : base.envLabel,
    pauseInfo: detail.pauseInfo ?? undefined,
    pausedAt: run.pausedAt ?? detail.pauseInfo?.pausedAt,
  }
}
