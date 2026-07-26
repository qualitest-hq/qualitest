import request from '@/utils/request'

// 查询AI 会话消息列表
export function listAiChatMessage(query) {
  return request({
    url: '/ai/aiChatMessage/list',
    method: 'get',
    params: query
  })
}

// 查询AI 会话消息详细
export function getAiChatMessage(aiChatMessageId) {
  return request({
    url: '/ai/aiChatMessage/' + aiChatMessageId,
    method: 'get'
  })
}

// 新增AI 会话消息
export function addAiChatMessage(data) {
  return request({
    url: '/ai/aiChatMessage',
    method: 'post',
    data: data
  })
}

// 修改AI 会话消息
export function updateAiChatMessage(data) {
  return request({
    url: '/ai/aiChatMessage',
    method: 'put',
    data: data
  })
}

// 删除AI 会话消息
export function delAiChatMessage(aiChatMessageId) {
  return request({
    url: '/ai/aiChatMessage/' + aiChatMessageId,
    method: 'delete'
  })
}
