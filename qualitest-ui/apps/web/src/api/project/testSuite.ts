import request from '@/utils/request'

export interface TestSuiteItemSave {
  testFlowId: string
  runScenarioId?: string
  sortNum?: number
}

export interface TestSuiteSavePayload {
  testSuiteId?: string
  testProjectId: string
  suiteName: string
  suiteDescription?: string
  stopOnNonPass?: boolean
  items: TestSuiteItemSave[]
}

export function listTestSuite(query: { testProjectId: string; suiteName?: string; pageNum?: number; pageSize?: number }) {
  return request({
    url: '/project/testSuite/list',
    method: 'get',
    params: query,
  })
}

export function getTestSuite(testSuiteId: string | number) {
  return request({
    url: `/project/testSuite/${testSuiteId}`,
    method: 'get',
  })
}

export function addTestSuite(data: TestSuiteSavePayload) {
  return request({
    url: '/project/testSuite',
    method: 'post',
    data,
  })
}

export function updateTestSuite(data: TestSuiteSavePayload) {
  return request({
    url: '/project/testSuite',
    method: 'put',
    data,
  })
}

export function delTestSuite(testSuiteIds: string | number) {
  return request({
    url: `/project/testSuite/${testSuiteIds}`,
    method: 'delete',
  })
}
