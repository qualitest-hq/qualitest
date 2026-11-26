# 测试流节点说明

画布节点 type 见 `FlowNodeType`（持久化小写 code；UI 展示用英文 label）。流契约 2.0：入口三类 + `http-return` / `subflow-return`；场景图不提供返回节点。下文每种节点单独一节，用 **JSONC**（`jsonc` 代码块，`//` 注释字段含义）展示落盘结构。

英文版：[test-flow-nodes.en.md](./test-flow-nodes.en.md)  
产品概念 / 项目鉴权：[project-summary.md](./project-summary.md) · 契约草案：[flow-contract-2.0.md](./flow-contract-2.0.md)

---

## 节点壳

所有画布节点统一壳字段；`data` 仅存该 `type` 的业务配置（不含 `selected` / `dragging`）。卡片副标题由 UI 运行时计算，不落盘。

```jsonc
{
  "id": "n1",                 // 图内唯一
  "type": "http",             // FlowNodeType code
  "name": "调用登录",          // 顶层必填；展示名 / Run 步骤名；不进 data
  "position": { "x": 0, "y": 0 },
  "data": { /* 见各节 */ }
}
```

---

## 总览

| type | 标签 | 作用 |
|------|------|------|
| `scenario-entry` | Scenario Entry | 人跑/套件入口；可选 `inSeedKeys` + 场景子集 `inSeed` |
| `http-entry` | HTTP Entry | 开放入站；`inputShape` 三节 + 完整 `fallbackOut` |
| `subflow-entry` | Subflow Entry | 被父流调用；`inputDecl` + 业务对象 `fallbackOut` |
| `http` | HTTP | 调用项目接口或外联 URL |
| `assert` | Assert | 对上下文做比较断言（多条 AND） |
| `condition` | Condition | IF / ELIF / ELSE 分支 |
| `assign` | Assign | 写 `flow` 或 `out`；可打包 `flow.out` |
| `delay` | Delay | 等待一段时间 |
| `script` | Script | GraalVM 沙箱脚本（JS / Python） |
| `subflow` | Subflow | 引用同项目另一张测试流 |
| `input` | Input | 暂停等待人工输入，写入 `flow` |
| `http-return` | HTTP Return | 晋升 `out` 并立刻写出 |
| `subflow-return` | Subflow Return | 晋升 `out` 并挂父 `nodes.<id>.out` |
| `pluginAction` | 插件步骤 | 调用已启用插件能力 |

执行时还有审计步（非画布拖拽节点）：`run_config`、`snapshot` / `restore` 等，见 Run 详情。

---

## Scenario Entry（`scenario-entry`）

人跑 / 套件从这里进；**无** `fallbackOut`，**不画**返回节点。场景列表挂在本入口上（每入口一份子集）。入参声明用可选 **`inSeedKeys`**（与 HTTP `inputShape` / 子流 `inputDecl` 分治）。

```jsonc
{
  "id": "e-sc",
  "type": "scenario-entry",
  "name": "默认场景入口",
  "position": { "x": 0, "y": 0 },
  "data": {
    "doc": "",
    "maxSteps": 200,
    "inSeedKeys": [                    // 可选；提示场景该填哪些键（含 required），无测值
      { "name": "mobile", "type": "string", "required": true },
      { "name": "password", "type": "string", "required": true }
    ],
    // 无 inputShape / inputDecl / fallbackOut
    "scenarios": [
      {
        "id": "sc1",
        "name": "冒烟",
        "envId": "<envId>",
        "inSeed": {                    // 有值；开跑灌进 in
          "mobile": "13800000000",
          "password": "Test@123456"
        }
      }
    ]
  }
}
```

开跑：`entryKind=scenario` + `entryNodeId` + `trigger`（`ui` / `suite` / `mcp`…）；选场景后灌 `inSeed`。跑前面板按 `inSeedKeys` 补空行并标 required（本轮不硬拦开跑）。节点失败固定记失败并走失败后继续（`alwaysRun`）；存档失败策略见环境存档点插件 `onSnapshotFailure`。

---

## HTTP Entry（`http-entry`）

有该节点即开放入站（无「是否开放」开关）。**仅** HTTP 使用 **`inputShape`** 三节；真实值来自入站请求，形状不符 → 平台 **400** 不进图。

