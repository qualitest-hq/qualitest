/**
 * API 前置/后置脚本宿主 api 对象说明（脚本编辑器侧栏参考）。
 * 运行时为服务端 GraalJS：无 DOM、无 btoa/atob、无 Postman pm.*。
 */

export const SCRIPT_API_GROUPS = [
  {
    id: 'variables',
    title: '变量',
    items: [
      { name: 'variables.get/set/unset/has', sig: "api.variables.get('key') / .set('key', value)", desc: '临时变量（跑流写入 flow；调试会话内有效）', access: 'write' },
      { name: 'environment.get/set/unset/has', sig: "api.environment.get('baseUrl')", desc: '环境变量；set 仅当前会话，不回写环境库', access: 'read' },
      { name: 'globals.get/set/unset/has', sig: "api.globals.set('key', value)", desc: '全局变量（调试会话）', access: 'write' },
    ],
  },
  {
    id: 'request',
    title: '请求（仅前置可写）',
    items: [
      { name: 'request.url', sig: 'api.request.url = fullUrl', desc: '读/改完整 URL（改 query 也改这里）', access: 'write' },
      { name: 'request.method', sig: "api.request.method = 'POST'", desc: '读/改 HTTP 方法', access: 'write' },
      { name: 'request.headers.add', sig: "api.request.headers.add({ key, value }) 或 add(k, v)", desc: '添加/覆盖 Header', access: 'write' },
      { name: 'request.headers.remove', sig: "api.request.headers.remove('X-Custom')", desc: '删除 Header', access: 'write' },
      { name: 'request.headers.get/list', sig: "api.request.headers.get('Authorization')", desc: '读单个或列出全部 Header', access: 'read' },
      { name: 'request.body', sig: 'api.request.body = { kind, raw, ... }', desc: '读 body 快照；写入须整对象重赋（嵌套改字段不会回写）', access: 'write' },
    ],
  },
  {
    id: 'response',
    title: '响应（仅后置可读）',
    items: [
      { name: 'response.code / status', sig: 'api.response.code', desc: 'HTTP 状态码', access: 'read' },
      { name: 'response.statusText', sig: 'api.response.statusText', desc: '状态文案', access: 'read' },
      { name: 'response.headers', sig: 'api.response.headers', desc: '响应头对象', access: 'read' },
      { name: 'response.json', sig: 'api.response.json()', desc: '解析 JSON 响应体', access: 'read' },
      { name: 'response.text', sig: 'api.response.text()', desc: '原始响应文本', access: 'read' },
    ],
  },
  {
    id: 'assert',
    title: '断言（仅后置）',
    items: [
      { name: 'test', sig: "api.test('name', () => { ... })", desc: '后置测试块；前置调用会报错', access: 'util' },
      { name: 'expect', sig: 'api.expect(actual).to.equal(expected)', desc: '断言链（equal/eql/include/…）', access: 'util' },
    ],
  },
  {
    id: 'utils',
    title: '工具函数',
    items: [
      { name: 'base64Encode / base64Decode', sig: "api.base64Encode('plain')", desc: 'Base64 编解码；无浏览器 btoa/atob', access: 'util' },
      { name: 'jsonParse / jsonStringify', sig: 'api.jsonParse(text) / api.jsonStringify(obj)', desc: 'JSON 解析与序列化', access: 'util' },
      { name: 'hmacSha256', sig: "api.hmacSha256(data, secret)", desc: 'HMAC-SHA256，返回 hex', access: 'util' },
      { name: 'md5', sig: "api.md5(data)", desc: 'MD5，返回 hex', access: 'util' },
      { name: 'sendRequest', sig: 'api.sendRequest({ url, method, headers, body })', desc: '同步辅助 HTTP（如换 token）', access: 'util' },
    ],
  },
]

/** 从其他工具迁到 api.* 时的对照说明（编辑器提示用）。 */
export const SCRIPT_API_MIGRATION_NOTES = [
  '运行时：服务端 GraalVM JS，无 DOM、无 btoa/atob、无 Postman pm.* / Apifox pm',
  'Postman：pm.environment.get → api.environment.get；pm.request → api.request',
  'Apipost：apt.setRequestHeader(k,v) → api.request.headers.add({ key:k, value:v })',
  'project 绑定节点：用接口库 preRequestScript/postRequestScript；节点 data.preScript 不执行',
  'external 节点：用 data.preScript / data.postScript',
  '改 body 须 api.request.body = 新对象；对 api.request.body.xxx = 的嵌套赋值不会回写到真实请求',
]
