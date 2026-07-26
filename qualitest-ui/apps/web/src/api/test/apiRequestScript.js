import request from '@/utils/request'

const PRE_PATH = '/test/api-request-script/pre'
const POST_PATH = '/test/api-request-script/post'

/**
 * @param {object} payload
 * @returns {Promise<import('@/views/project/testProject/composables/useApiDebugScript').ApiScriptExecuteResult>}
 */
export function executePreRequestScript(payload) {
  return request({
    url: PRE_PATH,
    method: 'post',
    data: payload,
    timeout: 35000
  })
}

/**
 * @param {object} payload
 * @returns {Promise<import('@/views/project/testProject/composables/useApiDebugScript').ApiScriptExecuteResult>}
 */
export function executePostRequestScript(payload) {
  return request({
    url: POST_PATH,
    method: 'post',
    data: payload,
    timeout: 35000
  })
}
