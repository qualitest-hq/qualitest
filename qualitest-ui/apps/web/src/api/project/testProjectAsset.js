import request from '@/utils/request'

/** 查询项目素材列表 */
export function listTestProjectAsset(query) {
  return request({
    url: '/project/testProjectAsset/list',
    method: 'get',
    params: query
  })
}

/** 按条目 id 查询 */
export function getTestProjectAsset(id, testProjectId) {
  return request({
    url: '/project/testProjectAsset/' + id,
    method: 'get',
    params: { testProjectId }
  })
}

/** 按 key 查询 */
export function getTestProjectAssetByKey(testProjectId, key) {
  return request({
    url: '/project/testProjectAsset/byKey',
    method: 'get',
    params: { testProjectId, key }
  })
}

/** 新增素材 */
export function addTestProjectAsset(data) {
  return request({
    url: '/project/testProjectAsset',
    method: 'post',
    data
  })
}

/** 修改素材 */
export function updateTestProjectAsset(data) {
  return request({
    url: '/project/testProjectAsset',
    method: 'put',
    data
  })
}

/** 批量删除（ids 逗号分隔） */
export function delTestProjectAsset(ids, testProjectId) {
  return request({
    url: '/project/testProjectAsset/' + ids,
    method: 'delete',
    params: { testProjectId }
  })
}
