/**
 * script 节点宿主 ctx 对象 API 说明（与后端 ScriptHostContext 暴露能力对应）。
 */

/** API 分组：读写 flow、只读上下文、JSON/签名工具 */
export const SCRIPT_CTX_API_GROUPS = [
  {
    id: 'flow',
    title: 'flow 变量',
    items: [
      {
        name: 'getFlow',
        sig: "ctx.getFlow('key')",
        desc: '读取当前 Run 的 flow 变量',
        access: 'read',
      },
      {
        name: 'setFlow',
        sig: "ctx.setFlow('key', value)",
        desc: '写入 flow 变量；脚本中唯一可写 flow 入口',
        access: 'write',
      },
    ],
  },
  {
    id: 'context',
    title: '只读上下文',
    items: [
      {
        name: 'getEnv',
        sig: "ctx.getEnv('baseUrl')",
        desc: '读取当前场景绑定的 env 变量',
        access: 'read',
      },
      {
        name: 'getAsset',
        sig: "ctx.getAsset('defaults', 'apiKey')",
        desc: '读取项目素材；第二参数 path 可选，点分路径',
        access: 'read',
      },
      {
        name: 'getLastHttp',
        sig: 'ctx.getLastHttp()',
        desc: '上一步 HTTP 快照：status / headers / body / durationMs；无则 null',
        access: 'read',
      },
    ],
  },
  {
    id: 'session',
    title: 'Run 会话',
    items: [
      {
        name: 'session.get',
        sig: "ctx.session.get('token')",
        desc: '读取 Run 级 session 缓存（跨步骤复用）',
        access: 'read',
      },
      {
        name: 'session.set',
        sig: "ctx.session.set('token', value)",
        desc: '写入 Run 级 session 缓存',
        access: 'write',
      },
    ],
  },
  {
    id: 'utils',
    title: 'JSON 与签名',
    items: [
      {
        name: 'jsonParse',
        sig: "ctx.jsonParse('{ \"a\": 1 }')",
        desc: 'JSON 字符串解析为对象',
        access: 'util',
      },
      {
        name: 'jsonStringify',
        sig: 'ctx.jsonStringify({ a: 1 })',
        desc: '对象序列化为 JSON 字符串',
        access: 'util',
      },
      {
        name: 'hmacSha256',
        sig: "ctx.hmacSha256(data, secret)",
        desc: 'HMAC-SHA256，返回小写十六进制字符串',
        access: 'util',
      },
      {
        name: 'md5',
        sig: "ctx.md5('text')",
        desc: 'MD5 摘要，返回小写十六进制字符串',
        access: 'util',
      },
      {
        name: 'base64Encode',
        sig: "ctx.base64Encode('text')",
        desc: 'UTF-8 文本 Base64 编码',
        access: 'util',
      },
      {
        name: 'base64Decode',
        sig: "ctx.base64Decode('dGV4dA==')",
        desc: 'Base64 解码为 UTF-8 文本',
        access: 'util',
      },
      {
        name: 'uuid',
        sig: 'ctx.uuid()',
        desc: '生成 UUID 字符串（state / requestId 等）',
        access: 'util',
      },
      {
        name: 'log',
        sig: "ctx.log('message')",
        desc: '写入步骤日志（与 print 输出合并展示）',
        access: 'util',
      },
    ],
  },
  {
    id: 'http',
    title: 'HTTP（外联）',
    items: [
      {
        name: 'http',
        sig: 'ctx.http({ method, url, headers?, body?, timeoutMs? })',
        desc: '外联 HTTP；支持 http/https 任意地址；单步最多 3 次；返回 { ok, status, headers, body, durationMs }',
        access: 'util',
      },
    ],
  },
]

/** 按语言返回示例片段 */
export function getScriptCtxExamples(language) {
  if (language === 'python') {
    return [
      {
        title: '写入 flow',
        code: "ctx.setFlow('token', ctx.getFlow('token') or '')",
      },
      {
        title: 'Base64 与 session',
        code: "ctx.session.set('img', ctx.base64Encode(ctx.getFlow('rawImage')))\nctx.setFlow('rid', ctx.uuid())",
      },
      {
        title: '生成签名',
        code: "sign = ctx.hmacSha256(ctx.getFlow('body'), ctx.getEnv('secret'))\nctx.setFlow('sign', sign)",
      },
    ]
  }
  return [
    {
      title: '写入 flow',
      code: "ctx.setFlow('demo', ctx.jsonStringify({ ok: true }));",
    },
    {
      title: '项目签名 Header',
      code: "const ts = Date.now();\nconst sign = ctx.hmacSha256(String(ts) + ctx.getFlow('body'), ctx.getAsset('pay', 'signSecret'));\nctx.setFlow('payHeaders', ctx.jsonStringify({ 'X-Timestamp': String(ts), 'X-Sign': sign }));",
    },
    {
      title: '外联 HTTP',
      code: "const res = ctx.http({\n  method: 'POST',\n  url: ctx.getEnv('captchaApiUrl'),\n  headers: { 'Content-Type': 'application/json' },\n  body: ctx.jsonStringify({ image: ctx.getFlow('captchaImageBase64') })\n});\nif (!res.ok) throw new Error('打码失败');\nctx.setFlow('code', ctx.jsonParse(res.body).data.code);",
    },
  ]
}

/** 沙箱与使用限制说明 */
export const SCRIPT_CTX_NOTES = [
  'JavaScript 与 Python 使用同一套 ctx API，仅在服务端执行',
  '仅 ctx.setFlow / ctx.session.set 可写；env / asset 只读',
  'ctx.http 支持 http/https 任意地址，单步最多 3 次；优先用 HTTP(external) 节点',
  '不支持任意网络、文件、子进程与外部模块导入',
  'print / console 与 ctx.log 输出会写入步骤日志',
]
