import request from '@/utils/request'

// 编辑测试项目用户设置
export function editTestProjectUserSetting(data) {
  return request({
    url: '/project/testProjectUserSetting/edit',
    method: 'post',
    data: data
  })
}

// 更新测试项目用户设置的环境ID
export function updateTestProjectUserSettingEnv(data) {
  return request({
    url: '/project/testProjectUserSetting/updateEnv',
    method: 'put',
    data: data
  })
}

// 根据项目ID刷新项目Token
export function refreshProjectTokenByProjectId(testProjectId) {
  return request({
    url: '/project/testProjectUserSetting/refreshToken/project/' + testProjectId,
    method: 'post'
  })
}