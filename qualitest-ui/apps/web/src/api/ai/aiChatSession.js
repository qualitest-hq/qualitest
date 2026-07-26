import request from '@/utils/request'

// 查询AI 会话列表
export function listAiChatSession(query) {
  return request({
    url: '/ai/aiChatSession/list',
    method: 'get',
    params: query
  })
}

// 查询AI 会话详细
export function getAiChatSession(aiChatSessionId) {
  return request({
    url: '/ai/aiChatSession/' + aiChatSessionId,
    method: 'get'
  })
}

// 新增AI 会话
export function addAiChatSession(data) {
  return request({
    url: '/ai/aiChatSession',
    method: 'post',
    data: data
  })
}

// 修改AI 会话
export function updateAiChatSession(data) {
  return request({
    url: '/ai/aiChatSession',
    method: 'put',
    data: data
  })
}

// 删除AI 会话
export function delAiChatSession(aiChatSessionId) {
  return request({
    url: '/ai/aiChatSession/' + aiChatSessionId,
    method: 'delete'
  })
}
