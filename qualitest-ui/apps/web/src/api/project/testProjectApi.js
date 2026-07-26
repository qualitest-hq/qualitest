import request from '@/utils/request'

// 查询测试项目API列表
export function listTestProjectApi(query) {
  return request({
    url: '/project/testProjectApi/list',
    method: 'get',
    params: query
  })
}

/** 侧边栏树 */
export function getTestProjectApiTree(query) {
  return request({
    url: '/project/testProjectApi/apiTree',
    method: 'get',
    params: query
  })
}

// 查询测试项目API详细
export function getTestProjectApi(testProjectApiId) {
  return request({
    url: '/project/testProjectApi/' + testProjectApiId,
    method: 'get'
  })
}

// 新增测试项目API
export function addTestProjectApi(data) {
  return request({
    url: '/project/testProjectApi',
    method: 'post',
    data: data
  })
}

// 修改测试项目API
export function updateTestProjectApi(data) {
  return request({
    url: '/project/testProjectApi',
    method: 'put',
    data: data
  })
}

// 删除测试项目API
export function delTestProjectApi(testProjectApiId) {
  return request({
    url: '/project/testProjectApi/' + testProjectApiId,
    method: 'delete'
  })
}

/** 列出绑定了该 API 的测试流 HTTP 节点（反向引用） */
export function listApiFlowReferences(testProjectApiId) {
  return request({
    url: '/project/testProjectApi/' + testProjectApiId + '/flowReferences',
    method: 'get'
  })
}

/** 列出引用并返回命中节点上的语义健康告警（孤儿测值、抽取路径等） */
export function diagnoseApiImpacts(testProjectApiId) {
  return request({
    url: '/project/testProjectApi/' + testProjectApiId + '/diagnoseImpacts',
    method: 'get'
  })
}
