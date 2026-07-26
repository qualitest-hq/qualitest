import request from '@/utils/request'

// 查询测试项目环境列表
export function listTestProjectEnv(query) {
  return request({
    url: '/project/testProjectEnv/list',
    method: 'get',
    params: query
  })
}

// 查询测试项目环境详细
export function getTestProjectEnv(testProjectEnvId) {
  return request({
    url: '/project/testProjectEnv/' + testProjectEnvId,
    method: 'get'
  })
}

// 新增测试项目环境
export function addTestProjectEnv(data) {
  return request({
    url: '/project/testProjectEnv',
    method: 'post',
    data: data
  })
}

// 修改测试项目环境
export function updateTestProjectEnv(data) {
  return request({
    url: '/project/testProjectEnv',
    method: 'put',
    data: data
  })
}

// 拖动排序（批量更新 sort_num）
export function reorderTestProjectEnv(data) {
  return request({
    url: '/project/testProjectEnv/reorder',
    method: 'put',
    data: data
  })
}

// 删除测试项目环境
export function delTestProjectEnv(testProjectEnvId) {
  return request({
    url: '/project/testProjectEnv/' + testProjectEnvId,
    method: 'delete'
  })
}
