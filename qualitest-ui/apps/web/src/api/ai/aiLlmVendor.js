import request from '@/utils/request'

// 查询 AI 配置列表
export function listAiLlmVendor(query) {
  return request({
    url: '/ai/aiLlmVendor/list',
    method: 'get',
    params: query
  })
}

// 查询 AI 配置详细
export function getAiLlmVendor(aiLlmVendorId) {
  return request({
    url: '/ai/aiLlmVendor/' + aiLlmVendorId,
    method: 'get'
  })
}

// 新增 AI 配置
export function addAiLlmVendor(data) {
  return request({
    url: '/ai/aiLlmVendor',
    method: 'post',
    data: data
  })
}

// 修改 AI 配置
export function updateAiLlmVendor(data) {
  return request({
    url: '/ai/aiLlmVendor',
    method: 'put',
    data: data
  })
}

// 删除 AI 配置
export function delAiLlmVendor(aiLlmVendorId) {
  return request({
    url: '/ai/aiLlmVendor/' + aiLlmVendorId,
    method: 'delete'
  })
}
