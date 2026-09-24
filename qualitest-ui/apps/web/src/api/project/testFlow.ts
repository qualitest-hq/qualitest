import request from '@/utils/request';
import type { FlowDesignPatch } from '@/views/project/testFlow/types/aiDesignTypes';

export interface TestFlowListParams {
  /** 所属项目 */
  testProjectId?: string;
  /** 按名称模糊搜 */
  flowName?: string;
  /** 按目录过滤（含子孙） */
  flowGroupId?: string | number;
  /** 只查未分组 */
  ungroupedOnly?: boolean;
  pageNum?: number;
  pageSize?: number;
  [key: string]: unknown;
}

export interface TestFlowRecord {
  testFlowId?: string | number;
  testProjectId?: string | number;
  /** 所属目录；空为未分组 */
  flowGroupId?: string | number | null;
  /** 所属目录名称（列表联表） */
  flowGroupName?: string;
  /** 为 true 时清空所属目录（与 flowGroupId 同时传则以清空为准） */
  clearFlowGroup?: boolean;
  flowName?: string;
  flowDescription?: string;
  graphJson?: string;
  updateTime?: string;
  [key: string]: unknown;
}

export function listTestFlow(query?: TestFlowListParams) {
  return request({
    url: '/project/testFlow/list',
    method: 'get',
    params: query,
  });
}

export function getTestFlow(testFlowId: string | number) {
  return request({
    url: `/project/testFlow/${testFlowId}`,
    method: 'get',
  });
}

export function addTestFlow(data: Partial<TestFlowRecord>) {
  return request({
    url: '/project/testFlow',
    method: 'post',
    data,
  });
}

/** 保存测试流；可选带上写锁 token，服务端续期不换锁 */
export function updateTestFlow(data: Partial<TestFlowRecord>, leaseToken?: string | null) {
  const headers: Record<string, string> = {}
  if (leaseToken) {
    headers['X-Flow-Edit-Lease'] = leaseToken
  }
  return request({
    url: '/project/testFlow',
    method: 'put',
    data,
    headers,
  });
}

/** 清空测试流所属目录（变为未分组） */
export function clearTestFlowGroup(testFlowId: string | number) {
  return request({
    url: `/project/testFlow/${testFlowId}/flowGroup`,
    method: 'delete',
  });
}

/**
 * 占用测试流写锁。
 * 画布有未保存修改时调用；成功时 data.token 为本标签租约凭证。
 */
export function acquireFlowEditLease(testFlowId: string | number) {
  return request({
    url: `/project/testFlow/${testFlowId}/editLease`,
    method: 'post',
    headers: { repeatSubmit: false },
  })
}

/**
 * 写锁心跳续期。
 * 用已持有的 token 延长服务端租约存活时间；token 无效或已过期时失败。
 */
export function heartbeatFlowEditLease(testFlowId: string | number, token: string) {
  return request({
    url: `/project/testFlow/${testFlowId}/editLease/heartbeat`,
    method: 'post',
    data: { token },
    headers: { repeatSubmit: false },
  })
}

/**
 * 释放测试流写锁。
 * 仅当 token 与服务端当前租约一致时删除；保存变干净或离开画布时调用。
 */
export function releaseFlowEditLease(testFlowId: string | number, token: string) {
  return request({
    url: `/project/testFlow/${testFlowId}/editLease`,
    method: 'delete',
    params: { token },
    headers: { repeatSubmit: false },
  })
}

export function delTestFlow(testFlowIds: string | number) {
  return request({
    url: `/project/testFlow/${testFlowIds}`,
    method: 'delete',
  });
}

export function listSubflowTemplates() {
  return request({
    url: '/project/testFlow/subflowTemplates',
    method: 'get',
  });
}

export interface CreateSubflowFromTemplateParams {
  testProjectId: string | number;
  templateId: string;
  flowName?: string;
}

export function createFromSubflowTemplate(data: CreateSubflowFromTemplateParams) {
  return request({
    url: '/project/testFlow/fromSubflowTemplate',
    method: 'post',
    data,
  });
}

/** 单条语义健康告警（API 缺失 / 孤儿测值 / 抽取路径失效等） */
export interface FlowApiHealthWarning {
  code?: string;
  testFlowId?: string | number;
  flowName?: string;
  nodeId?: string;
  nodeName?: string;
  testProjectApiId?: string | number;
  message?: string;
  detail?: string;
}

/** 单条测试流的语义健康检查结果 */
export interface FlowApiHealthResult {
  testFlowId?: string | number;
  testProjectId?: string | number;
  flowName?: string;
  warnings?: FlowApiHealthWarning[];
}

/**
 * 按当前画布草稿 graphJson 预检 API 语义告警。
 * 只返回告警列表，不写 test_flow.api_health_*。
 * 关闭防重复提交：预检是只读查询，进页时可能短时间连发，不应弹「请勿重复提交」。
 *
 * @param testFlowId 测试流 id
 * @param graphJson 画布序列化结果（对象会自动 JSON.stringify）
 */
export function previewTestFlowApiHealth(
  testFlowId: string | number,
  graphJson: string | object,
) {
  const payload =
    typeof graphJson === 'string' ? graphJson : JSON.stringify(graphJson ?? {});
  return request({
    url: `/project/testFlow/${testFlowId}/apiHealth/preview`,
    method: 'post',
    data: { graphJson: payload },
    headers: { repeatSubmit: false },
  });
}

/** 「按项目鉴权刷新本流托管头」预览结果（不写库） */
export interface RefreshAuthHeadersResult {
  patch?: FlowDesignPatch;
  warnings?: string[];
  changedCount?: number;
  message?: string;
}

/**
 * 按项目鉴权配置刷新本流托管头，返回 Staging 可用的 updateNodes patch。
 * 不写 test_flow；前端确认后再保存。
 */
export async function refreshAuthHeaders(params: {
  testProjectId: string | number;
  graphJson: string | object;
}): Promise<RefreshAuthHeadersResult> {
  const graphJson =
    typeof params.graphJson === 'string'
      ? params.graphJson
      : JSON.stringify(params.graphJson ?? {});
  const res = await request({
    url: '/project/testFlow/refreshAuthHeaders',
    method: 'post',
    data: {
      testProjectId: params.testProjectId,
      graphJson,
    },
    headers: { repeatSubmit: false },
  });
  return (res.data ?? res) as RefreshAuthHeadersResult;
}
