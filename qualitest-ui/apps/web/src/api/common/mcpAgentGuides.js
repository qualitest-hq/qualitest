import request from '@/utils/request'

/**
 * 拉取 MCP 造流弹框载荷：怎么用、提示、示例提问，以及各编辑器规程 Tab（含可复制正文）。
 * @param {string|number} [testProjectId] 当前测试项目 id；传入则按该项目写流/导入开关裁剪规程，不传则为只读规程
 */
export function getMcpAgentGuides(testProjectId) {
  return request({
    url: '/common/mcpAgentGuides',
    method: 'get',
    params: testProjectId != null && testProjectId !== '' ? { testProjectId } : undefined,
  })
}