```jsonc
{
  "id": "e-http",
  "type": "http-entry",
  "name": "退款入站",
  "position": { "x": 0, "y": 0 },
  "data": {
    "doc": "",
    "maxSteps": 200,
    "method": "POST",
    "path": "/v3/refund",
    "envId": "<envId>",
    "inputShape": {                    // 仅 HTTP；入站按此校验，不符 400
      "body": [                        // 字段树：可嵌套；父级 type 多为 object / json
        {
          "name": "order",
          "type": "object",
          "required": true,
          "children": [
            { "name": "orderId", "type": "string", "required": true },
            { "name": "amount", "type": "number", "required": false }
          ]
        }
      ],
      "query": [                       // 轻量表：扁平，无 children
        { "name": "traceId", "type": "string", "required": false }
      ],
      "headers": [                     // 同上，扁平
        { "name": "X-Request-Id", "type": "string", "required": false }
      ]
    },
    "fallbackOut": {                   // 必配；零次 return 时对外写出
      "status": 500,
      "headers": { "Content-Type": "application/json;charset=UTF-8" },
      "body": { "code": "NO_RETURN", "message": "未执行到返回节点" }
    }
  }
}
```

不在入口上做平台鉴权 / 自动 401、不选手填响应 Content-Type、不配入站名额。同项目 `method + path` 冲突 → 保存放行、列表标 error、开跑硬拦。

---

## Subflow Entry（`subflow-entry`）

被父流 `subflow` 节点调起时的入口。入参用扁平 **`inputDecl`**（不用 HTTP 三节表）；须一份**业务对象** `fallbackOut`（引擎收束包成 `out.body` 再挂父；存量完整 out 形兼容）。

```jsonc
{
  "id": "e-sub",
  "type": "subflow-entry",
  "name": "登录子流入口",
  "position": { "x": 0, "y": 0 },
  "data": {
    "doc": "",
    "maxSteps": 200,
    "inputDecl": [                     // 仅子流；对齐父 subflow.inputs 的键清单
      { "name": "mobile", "type": "string", "required": true },
      { "name": "password", "type": "string", "required": true }
    ],
    // 无 inputShape / query / headers
    "fallbackOut": {                   // 必配；业务兜底（无 status/headers）
      "code": "NO_RETURN",
      "message": "未执行到返回节点"
    }
  }
}
```

挂父后父侧可读：`{{nodes.<子流节点id>.out.body.code}}`（引擎补了 `body` 包装）。父侧 `inputs` 灌子 `in`；命中 `subflow-return` 立刻挂父；成败与是否已挂 `out` 分离。嵌套上限 **2** 层。本轮不做父 `inputs` ↔ `inputDecl` 开跑硬拦。

---

## HTTP（`http`）

按 `data.callMode` 分两条链路，共用转发与 `extracts`。

```jsonc
{
  "id": "n-http",
  "type": "http",
  "name": "调用登录",
  "position": { "x": 240, "y": 0 },
  "data": {
    "callMode": "project",           // project | external
    "testProjectApiId": "<apiId>",   // project：绑定接口资产
    // external 时改用：
    // "externalUrl": "https://…",
    // "httpMethod": "POST",
    // "headers": [{ "key": "Authorization", "value": "Bearer …" }],
    // "requestBody": { "kind": "json", "raw": "{…}" },
    // "preScript": "", "postScript": "",   // 仅 external 执行节点脚本
    "timeoutMs": 30000,
    "statusCheck": {                 // HTTP 状态层；默认 mode=2xx
      "mode": "2xx"                  // 2xx | whitelist | off
      // "values": [200, 401, 403]   // whitelist 时
    },
    "successCheck": {                // 业务码层；仅 2xx 后可选
      "mode": "off",                 // off | whitelist
      "codePath": "$.code",
      "values": ["0"]
    },
    "extracts": [
      {
        "path": "$.data.token",      // 相对 body 根，须以 $ 开头
        "name": "token",
        "scope": "flow"              // flow | asset；asset 默认可回写素材库
        // "persist": false          // 仅改本 Run 内存
      }
    ],
    // "extractsOnFailure": "write", // 失败也写入内存；默认仅最终通过后提取
    "snapshotBefore": false,         // 须 JSON 布尔；打档失败看环境插件 onSnapshotFailure
    "alwaysRun": false,              // 须 JSON 布尔；失败后继续阶段仍执行
    "retryUntil": {                  // 可选；仅 http
      "maxAttempts": 1,              // 默认 1；上限 60
      "intervalMs": 1000,            // 上限 60000
      "until": [                     // 可选；全满足才算最终通过
        { "left": "http.body.data.status", "operator": "eq", "right": "PAID" }
      ]
    }
  }
}
```

