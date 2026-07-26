import request from '@/utils/request';

export interface TestFlowListParams {
  testProjectId?: string;
  pageNum?: number;
  pageSize?: number;
  [key: string]: unknown;
}

export interface TestFlowRecord {
  testFlowId?: string | number;
  testProjectId?: string | number;
  flowName?: string;
  flowDescription?: string;
  graphJson?: string;
  updateTime?: string;
  upgradeAvailable?: boolean;
  upgradeFromVersion?: number;
  upgradeToVersion?: number;
  upgradeSummary?: string[];
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

export function updateTestFlow(data: Partial<TestFlowRecord>) {
  return request({
    url: '/project/testFlow',
    method: 'put',
    data,
  });
}

export function upgradeTestFlowGraph(testFlowId: string | number) {
  return request({
    url: '/project/testFlow/upgradeGraph',
    method: 'post',
    data: { testFlowId },
  });
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
