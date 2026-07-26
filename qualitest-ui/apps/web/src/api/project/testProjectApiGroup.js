import request from '@/utils/request'

// 查询测试项目API分组列表
export function listTestProjectApiGroup(query) {
  return request({
    url: '/project/testProjectApiGroup/list',
    method: 'get',
    params: query
  })
}

// 查询测试项目API分组详细
export function getTestProjectApiGroup(apiGroupId) {
  return request({
    url: '/project/testProjectApiGroup/' + apiGroupId,
    method: 'get'
  })
}

// 新增测试项目API分组
export function addTestProjectApiGroup(data) {
  return request({
    url: '/project/testProjectApiGroup',
    method: 'post',
    data: data
  })
}

// 修改测试项目API分组
export function updateTestProjectApiGroup(data) {
  return request({
    url: '/project/testProjectApiGroup',
    method: 'put',
    data: data
  })
}

// 删除测试项目API分组
export function delTestProjectApiGroup(apiGroupId) {
  return request({
    url: '/project/testProjectApiGroup/' + apiGroupId,
    method: 'delete'
  })
}
