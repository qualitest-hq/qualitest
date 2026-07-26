/**
 * 生成 Cursor IDE 连接质衡 MCP 服务所需的客户端配置。
 * <p>
 * {@link #MCP_PATH} 须与后端 {@code ProjectConstants.MCP_ENDPOINT} 保持相同字面值。
 */

/** MCP 服务在质衡后端的 HTTP 路径（精确匹配，无尾斜杠）。 */
const MCP_PATH = '/api/project/mcp'

/** 写入 mcp.json 时使用的服务器键名，用户可在本地文件中自行改名。 */
const MCP_SERVER_KEY = 'qualitest'

/**
 * 计算 IDE 应连接的 MCP 完整 URL。
 * <p>
 * 优先级：
 * <ol>
 *   <li>{@code VITE_MCP_BASE_URL}：显式指定后端根地址（不含路径），适用于自定义部署</li>
 *   <li>开发模式：固定 {@code http://127.0.0.1:8080}，因 IDE 进程不经过 Vite 开发代理</li>
 *   <li>生产模式：当前页面 origin + {@code VITE_APP_BASE_API} 前缀拼出 API 根地址</li>
 * </ol>
 *
 * @returns 例如 {@code http://127.0.0.1:8080/api/project/mcp}
 */
export function resolveMcpEndpointUrl() {
  const explicit = import.meta.env.VITE_MCP_BASE_URL
  if (explicit) {
    return joinUrl(explicit, MCP_PATH)
  }
  if (import.meta.env.DEV) {
    return `http://127.0.0.1:8080${MCP_PATH}`
  }
  const baseApi = import.meta.env.VITE_APP_BASE_API || ''
  if (baseApi.startsWith('http://') || baseApi.startsWith('https://')) {
    return joinUrl(baseApi, MCP_PATH)
  }
  const origin = typeof window !== 'undefined' ? window.location.origin : ''
  return joinUrl(`${origin}${baseApi}`, MCP_PATH)
}

/**
 * 生成可粘贴到 Cursor {@code mcp.json} 的 JSON 文本。
 * <p>
 * 结构为 {@code mcpServers.<name>.url} 加 {@code headers.X-Project-Token}；
 * Token 来自项目设置，刷新 Token 后需重新复制。
 *
 * @param {string} projectToken 当前用户的项目 Token，未生成时传空字符串
 * @returns 带缩进的 JSON 字符串
 */
export function buildCursorMcpConfig(projectToken) {
  const config = {
    mcpServers: {
      [MCP_SERVER_KEY]: {
        url: resolveMcpEndpointUrl(),
        headers: {
          'X-Project-Token': projectToken || ''
        }
      }
    }
  }
  return JSON.stringify(config, null, 2)
}

/** 拼接 URL：去掉 base 末尾斜杠，保证 path 以 {@code /} 开头。 */
function joinUrl(base, path) {
  const normalized = String(base || '').replace(/\/+$/, '')
  const suffix = path.startsWith('/') ? path : `/${path}`
  return `${normalized}${suffix}`
}
