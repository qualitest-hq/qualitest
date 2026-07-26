/**
 * API 前置/后置脚本宿主 api 对象说明。
 */

export const SCRIPT_API_GROUPS = [
  {
    id: 'variables',
    title: '变量',
    items: [
      { name: 'variables', sig: "api.variables.set('key', value)", desc: '临时变量（调试会话 / flow）', access: 'write' },
      { name: 'environment', sig: "api.environment.get('key')", desc: '环境变量（set 仅影响当前会话）', access: 'read' },
      { name: 'globals', sig: "api.globals.set('key', value)", desc: '全局变量（调试会话）', access: 'write' },
    ],
  },
  {
    id: 'request',
    title: '请求（前置可写）',
    items: [
      { name: 'request.url', sig: 'api.request.url = fullUrl', desc: '修改请求 URL', access: 'write' },
      { name: 'request.headers.add', sig: "api.request.headers.add({ key, value })", desc: '添加 Header', access: 'write' },
      { name: 'request.body', sig: 'api.request.body', desc: '请求体快照（kind/raw/json/fields）', access: 'read' },
    ],
  },
  {
    id: 'response',
    title: '响应（后置只读）',
    items: [
      { name: 'response.code', sig: 'api.response.code', desc: 'HTTP 状态码', access: 'read' },
      { name: 'response.json', sig: 'api.response.json()', desc: '解析 JSON 响应体', access: 'read' },
      { name: 'response.text', sig: 'api.response.text()', desc: '原始响应文本', access: 'read' },
    ],
  },
  {
    id: 'assert',
    title: '断言',
    items: [
      { name: 'test', sig: "api.test('name', () => { ... })", desc: '后置测试块', access: 'util' },
      { name: 'expect', sig: 'api.expect(actual).to.equal(expected)', desc: '断言链', access: 'util' },
    ],
  },
  {
    id: 'utils',
    title: '工具',
    items: [
      { name: 'sendRequest', sig: 'api.sendRequest({ url, method, headers, body })', desc: '同步发送辅助 HTTP', access: 'util' },
      { name: 'hmacSha256', sig: "api.hmacSha256(data, secret)", desc: 'HMAC-SHA256 签名', access: 'util' },
    ],
  },
]

export const SCRIPT_API_MIGRATION_NOTES = [
  'Postman/Apifox：将 pm. 替换为 api.',
  'Apipost：apt.setRequestHeader(k,v) → api.request.headers.add({ key:k, value:v })',
  '仅 JavaScript；在服务端 GraalVM 沙箱执行',
]
