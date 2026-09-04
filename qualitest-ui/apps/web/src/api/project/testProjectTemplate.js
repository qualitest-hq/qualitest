import request from '@/utils/request'

/** 项目模板分页列表 */
export function listTestProjectTemplate(query) {
  return request({
    url: '/project/testProjectTemplate/list',
    method: 'get',
    params: query,
  })
}

/** 项目模板详情 */
export function getTestProjectTemplate(testProjectTemplateId) {
  return request({
    url: '/project/testProjectTemplate/' + testProjectTemplateId,
    method: 'get',
  })
}

/** 新增自定义项目模板 */
export function addTestProjectTemplate(data) {
  return request({
    url: '/project/testProjectTemplate',
    method: 'post',
    data,
  })
}

/** 修改自定义项目模板 */
export function updateTestProjectTemplate(data) {
  return request({
    url: '/project/testProjectTemplate',
    method: 'put',
    data,
  })
}

/** 逻辑删除自定义项目模板 */
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

/** 已启用项目模板列表（新建/设置勾选） */
export function listEnabledTestProjectTemplate() {
  return request({
    url: '/project/testProjectTemplate/enabledList',
    method: 'get',
  })
}

/** 校验完整包或精简包：返回摘要与预览，不写库 */
export function validateTestProjectTemplatePack(data) {
  return request({
    url: '/project/testProjectTemplate/validate',
    method: 'post',
    data,
  })
}

/** 拉取可复制给 AI 的精简包生成提示词正文 */
export function getTestProjectTemplateAiPrompt() {
  return request({
    url: '/project/testProjectTemplate/aiPrompt',
    method: 'get',
  })
}

/** 导入完整包或精简包为自定义模板 */
export function importTestProjectTemplatePack(data) {
  return request({
    url: '/project/testProjectTemplate/import',
    method: 'post',
    data,
  })
}

/** 按模板 id 导出完整包（含测试流等） */
export function exportTestProjectTemplatePack(testProjectTemplateId) {
  return request({
    url: '/project/testProjectTemplate/' + testProjectTemplateId + '/export',
    method: 'get',
  })
}
