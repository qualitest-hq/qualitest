import request from '@/utils/request'

/** 鉴权模板分页列表 */
export function listTestProjectTemplate(query) {
  return request({
    url: '/project/testProjectTemplate/list',
    method: 'get',
    params: query,
  })
}

/** 鉴权模板详情 */
export function getTestProjectTemplate(testProjectTemplateId) {
  return request({
    url: '/project/testProjectTemplate/' + testProjectTemplateId,
    method: 'get',
  })
}

/** 新增自定义鉴权模板 */
export function addTestProjectTemplate(data) {
  return request({
    url: '/project/testProjectTemplate',
    method: 'post',
    data,
  })
}

/** 修改自定义鉴权模板 */
export function updateTestProjectTemplate(data) {
  return request({
    url: '/project/testProjectTemplate',
    method: 'put',
    data,
  })
}

/** 逻辑删除自定义鉴权模板 */
export function delTestProjectTemplate(testProjectTemplateIds) {
  return request({
    url: '/project/testProjectTemplate/' + testProjectTemplateIds,
    method: 'delete',
  })
}

/** 克隆为自定义模板，返回新 id */
export function cloneTestProjectTemplate(testProjectTemplateId) {
  return request({
    url: '/project/testProjectTemplate/' + testProjectTemplateId + '/clone',
    method: 'post',
  })
}

/** 已启用鉴权模板列表（新建/设置勾选） */
export function listEnabledTestProjectTemplate() {
  return request({
    url: '/project/testProjectTemplate/enabledList',
    method: 'get',
  })
}
