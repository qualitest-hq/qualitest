/**
 * API 前置/后置脚本 — 常用脚本片段（api.* 宿主）。
 *
 * @typedef {'pre' | 'post'} ApiScriptPhase
 * @typedef {{ id: string, label: string, comment: string, code: string }} ApiScriptSnippet
 */

/** @type {ApiScriptSnippet[]} */
export const API_PRE_REQUEST_SNIPPETS = [
  {
    id: 'env-set',
    label: '设置一个环境变量',
    comment: '设置一个环境变量（仅当前调试会话有效）',
    code: "api.environment.set('key', 'value');",
  },
  {
    id: 'env-get',
    label: '获取一个环境变量',
    comment: '获取一个环境变量',
    code: "var value = api.environment.get('key');",
  },
  {
    id: 'env-unset',
    label: '删除一个环境变量',
    comment: '删除当前会话中的环境变量',
    code: "api.environment.unset('key');",
  },
  {
    id: 'env-base-url',
    label: '获取当前环境 baseUrl',
    comment: '获取当前选中环境的前置 URL（baseUrl）',
    code: "var baseUrl = api.environment.get('baseUrl');",
  },
  {
    id: 'var-set',
    label: '设置一个临时变量',
    comment: '设置临时变量（variables，可在后续请求中使用 {{flow.*}} 或脚本读取）',
    code: "api.variables.set('key', 'value');",
  },
  {
    id: 'var-get',
    label: '获取一个临时变量',
    comment: '获取临时变量',
    code: "var value = api.variables.get('key');",
  },
  {
    id: 'global-set',
    label: '设置一个全局变量',
    comment: '设置全局变量（当前项目调试会话）',
    code: "api.globals.set('key', 'value');",
  },
  {
    id: 'header-add',
    label: '添加请求 Header',
    comment: '动态添加请求头',
    code: "api.request.headers.add({ key: 'X-Custom', value: 'value' });",
  },
  {
    id: 'header-remove',
    label: '删除请求 Header',
    comment: '删除指定请求头',
    code: "api.request.headers.remove('X-Custom');",
  },
  {
    id: 'hmac-sign',
    label: '生成 HMAC 签名',
    comment: '使用 HMAC-SHA256 生成签名并写入临时变量',
    code: "api.variables.set('sign', api.hmacSha256('data', api.environment.get('secret')));",
  },
  {
    id: 'base64-password',
    label: '密码 Base64 后写入 body',
    comment: '读环境明文密码，Base64 后整对象重赋 request.body（嵌套改字段无效）',
    code: `var pwd = api.environment.get('password');
var encoded = api.base64Encode(pwd);
api.request.body = {
  kind: 'json',
  raw: api.jsonStringify({ mobile: api.environment.get('mobile'), password: encoded })
};`,
  },
  {
    id: 'send-request',
    label: '发送辅助 HTTP 请求',
    comment: '在前置脚本中同步发送辅助请求（如获取 token）',
    code: `var res = api.sendRequest({
  url: api.environment.get('baseUrl') + '/api/token',
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: { kind: 'json', raw: '{}' }
});
if (res && res.code === 200) {
  api.variables.set('token', res.body);
}`,
  },
]

/** @type {ApiScriptSnippet[]} */
export const API_POST_REQUEST_SNIPPETS = [
  {
    id: 'assert-status',
    label: '断言状态码为 200',
    comment: '断言 HTTP 状态码为 200',
    code: `api.test('状态码为 200', function () {
  api.expect(api.response.code).to.equal(200);
});`,
  },
  {
    id: 'parse-json',
    label: '解析 JSON 响应体',
    comment: '将响应体解析为 JSON 对象',
    code: 'var data = api.response.json();',
  },
  {
    id: 'extract-token',
    label: '提取 token 到临时变量',
    comment: '从 JSON 响应中提取 token 并保存',
    code: `var data = api.response.json();
if (data && data.token) {
  api.variables.set('token', data.token);
}`,
  },
  {
    id: 'assert-body-field',
    label: '断言响应字段等于期望值',
    comment: '断言 JSON 响应中某字段等于指定值',
    code: `api.test('code 为 0', function () {
  var data = api.response.json();
  api.expect(data.code).to.equal(0);
});`,
  },
  {
    id: 'assert-include',
    label: '断言响应体包含文本',
    comment: '断言响应文本包含指定字符串',
    code: `api.test('响应包含 success', function () {
  api.expect(api.response.text()).to.include('success');
});`,
  },
  {
    id: 'log-response',
    label: '输出响应状态与耗时',
    comment: '通过 console 输出响应摘要（写入脚本日志）',
    code: `console.log('status=' + api.response.code);
console.log('body=' + api.response.text());`,
  },
  {
    id: 'env-set-from-response',
    label: '将响应值写入环境变量',
    comment: '从响应中提取值并写入环境变量（当前会话）',
    code: `var data = api.response.json();
if (data && data.token) {
  api.environment.set('token', data.token);
}`,
  },
]

/**
 * @param {ApiScriptPhase} phase
 * @returns {ApiScriptSnippet[]}
 */
export function getApiScriptSnippets(phase) {
  return phase === 'post' ? API_POST_REQUEST_SNIPPETS : API_PRE_REQUEST_SNIPPETS
}

/**
 * @param {ApiScriptSnippet} snippet
 * @param {boolean} withComment
 * @returns {string}
 */
export function formatApiScriptSnippet(snippet, withComment) {
  if (!snippet) return ''
  if (!withComment || !snippet.comment) {
    return snippet.code
  }
  return `// ${snippet.comment}\n${snippet.code}`
}
