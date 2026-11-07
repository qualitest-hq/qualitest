import request from '@/utils/request'

// 查询测试项目列表
export function listTestProject(query) {
  return request({
    url: '/project/testProject/list',
    method: 'get',
    params: query
  })
}

// 查询测试项目详细
export function getTestProject(testProjectId) {
  return request({
    url: '/project/testProject/' + testProjectId,
    method: 'get'
  })
}

// 新增测试项目
export function addTestProject(data) {
  return request({
    url: '/project/testProject',
    method: 'post',
    data: data
  })
}

// 修改测试项目
export function updateTestProject(data) {
  return request({
    url: '/project/testProject',
    method: 'put',
    data: data
  })
}

// 删除测试项目
export function delTestProject(testProjectId) {
  return request({
    url: '/project/testProject/' + testProjectId,
    method: 'delete'
  })
}

// 我的项目信息
export function myProjectContext(testProjectId) {
  return request({
    url: '/project/testProject/myProjectContext',
    method: 'get',
    params: { testProjectId }
  })
}

/** 从项目模板追加鉴权 Profile，并种子尚未存在的预制接口 */
export function applyAuthTemplates(testProjectId, templateIds) {
  return request({
    url: `/project/testProject/${testProjectId}/applyAuthTemplates`,
    method: 'post',
    data: { templateIds },
  })
}

/** 从本项目另存为自定义鉴权项目模板（完整包） */
export function saveAsAuthTemplate(testProjectId, data) {
  return request({
    url: `/project/testProject/${testProjectId}/saveAsAuthTemplate`,
    method: 'post',
    data,
  })
}

/**
 * 项目运行中心总览。
 * 返回并发模式、缓存中的排队/执行中活动项，以及库表最近正式 Run。
 */
export function getProjectRunHub(testProjectId, recentLimit) {
  return request({
    url: `/project/testProject/${testProjectId}/runHub`,
    method: 'get',
    params: recentLimit != null ? { recentLimit } : undefined,
  })
}

/**
 * 取消仍在排队、尚未开跑的项。
 * 会打断对该 queueItemId 的等待轮询。
 */
export function cancelProjectQueuedRun(testProjectId, queueItemId) {
  return request({
    url: `/project/testProject/${testProjectId}/runHub/queue/${encodeURIComponent(queueItemId)}`,
    method: 'delete',
  })
}

/**
 * 从运行中心活动列表移除一行。
 * 排队行等价于取消排队；执行中行只清展示，不删库表正式报告。
 */
export function dismissProjectHubActive(testProjectId, itemId) {
  return request({
    url: `/project/testProject/${testProjectId}/runHub/active/${encodeURIComponent(itemId)}`,
    method: 'delete',
  })
}

/**
 * 非阻塞查询排队项是否已绑定 runId。
 * data 为 runId 字符串；尚未开跑或已取消时为空。
 */
export function peekQueuedRunId(testProjectId, queueItemId) {
  return request({
    url: `/project/testProject/${testProjectId}/runHub/queue/${encodeURIComponent(queueItemId)}/runId`,
    method: 'get',
  })
}
