import request from '@/utils/request';

export interface TriggerTestFlowRunParams {
  testFlowId: string;
  testProjectEnvId?: string;
  runScenarioId?: string;
  triggerType?: string;
  [key: string]: unknown;
}

/** 后端 Run 头字段（列表项或详情 run 节点） */
export interface ApiTestFlowRun {
  testFlowRunId?: string | number;
  testFlowId?: string | number;
  testProjectEnvId?: string | number;
  runScenarioId?: string;
  status?: string;
  graphJsonSnapshot?: string;
  graphFingerprint?: string;
  flowSnapshot?: string;
  startedAt?: string;
  finishedAt?: string;
  durationMs?: string | number;
  errorCode?: string;
  errorMessage?: string;
  triggerType?: string;
  runExecutionState?: string;
  pausedAt?: string;
}

/** 快照栈单条 */
export interface SnapshotStackItem {
  nodeId?: string;
  snapshotId?: string;
}

/** paused Run 的补充信息 */
export interface RunPauseInfo {
  pauseReason?: string;
  pauseNodeId?: string;
  currentNodeId?: string;
  snapshotStack?: SnapshotStackItem[];
  pausedAt?: string;
  availableDecisions?: ResumeDecision[];
}

export type ResumeDecision = 'restoreAndRetry' | 'retryInPlace' | 'skip' | 'abort';

export interface ResumeTestFlowRunParams {
  decision: ResumeDecision;
  snapshotId?: string;
}

export interface ResumeRunResult {
  testFlowRunId?: string | number;
  status?: string;
  idempotent?: boolean;
  errorCode?: string;
  errorMessage?: string;
}

/** 后端单步记录行 */
export interface ApiTestFlowRunStep {
  testFlowRunStepId?: string | number;
  stepIndex?: string | number;
  nodeId?: string;
  nodeType?: string;
  nodeName?: string;
  status?: string;
  durationMs?: string | number;
  edgeId?: string;
  stepDetails?: string;
}

/** GET /project/testFlowRun/{id} 响应体 */
export interface ApiTestFlowRunDetail {
  run?: ApiTestFlowRun;
  steps?: ApiTestFlowRunStep[];
  pauseInfo?: RunPauseInfo | null;
}

export interface TestFlowRunListParams {
  testFlowId?: string;
  pageNum?: number;
  pageSize?: number;
  [key: string]: unknown;
}

export function triggerTestFlowRun(data: TriggerTestFlowRunParams) {
  return request({
    url: '/project/testFlowRun/trigger',
    method: 'post',
    data,
  });
}

export function getTestFlowRun(testFlowRunId: string | number) {
  return request({
    url: `/project/testFlowRun/${testFlowRunId}`,
    method: 'get',
  });
}

export function listTestFlowRun(query?: TestFlowRunListParams) {
  return request({
    url: '/project/testFlowRun/list',
    method: 'get',
    params: query,
  });
}

export function delTestFlowRun(testFlowRunIds: string | number) {
  return request({
    url: `/project/testFlowRun/${testFlowRunIds}`,
    method: 'delete',
  });
}

export function resumeTestFlowRun(
  testFlowRunId: string | number,
  data: ResumeTestFlowRunParams,
) {
  return request({
    url: `/project/testFlowRun/${testFlowRunId}/resume`,
    method: 'post',
    data,
  });
}
