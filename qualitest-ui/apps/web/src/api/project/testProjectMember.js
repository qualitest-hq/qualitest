import request from '@/utils/request'

// 查询测试项目成员列表
export function listTestProjectMember(query) {
  return request({
    url: '/project/testProjectMember/list',
    method: 'get',
    params: query
  })
}

// 可加入项目的用户分页列表
export function listSelectableUsersForProject(query) {
  return request({
    url: '/project/testProjectMember/selectableUsers',
    method: 'get',
    params: query
  })
}


// 查询测试项目成员详细
export function getTestProjectMember(testProjectMemberId) {
  return request({
    url: '/project/testProjectMember/' + testProjectMemberId,
    method: 'get'
  })
}

// 新增测试项目成员
export function addTestProjectMember(data) {
  return request({
    url: '/project/testProjectMember',
    method: 'post',
    data: data
  })
}

// 修改测试项目成员
export function updateTestProjectMember(data) {
  return request({
    url: '/project/testProjectMember',
    method: 'put',
    data: data
  })
}

// 删除测试项目成员
export function delTestProjectMember(testProjectMemberId) {
  return request({
    url: '/project/testProjectMember/' + testProjectMemberId,
    method: 'delete'
  })
}
