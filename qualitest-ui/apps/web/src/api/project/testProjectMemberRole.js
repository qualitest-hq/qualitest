/**
 * 项目角色接口：角色 CRUD，以及成员页下拉、角色编辑页权限父子树用的辅助列表。
 */
import request from '@/utils/request'

/** 分页查询项目成员角色 */
export function listTestProjectMemberRole(query) {
  return request({
    url: '/project/testProjectMemberRole/list',
    method: 'get',
    params: query
  })
}

/** 查询单个角色详情（含 permCodes） */
export function getTestProjectMemberRole(testProjectMemberRoleId) {
  return request({
    url: '/project/testProjectMemberRole/' + testProjectMemberRoleId,
    method: 'get'
  })
}

/** 新增角色并绑定权限 */
export function addTestProjectMemberRole(data) {
  return request({
    url: '/project/testProjectMemberRole',
    method: 'post',
    data: data
  })
}

/** 修改角色元信息与权限 */
export function updateTestProjectMemberRole(data) {
  return request({
    url: '/project/testProjectMemberRole',
    method: 'put',
    data: data
  })
}

/** 删除角色 */
export function delTestProjectMemberRole(testProjectMemberRoleId) {
  return request({
    url: '/project/testProjectMemberRole/' + testProjectMemberRoleId,
    method: 'delete'
  })
}

/** 仅返回启用状态的角色，供成员筛选与表单下拉（value=roleKey，label=roleName） */
export function listEnabledMemberRoles() {
  return request({
    url: '/project/testProjectMemberRole/enabledOptions',
    method: 'get'
  })
}

/** 返回可分配给角色的权限目录（父子树：父为分组，子为权限，字段 id/label/children） */
export function listMemberRolePermCatalog() {
  return request({
    url: '/project/testProjectMemberRole/permCatalog',
    method: 'get'
  })
}
