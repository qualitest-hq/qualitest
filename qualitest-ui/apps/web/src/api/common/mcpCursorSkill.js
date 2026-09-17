import request from '@/utils/request'

/**
 * 拉取 MCP 造流 Agent 规程列表（多编辑器 Tab：id / title / intro / steps / saveHint / content）。
 */
export function getMcpAgentGuides() {
  return request({
    url: '/common/mcpAgentGuides',
    method: 'get',
  })
}
