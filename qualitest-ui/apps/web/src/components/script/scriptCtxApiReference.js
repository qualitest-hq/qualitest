/**
 * script 节点宿主 ctx 对象 API 说明（与后端 ScriptHostContext 暴露能力对应）。
 */

import { buildScriptToolGroups } from './scriptToolReference'

/** API 分组：读写 flow、只读上下文、会话、HTTP、编码 / 报文 / 加密工具 */
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
        name: 'setAsset',
        sig: "ctx.setAsset('clientAuth', 'token', value)",
        desc: '仅写本 Run 素材内存，不回写项目素材库',
        access: 'write',
      },
      {
        name: 'getLastHttp',
        sig: 'ctx.getLastHttp()',
        desc: '上一步 HTTP 快照：status / headers / body（JSON、XML 已转对象）/ bodyText（原文）/ responseKind / durationMs；无则 null',
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
    title: '通用',
    items: [
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
        desc: '外联 HTTP；支持 http/https 任意地址；节点每次执行最多调用 3 次，超出报错；返回 { ok, status, headers, body, durationMs }',
        access: 'util',
      },
    ],
  },
  // 编码 / 报文 / 加密等共用工具函数
  ...buildScriptToolGroups('ctx'),
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
      {
        title: '微信支付 v2 回调 XML（MD5 签名）',
        code: `params = {
    'return_code': 'SUCCESS',
    'result_code': 'SUCCESS',
    'out_trade_no': ctx.getFlow('orderNo'),
    'total_fee': 1,
    'nonce_str': ctx.uuid().replace('-', ''),
}
params['sign'] = ctx.md5(ctx.signContent(params) + '&key=' + ctx.getAsset('wxpay', 'apiKey')).upper()
ctx.setFlow('wxNotifyXml', ctx.xmlStringify(params))`,
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
    {
      title: '支付宝异步通知（RSA2 签名表单）',
      code: `const params = {
  notify_id: ctx.uuid(),
  notify_time: '2026-01-01 12:00:00',
  app_id: ctx.getEnv('alipayAppId'),
  out_trade_no: ctx.getFlow('orderNo'),
  trade_no: 'T' + Date.now(),
  trade_status: 'TRADE_SUCCESS',
  total_amount: '0.01',
  charset: 'utf-8',
  sign_type: 'RSA2',
  version: '1.0'
};
const content = ctx.signContent(params, { exclude: ['sign', 'sign_type'] });
params.sign = ctx.rsaSign(content, ctx.getAsset('alipay', 'privateKey'));
const form = Object.keys(params).map(k => k + '=' + ctx.urlEncode(params[k])).join('&');
ctx.setFlow('alipayNotifyBody', form);`,
    },
    {
      title: '微信支付 v2 回调 XML（MD5 签名）',
      code: `const params = {
  return_code: 'SUCCESS',
  result_code: 'SUCCESS',
  out_trade_no: ctx.getFlow('orderNo'),
  total_fee: 1,
  nonce_str: ctx.uuid().replace(/-/g, '')
};
const content = ctx.signContent(params) + '&key=' + ctx.getAsset('wxpay', 'apiKey');
params.sign = ctx.md5(content).toUpperCase();
ctx.setFlow('wxNotifyXml', ctx.xmlStringify(params));`,
    },
    {
      title: '微信支付 v3 回调（加密 resource + 签名头）',
      code: `const nonce = ctx.uuid().replace(/-/g, '').slice(0, 12);
const plain = ctx.jsonStringify({ out_trade_no: ctx.getFlow('orderNo'), trade_state: 'SUCCESS' });
const body = ctx.jsonStringify({
  id: ctx.uuid(),
  event_type: 'TRANSACTION.SUCCESS',
  resource_type: 'encrypt-resource',
  resource: {
    algorithm: 'AEAD_AES_256_GCM',
    associated_data: 'transaction',
    nonce: nonce,
    ciphertext: ctx.aesGcmEncrypt(plain, ctx.getAsset('wxpay', 'apiV3Key'), nonce, 'transaction')
  }
});
const ts = String(Math.floor(Date.now() / 1000));
const sigNonce = ctx.uuid();
const signature = ctx.rsaSign(ts + '\\n' + sigNonce + '\\n' + body + '\\n', ctx.getAsset('wxpay', 'platformPrivateKey'));
ctx.setFlow('wxV3Body', body);
ctx.setFlow('wxV3Headers', ctx.jsonStringify({
  'Wechatpay-Timestamp': ts,
  'Wechatpay-Nonce': sigNonce,
  'Wechatpay-Signature': signature,
  'Wechatpay-Serial': ctx.getAsset('wxpay', 'platformSerial')
}));`,
    },
  ]
}

/** 沙箱与使用限制说明 */
export const SCRIPT_CTX_NOTES = [
  'JavaScript 与 Python 使用同一套 ctx API，仅在服务端执行',
  '仅 ctx.setFlow / ctx.session.set 可写；env / asset 只读',
  'ctx.http 支持 http/https 任意地址，节点每次执行最多调用 3 次；优先用 HTTP(external) 节点',
  '不支持任意网络、文件、子进程与外部模块导入',
  'print / console 与 ctx.log 输出会写入步骤日志',
  '加密函数入参出参均为字符串；私钥、密钥建议放项目素材或环境变量，不要写死在脚本里',
  '仿渠道回调时，被测系统需配置与脚本私钥对应的公钥 / 证书，验签才会通过',
]
