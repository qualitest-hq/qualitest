import request from '@/utils/request'

/** 已启用鉴权模板列表（新建/设置勾选） */
export function listEnabledTestProjectTemplate() {
  return request({
    url: '/project/testProjectTemplate/enabledList',
    method: 'get',
  })
}
