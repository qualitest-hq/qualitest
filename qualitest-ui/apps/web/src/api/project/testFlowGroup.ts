import request from '@/utils/request'

/** 测试流目录节点（平铺或树） */
export interface TestFlowGroupRecord {
  flowGroupId?: string | number
  testProjectId?: string | number
  parentId?: string | number
  ancestors?: string
  groupName?: string
  sortNum?: number
  label?: string
  children?: TestFlowGroupRecord[]
  [key: string]: unknown
}

/** 查询测试流分组树 */
export function getTestFlowGroupTree(testProjectId: string | number) {
  return request({
    url: '/project/testFlowGroup/tree',
    method: 'get',
    params: { testProjectId },
  })
}

/** 新增测试流分组 */
export function addTestFlowGroup(data: Partial<TestFlowGroupRecord>) {
  return request({
    url: '/project/testFlowGroup',
    method: 'post',
    data,
  })
}

/** 修改测试流分组 */
export function updateTestFlowGroup(data: Partial<TestFlowGroupRecord>) {
  return request({
    url: '/project/testFlowGroup',
    method: 'put',
    data,
  })
}

/** 删除测试流分组 */
export function delTestFlowGroup(flowGroupId: string | number) {
  return request({
    url: `/project/testFlowGroup/${flowGroupId}`,
    method: 'delete',
  })
}