| callMode | 说明 |
|----------|------|
| `project` | 绑定 `testProjectApiId`，合并接口资产；pre/post 脚本来自**接口定义**（节点 `preScript` **不执行**） |
| `external` | 用节点 URL / method / headers / body；校验外联权限；步骤报告脱敏 |

常用能力摘要：

- **两层成功判定不合并**：状态码 `statusCheck` → 可选业务码 `successCheck`
- **XML 自动解析**：合适 Content-Type / 正文特征时 body 转对象；原文在 `http.bodyText`
- **extracts**：不定路径命中恰好 1 个时解包为标量；0 个为 `[]`，多个保留数组
- 占位符解析请求；项目鉴权补头（Profile 托管头带 `profileManaged`）
- `retryUntil`：整节点时间线仍占 1 步；硬错误不重试

### HTTP 脚本（preScript / postScript）

宿主对象名为 **`api`**（GraalVM JS；无 DOM、无 `btoa`/`atob`、无 Postman `pm`）。

| 挂载位置 | 何时执行 |
|----------|----------|
| `callMode=project` | 接口库 `preRequestScript` / `postRequestScript` |
| `callMode=external` | 节点 `data.preScript` / `data.postScript` |

#### 变量

| API | 说明 |
|-----|------|
| `api.variables.get/set/unset/has(key[, value])` | 临时变量（跑流侧≈flow） |
| `api.environment.get/set/unset/has` | 环境变量；`set` 仅当前会话 |
| `api.globals.get/set/unset/has` | 全局（调试会话） |

#### 请求（仅前置可写）

| API | 说明 |
|-----|------|
| `api.request.url` / `method` | 读、赋值修改；改 query 请改完整 `url` |
| `api.request.headers.add({key,value})` 或 `add(k,v)` | 增改头 |
| `api.request.headers.remove(name)` / `get` / `list` | 删、读 |
| `api.request.body` | 读快照；**写入须整对象重赋** `api.request.body = {…}` |

#### 响应（仅后置）

| API | 说明 |
|-----|------|
| `api.response.code`（或 `status`） | HTTP 状态码 |
| `api.response.statusText` / `headers` | 文案、响应头 |
| `api.response.json()` / `text()` | 解析 JSON / 原文 |

#### 工具与断言

| API | 说明 |
|-----|------|
| `api.base64Encode` / `base64Decode` | Base64（不要用 `btoa`） |
| `api.jsonParse` / `jsonStringify` | JSON |
| `api.sendRequest({ url, method, headers, body })` | 同步辅助请求 |
| `api.log(message)` | 写入步骤 `http.preScript` / `http.postScript` 的 logs |
| 编码 / 报文 / 加密工具 | 与 Script 节点 `ctx` 同名，见 Script 节 |
| `api.test(name, fn)` / `api.expect(x).to.equal(y)` | **仅后置**断言 |

```javascript
var pwd = api.environment.get('password');
api.request.body = {
  kind: 'json',
  raw: api.jsonStringify({
    mobile: api.environment.get('mobile'),
    password: api.base64Encode(pwd)
  })
};
```

UI 调试台侧栏「脚本 API」与片段库与上表同源（`scriptApiReference.js` / `apiScriptSnippets.js`）。

---

## Assert（`assert`）

对 `data.rules[]` 逐条求值（`CompareRuleEvaluator`），**全部通过才算通过**。不负责 HTTP 状态码。

```jsonc
{
  "id": "n-assert",
  "type": "assert",
  "name": "校验业务码",
  "position": { "x": 480, "y": 0 },
  "data": {
    "rules": [
      {
        "left": "http.body.data.code",   // 见下方路径方言
        "operator": "eq",                // 默认 eq；见运算符
        "right": "0"                     // 可含占位符
      }
    ]
  }
}
```

### 路径方言

