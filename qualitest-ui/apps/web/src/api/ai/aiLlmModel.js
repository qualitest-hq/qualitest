import request from '@/utils/request'

// 查询AI 模型列表
export function listAiLlmModel(query) {
  return request({
    url: '/ai/aiLlmModel/list',
    method: 'get',
    params: query
  })
}

// 查询AI 模型详细
export function getAiLlmModel(aiLlmModelId) {
  return request({
    url: '/ai/aiLlmModel/' + aiLlmModelId,
    method: 'get'
  })
}

// 新增AI 模型
export function addAiLlmModel(data) {
  return request({
    url: '/ai/aiLlmModel',
    method: 'post',
    data: data
  })
}

// 修改AI 模型
export function updateAiLlmModel(data) {
  return request({
    url: '/ai/aiLlmModel',
    method: 'put',
    data: data
  })
}

// 删除AI 模型
export function delAiLlmModel(aiLlmModelId) {
  return request({
    url: '/ai/aiLlmModel/' + aiLlmModelId,
    method: 'delete'
  })
}
