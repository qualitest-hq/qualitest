import request from '@/utils/request'

/**
 * 拉取 MCP 造流弹框载荷：
 * howToUse / tips / examples + guides（各编辑器 Tab：id / title / intro / steps / saveHint / content）。
 */
export function getMcpAgentGuides() {
  return request({
    url: '/common/mcpAgentGuides',
    method: 'get',
  })
}