| 场景 | 写法 | 说明 |
|------|------|------|
| 断言 / 条件左值 | `http.body.data.code`、`http.body.data[?(@.cartId=='5001')].quantity` | 相对本上下文**最近一次** HTTP 响应 body。支持下标、过滤器、`[*]`、`length()`。**数组字段不要写 `.items`** |
| 按节点引用 | `nodes.<节点ID>.http.body.data.code` | 读该 HTTP 节点最近一次快照；只认 id |
| 简写（会规范化） | `$.data.code` | 规范为 `http.body.data.code`（仅裸 http） |
| Extract / 业务码 | `$.data.token` | **必须以 `$` 开头**，相对 body 根 |
| 其它上下文 | `flow.*` / `env.*` / `asset.*` / `in.*` / `out.*` / `nodes.<id>.out.*` / `http.status` / `http.duration` / `http.bodyText` | **不走 JsonPath** |

禁止：`http.body.$.…`（Confirm / **运行**失败；保存可带错落盘）。

### 运算符

UI：`eq/ne/gt/gte/lt/lte/contains/not_contains/exists`。  
别名：`equals`/`==` → `eq`；`notempty`/`not_empty` → `exists`。

集合语义：`exists` 要求非空；`eq` 多元素列表仅恰好 1 个时拆箱；`contains` 左值为列表时任一元素包含即可。

设计期：`http.body…` 静态可断定写法错误（如 `.items` / `http.body.$.…`）进画布图 error；**Staging ✓ / 保存不拦**；无 schema、字段是否存在静默。**运行**拦结构 + 断言路径结构错 + readiness。响应 example 只给人看，不参与硬拦。

---

## Condition（`condition`）

扫描 `data.branches[]`（顺序 IF → ELIF → ELSE）。命中分支有非空 `target` 则继续；无 `target` 则本流正常结束。结果写入步骤 `branchTaken`。

```jsonc
{
  "id": "n-if",
  "type": "condition",
  "name": "是否成功",
  "position": { "x": 360, "y": 0 },
  "data": {
    "branches": [
      {
        "id": "<snowflake>",
        "kind": "if",                    // if | elif | else
        "conditions": [                  // if/elif：全部成立（AND）
          { "left": "flow.code", "operator": "eq", "right": "0" }
        ],
        "target": "n-ok"                 // 下一节点 id；可空=正常结束
      },
      {
        "id": "<snowflake>",
        "kind": "else",
        "conditions": [],
        "target": "n-fail"
      }
    ]
  }
}
```

与隐式扇出不同：`condition` 只走一臂；普通节点多出边各臂都跑。

---

## Assign（`assign`）

处理 `data.assignments[]`。正式 Run 使用严格占位符。占位符分桶见 [flow-variables-and-values.md](./flow-variables-and-values.md)。

```jsonc
{
  "id": "n-assign",
  "type": "assign",
  "name": "组出参",
  "position": { "x": 240, "y": 0 },
  "data": {
    "assignments": [
      {
        "scope": "flow",                 // 缺省 / flow → 写 flow；out → 写 ctx.out
        "name": "out",                   // flow.out 整包供返回节点 from 晋升
        "op": "set",                     // set | add | sub | mul | div
        "value": {                       // set：可为对象字面量；可含占位符
          "headers": { "Content-Type": "application/json;charset=UTF-8" },
          "body": { "ok": true }
        }
        // 算术 op 时常用：
        // "step": 1,
        // "ifMissing": 0
      }
    ],
    "snapshotBefore": false,             // 须 JSON 布尔
    "alwaysRun": false                   // 须 JSON 布尔
  }
}
```

| scope | 写入目标 | 典型用法 |
|-------|----------|----------|
| 缺省 / `flow` | 运行时 `flow` | `name: "out"` + 对象 → `{{flow.out}}` |
| `out` | `ctx.out` 字段 | 逐键写 `status` / `headers` / `body` |

`scope` 只允许 `flow` / `out`；显式写出其它值（含空串）按画布异常报错并被开跑门禁拦下。缺省即 `flow`，运行时按同一口径归一。`value` 为对象 / 数组字面量时，内部的 `{{…}}` 会逐层解析（不再原样写入）。

---

## Delay（`delay`）

按 `data.ms` 阻塞当前执行线程。单步上限 **60_000 ms**。

```jsonc
{
  "id": "n-delay",
  "type": "delay",
  "name": "等待 1s",
  "position": { "x": 240, "y": 0 },
  "data": {
    "ms": 1000
  }
}
```

---

## Script（`script`）

在 GraalVM 沙箱中执行：可读写 `flow`、`session`；可读 `env` / `asset`；可选受控 `ctx.http`；`ctx.log` → 步骤 `script.logs`。能用 HTTP / Assign / Assert 表达的优先用声明式节点。

