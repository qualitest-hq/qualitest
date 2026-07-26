import request from '@/utils/request'

// 查询AI提示词模板列表
export function listAiPromptTemplate(query) {
  return request({
    url: '/ai/aiPromptTemplate/list',
    method: 'get',
    params: query
  })
}

// 查询AI提示词模板详细
export function getAiPromptTemplate(aiPromptTemplateId) {
  return request({
    url: '/ai/aiPromptTemplate/' + aiPromptTemplateId,
    method: 'get'
  })
}

// 新增AI提示词模板
export function addAiPromptTemplate(data) {
  return request({
    url: '/ai/aiPromptTemplate',
    method: 'post',
    data: data
  })
}

// 修改AI提示词模板
export function updateAiPromptTemplate(data) {
  return request({
    url: '/ai/aiPromptTemplate',
    method: 'put',
    data: data
  })
}

// 删除AI提示词模板
export function delAiPromptTemplate(aiPromptTemplateId) {
  return request({
    url: '/ai/aiPromptTemplate/' + aiPromptTemplateId,
    method: 'delete'
  })
}
