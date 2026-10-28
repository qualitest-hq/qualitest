import request from '@/utils/request'

export interface StartSuiteRunPayload {
  testProjectId: string
  testSuiteId: string
  testProjectEnvId?: string
  stopOnNonPass?: boolean
  suiteNameSnapshot?: string
}

export function listTestSuiteRun(query: {
  testProjectId: string
  testSuiteId?: string
  pageNum?: number
  pageSize?: number
}) {
  return request({
    url: '/project/testSuiteRun/list',
    method: 'get',
    params: query,
  })
}

export function getTestSuiteRun(testSuiteRunId: string | number) {
  return request({
    url: `/project/testSuiteRun/${testSuiteRunId}`,
    method: 'get',
  })
}

export function startTestSuiteRun(data: StartSuiteRunPayload) {
  return request({
    url: '/project/testSuiteRun/start',
    method: 'post',
    data,
  })
}

export function bindTestSuiteRunItem(
  testSuiteRunId: string | number,
  testSuiteRunItemId: string | number,
  testFlowRunId: string,
) {
  return request({
    url: `/project/testSuiteRun/${testSuiteRunId}/items/${testSuiteRunItemId}/bindRun`,
    method: 'post',
    data: { testFlowRunId },
  })
}

export function finishTestSuiteRunItem(
  testSuiteRunId: string | number,
  testSuiteRunItemId: string | number,
  data: { status: string; errorMessage?: string; durationMs?: number },
) {
  return request({
    url: `/project/testSuiteRun/${testSuiteRunId}/items/${testSuiteRunItemId}/finish`,
    method: 'post',
    data,
  })
}

export function abortTestSuiteRun(testSuiteRunId: string | number) {
  return request({
    url: `/project/testSuiteRun/${testSuiteRunId}/abort`,
    method: 'post',
  })
}