```jsonc
{
  "id": "n-script",
  "type": "script",
  "name": "拼签名",
  "position": { "x": 240, "y": 0 },
  "data": {
    "language": "javascript",            // javascript（默认）| python
    "source": "ctx.setFlow('demo', ctx.jsonStringify({ ok: true }));",
    "timeoutMs": 5000,                   // 有上下限归一化
    "snapshotBefore": false,             // 须 JSON 布尔
    "alwaysRun": false                   // 须 JSON 布尔
  }
}
```

### 脚本工具函数

Script 节点 `ctx.*` 与接口前后置脚本 `api.*` 同名可用。入参出参均为字符串，明文按 UTF-8；二进制结果默认 hex（摘要 / HMAC）或 Base64（签名 / 密文）。

| 函数 | 说明 |
|------|------|
| `jsonParse` / `jsonStringify`、`base64Encode` / `base64Decode` | JSON、Base64 |
| `urlEncode` / `urlDecode` | UTF-8 URL 编解码，空格为 `+` |
| `xmlParse(text)` | XML → 对象：去根节点，同名节点转数组，CDATA 取文本，忽略属性；禁止 DOCTYPE |
| `xmlStringify(obj, { root: 'xml', cdata: true })` | 对象 → XML；字符串默认包 CDATA，数组展开为重复节点 |
| `signContent(params, { exclude: ['sign'], skipEmpty: true })` | 按 key ASCII 升序拼 `k=v&k2=v2`（不 URL 编码） |
| `md5` / `sha1` / `sha256` / `sha512` / `sm3(data, encoding?)` | 摘要；`encoding` 为 `hex`（默认）或 `base64` |
| `hmacSha1` / `hmacSha256` / `hmacSha512(data, secret, encoding?)` | HMAC，密钥按 UTF-8 |
| `rsaSign(data, privateKey, alg?)` / `rsaVerify(data, sign, publicKey, alg?)` | 默认 `SHA256withRSA`，签名为 Base64 |
| `rsaEncrypt` / `rsaDecrypt(data, key, padding?)` | `OAEP`（默认）/ `OAEP256` / `PKCS1` |
| `aesGcmEncrypt` / `aesGcmDecrypt(data, key, nonce, aad?)` | 密文为 Base64(密文 + tag) |
| `aesEncrypt` / `aesDecrypt(data, key, opts?)` | `mode`/`iv`/`padding`/`encoding` 等 |
| `sm2Sign` / `sm2Verify` / `sm2Encrypt` / `sm2Decrypt` | 国密签名与加解密 |
| `sm4Encrypt` / `sm4Decrypt(data, key, opts?)` | 参数同 `aesEncrypt`，`keyEncoding` 默认 `hex` |

国密密钥支持 hex、PEM 与裸 Base64。PEM 在 JSON 素材里可用字面量 `\n` 换行。

---

## Subflow（`subflow`）

将同项目另一张测试流折叠为单步（**另一张图**，不是同画布子图）。开跑前子图须过就绪门禁。

```jsonc
{
  "id": "n-sub",
  "type": "subflow",
  "name": "登录子流",
  "position": { "x": 240, "y": 0 },
  "data": {
    "subflowId": "<testFlowId>",         // 须同 testProjectId
    "inputs": [                          // 在父上下文解析占位符 → 子 in / flow
      { "name": "mobile", "value": "{{flow.mobile}}" }
    ],
    "alwaysRun": false                   // 须 JSON 布尔；失败后继续阶段仍执行
  }
}
```

1. 按 `subflowId` 加载；**每次 Run 取被引用流当前图**
2. `entryKind=subflow`；`asset` / `env` / `session` 共享
3. 嵌套：主→子→孙，最多 **2** 层
4. 子图 `subflow-return`（或零次时入口 `fallbackOut`）挂父 `nodes.<本节点id>.out`；成败与是否已挂 out 分离
5. 步骤 `subflow.childSteps` 附内层摘要

平台提供登录 / OAuth / 验证码等子流模板，可 fork。MCP：`submit_subflow_node` 可写 `inputs` / `alwaysRun`。

---

## Input（`input`）

运行时暂停等待人工填写，将值写入 `flow` 后继续。无媒体预览配置（看图请打开上一步 HTTP 步骤）。

