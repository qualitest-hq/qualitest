import request from '@/utils/request';

export interface TriggerTestFlowRunParams {
  testFlowId: string;
  testProjectEnvId?: string;
  runScenarioId?: string;
  /** 触发来源：manual / ai / ci / schedule 等 */
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
  /** 触发来源：manual / ai / ci / schedule 等 */
  triggerType?: string;
  runExecutionState?: string;
  pausedAt?: string;
}

/** 快照栈单条 */
export interface SnapshotStackItem {
  nodeId?: string;
  snapshotId?: string;
}

/**
 * paused Run 详情中的暂停信息。
 * await_input 时带 prompt、fields，且 availableDecisions 仅为 continueWithInput / abort。
 */
export interface RunPauseInfo {
  /** node_failure / snapshot_failure / await_input */
  pauseReason?: string;
  pauseNodeId?: string;
  currentNodeId?: string;
  snapshotStack?: SnapshotStackItem[];
  pausedAt?: string;
  availableDecisions?: ResumeDecision[];
  /** 等待人工输入时的提示文案 */
  prompt?: string;
  /** 等待人工输入时的字段定义，用于渲染表单 */
  fields?: InputPauseField[];
}

/** 暂停面板上的单个输入项定义 */
export interface InputPauseField {
  /** 写入 flow 的变量名 */
  name: string;
  label?: string;
  /** text / textarea / password / number / boolean / select / multiselect / date / datetime */
  type?: string;
  required?: boolean;
  placeholder?: string;
  defaultValue?: unknown;
  options?: Array<{ label?: string; value?: string }>;
}

/** 续跑决策取值 */
export type ResumeDecision =
  | 'restoreAndRetry'
  | 'retryInPlace'
  | 'skip'
  | 'abort'
  | 'continueWithInput';

/** POST resume 请求体 */
export interface ResumeTestFlowRunParams {
  decision: ResumeDecision;
  /** restoreAndRetry 时的快照 id */
  snapshotId?: string;
  /** continueWithInput 时提交的字段值 */
  inputs?: Record<string, unknown>;
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

/** 触发正式 Run；立刻返回 runId，图在后台执行 */
export function triggerTestFlowRun(data: TriggerTestFlowRunParams) {
  return request({
    url: '/project/testFlowRun/trigger',
    method: 'post',
    data,
  });
}

/** 查询单次 Run 详情（执行中也可轮询，步骤随执行变长） */
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
