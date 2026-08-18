import request from '@/utils/request'

// 查询测试项目列表
export function listTestProject(query) {
  return request({
    url: '/project/testProject/list',
    method: 'get',
    params: query
  })
}

// 查询测试项目详细
export function getTestProject(testProjectId) {
  return request({
    url: '/project/testProject/' + testProjectId,
    method: 'get'
  })
}

// 新增测试项目
export function addTestProject(data) {
  return request({
    url: '/project/testProject',
    method: 'post',
    data: data
  })
}

// 修改测试项目
export function updateTestProject(data) {
  return request({
    url: '/project/testProject',
    method: 'put',
    data: data
  })
}

// 删除测试项目
export function delTestProject(testProjectId) {
  return request({
    url: '/project/testProject/' + testProjectId,
    method: 'delete'
  })
}

// 我的项目信息
export function myProjectContext(testProjectId) {
  return request({
    url: '/project/testProject/myProjectContext',
    method: 'get',
    params: { testProjectId }
  })
}

/** 从模板库追加鉴权 Profile，并种子尚未存在的预制接口 */
export function applyAuthTemplates(testProjectId, templateIds) {
  return request({
    url: `/project/testProject/${testProjectId}/applyAuthTemplates`,
    method: 'post',
    data: { templateIds },
  })
}