```jsonc
{
  "id": "n-input",
  "type": "input",
  "name": "填写验证码",
  "position": { "x": 240, "y": 0 },
  "data": {
    "prompt": "请填写后继续",
    "fields": [
      {
        "name": "captcha",
        "label": "验证码",
        "type": "text",                  // text|textarea|password|number|boolean|select|multiselect|date|datetime
        "required": true,
        "placeholder": "",
        "defaultValue": ""
        // select / multiselect 另需：
        // "options": [{ "label": "A", "value": "a" }]
      }
    ]
  }
}
```

`type` 缺省 `text`；未知 type **运行**硬拦（保存可带错落盘）。暂停原因 `await_input`；决策 `continueWithInput` / `abort`。套件 / 无人值守碰到即 paused；打码可用 `tpl_login_captcha` / `tpl_login_captcha_manual`。

---

## HTTP Return（`http-return`）

定稿 `out` 并立刻写出；**默认不结束路径**（后面还可 Assert）。场景图不提供本节点。

```jsonc
{
  "id": "n-ret",
  "type": "http-return",
  "name": "支付成功回包",
  "position": { "x": 480, "y": 0 },
  "data": {
    "from": "flow.out",                  // 必填；保存缺省写 flow.out；解析须为 Map，整表晋升到 ctx.out
    "status": 200                        // 可选；默认 200；晋升后覆盖 out.status
  }
}
```

开放入站整次至多执行一次返回；零次则收束用入口 `fallbackOut`，Run 仍失败。多种业务回包用 IF 各臂各接一个返回节点。详见 [flow-contract-2.0.md §4](./flow-contract-2.0.md)。

混图（场景入口 + HTTP/子流入口共用一条链）里要放返回节点，必须放在 `run.entryKind` 守卫的 IF 臂里（如 `run.entryKind eq subflow`，或 `ne scenario`）：场景 Run 静态走不到该臂，校验放行；没有守卫则按「场景入口可达子图不得含返回节点」报**画布异常**（开跑硬拦）。

---

## Subflow Return（`subflow-return`）

定稿 `out` 并立刻挂父 `nodes.<子流节点id>.out`；**无** `status` 配置项；默认不结束路径。

```jsonc
{
  "id": "n-ret",
  "type": "subflow-return",
  "name": "返回父流",
  "position": { "x": 480, "y": 0 },
  "data": {
    "from": "flow.out"                   // 必填；无 status
  }
}
```

子流整次至多一次返回；零次挂入口 `fallbackOut`；子/父仍记失败。失败后父仍可读已挂的 `out`。

挂父形状：`from` 解析结果**整包**挂 `nodes.<子流节点 id>.out`（业务对象如 `{code,data}` 原样挂，不拆 status/headers/body）；零次 return 时入口 `fallbackOut` 若是业务对象，会规范成 `{body: <该对象>}` 再挂，存量完整 out 形照挂。

---

## 插件步骤（`pluginAction`）

调用项目已启用插件的能力（如存档点）。具体 `config` 以各插件 `docs/design.md` 为准。

```jsonc
{
  "id": "n-plugin",
  "type": "pluginAction",
  "name": "跑前存档",
  "position": { "x": 240, "y": 0 },
  "data": {
    "pluginId": "qualitest.sut-db-archive-point",
    "contributionId": "",                // 贡献点 id（按插件约定）
    "capability": "snapshotBefore",      // 能力名
    "config": {}                         // 能力参数
  }
}
```

---

## 设计建议

1. **主路径**：HTTP（project）→ Assert；变量用 extracts / Assign。
2. **分支**：Condition 后各臂汇合前注意合并语义；复杂逻辑可拆子流。
3. **复用**：登录、鉴权等多步 → Subflow，父流只传输入输出（鉴权口径见 [project-summary.md](./project-summary.md)）。
4. **兜底**：签名、动态拼装 → Script；保持脚本短小、可测。
5. **破坏性写**：开 `snapshotBefore` + 环境允许还原。项目跑流并发默认 **按环境串行**（可改并行；排队见运行中心）。

更多：[project-summary.md](./project-summary.md) · [ai-staging.md](./ai-staging.md) · [flow-variables-and-values.md](./flow-variables-and-values.md) · [deploy.md](./deploy.md) · [mcp.md](./mcp.md) · [Demo AI 提示集](../../qualitest-demo-mall/docs/ai-test-flow-prompts.md)
