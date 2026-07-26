import request from '@/utils/request'

// 厂商模板列表
export function listProviderTemplates() {
  return request({
    url: '/ai/llm/provider-templates',
    method: 'get'
  })
}

// 检测厂商连通性
export function testLlmConnection(data) {
  return request({
    url: '/ai/llm/vendor/test-connection',
    method: 'post',
    data
  })
}

// 发现远端模型列表
export function discoverLlmModels(vendorId, refresh = false) {
  return request({
    url: '/ai/llm/vendor/' + vendorId + '/discover-models',
    method: 'get',
    params: { refresh }
  })
}

// 同步模型入库
export function syncLlmModels(vendorId, data) {
  return request({
    url: '/ai/llm/vendor/' + vendorId + '/sync-models',
    method: 'post',
    data
  })
}
