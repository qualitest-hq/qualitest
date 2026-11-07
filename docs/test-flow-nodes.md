# 测试流节点说明

画布固定 **8** 种节点类型（`FlowNodeType`），不可自定义 `type`。持久化值为小写 code；UI 展示用英文 label。

英文版：[test-flow-nodes.en.md](./test-flow-nodes.en.md)  
产品概念 / 项目鉴权：[project-summary.md](./project-summary.md)

---

## 总览

| type | 标签 | 作用 |
|------|------|------|
| `http` | HTTP | 调用项目接口或外联 URL |
| `assert` | Assert | 对上下文做比较断言（多条 AND） |
| `condition` | Condition | IF / ELIF / ELSE 分支 |
| `assign` | Assign | 写入 `flow` 变量 |
| `delay` | Delay | 等待一段时间 |
| `script` | Script | GraalVM 沙箱脚本（JS / Python） |
| `subflow` | Subflow | 引用同项目另一张测试流，折叠为单节点 |
| `input` | Input | 暂停等待人工输入，写入 `flow` 后继续 |

执行时还有审计步（非画布拖拽节点）：`run_config`、`snapshot` / `restore` 等，见 Run 详情。

---

## HTTP（`http`）

按 `data.callMode` 分两条链路，共用转发与 `extracts` 抽变量：

| callMode | 说明 |
|----------|------|
| `project` | 绑定 `testProjectApiId`，合并项目接口资产；pre/post 脚本来自接口定义 |
| `external` | 使用节点 `externalUrl` / `httpMethod` / `headers` / `requestBody`；校验外联权限；步骤报告会脱敏敏感字段 |

常用能力：

- 成功判定（两层，不合并）：
  - **HTTP 状态** `statusCheck`：默认 `mode=2xx`（非 2xx → 失败）；`whitelist` + `values` 仅放行列表内状态码（探活常用 `[200,401,403]`）；`off` 任意状态码步骤仍 passed。有响应即写入 `lastResponse`，后续 Condition 可读 `http.status`、`http.expectedMatch`（对照接口 `expectedResponseKind`：`json` / `xml` / `html` / `any`）、`http.responseKind`（`json` / `xml` / `nonJson`）
  - **XML 响应自动解析**：Content-Type 为 `text/xml`、`application/xml`、`*+xml`，或未声明 / `text/plain` 且正文以 `<?xml`、`<xml>` 开头时，body 转为对象（去掉根节点；同名节点转数组；叶子取文本，含 CDATA；忽略属性；禁止 DOCTYPE）。之后 `http.body.return_code`、断言、条件、extracts（`$.return_code`）可直接取字段；原文在 `http.bodyText`，步骤报告 `response.bodyText` 同样保留。解析失败保留原字符串，不影响步骤成败
  - **业务码** `successCheck`：仅在 **2xx** 后可选校验 body 业务码白名单；`mode=off` 关闭。`codePath` 支持 `code` 或 `$.code`
- **extracts**：默认仅步骤最终通过后执行；`extractsOnFailure=write` 时失败也写入内存。`scope=asset` 默认回写项目素材库；`persist: false` 只改本 Run 内存。不定路径（过滤器 / `[*]`）命中 **恰好 1 个**时自动解包为标量（与断言一致），便于 `{{flow.x}}` 写路径参数；**0 个**仍为 `[]`，**多个**保留数组。过滤器后不要再写 `[n]`（JsonPath 会把下标作用到每个命中元素，常得到 `[]`）
- **前置/后置脚本**：见下方「HTTP 脚本（preScript / postScript）」
- 占位符解析请求参数 / 体（`flow` / `env` / `asset` / `session` 等）
- **项目鉴权补头**（Profile 托管头 `{{asset.*}}` / `{{flow.*}}` → 见 [project-summary.md §4](./project-summary.md)）
- 可选 **执行前快照**（`snapshotBefore`）：写库失败可暂停并还原被测数据（环境允许还原时；详见概念地图 §4.4）

节点上托管鉴权头带 `profileManaged`，Run 时按当前项目/接口配置刷新；无该标记的显式头永不被静默改掉。画布可显示节点将使用/产出的凭证路径。

### HTTP 脚本（preScript / postScript）

宿主对象名为 **`api`**（GraalVM JS；无 DOM、无 `btoa`/`atob`、无 Postman `pm`）。

| 挂载位置 | 何时执行 |
|----------|----------|
| `callMode=project` | 接口库 `preRequestScript` / `postRequestScript`（**节点** `data.preScript` **不执行**） |
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
| `api.request.body` | 读快照；**写入须整对象重赋** `api.request.body = {…}`（`body.xxx =` 嵌套改不会回写） |

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
| `api.log(message)` | 写入步骤 `http.preScript` / `http.postScript` 的 logs（HTTP 脚本用此；Script 节点用 `ctx.log`） |
| 编码 / 报文 / 加密工具 | 与 Script 节点 `ctx` 同名，见下文「脚本工具函数」 |
| `api.test(name, fn)` / `api.expect(x).to.equal(y)` | **仅后置**断言 |

#### 示例：密码 Base64 后发出

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

对 `data.rules[]` 逐条求值（`CompareRuleEvaluator`），**全部通过才算通过**。  
不负责 HTTP 状态码（由 HTTP 节点在本步处理）。规则字段常见：`left`、`operator`（默认 `eq`）、`right`（可含占位符）。

### 路径方言

| 场景 | 写法 | 说明 |
|------|------|------|
| 断言 / 条件左值 | `http.body.data.code`、`http.body.data[?(@.cartId=='5001')].quantity` | 相对本上下文**最近一次** HTTP 响应 body；中间非 HTTP 节点不覆盖。支持下标、过滤器、`[*]`、`length()`。**数组字段不要写 JSON Schema 关键字 `.items`** |
| 按节点引用 | `nodes.<节点ID>.http.body.data.code` | 读该 HTTP 节点最近一次执行快照；只认 id。目标缺失/非 HTTP → 校验条 structure 错误，运行硬拦 |
| 简写（会规范化） | `$.data.code` | 设计态 / 运行态规范为 `http.body.data.code`（仅裸 http，不是 nodes） |
| Extract / 业务码 | `$.data.token` | **必须以 `$` 开头**，相对 body 根 |
| 其它上下文 | `flow.*` / `env.*` / `asset.*` / `http.status` / `http.duration` / `http.bodyText` | **不走 JsonPath**；`http.bodyText` 为响应原文 |

禁止：`http.body.$.…`（Confirm / **运行**会失败；保存可带错落盘）。字段向后传递仍优先 HTTP **extracts** / Assign → `flow.*`。

### 运算符

UI 提供：`eq/ne/gt/gte/lt/lte/contains/not_contains/exists`。  
别名：`equals`/`==` → `eq`；`notempty`/`not_empty` → `exists`（勿再写 `notempty`）。

集合语义：

- `exists`：非 null、非空串；列表/数组非空才通过  
- `eq`：多元素列表仅当恰好 1 个元素时拆箱再比，否则失败  
- `contains`：左值为列表时**任一**元素包含右值即可  

购物车「是否含某 cartId」推荐：`http.body.data[?(@.cartId=='5001')]` + `exists`（若 `data` 直接是数组；勿写成 `data.items[…]`）。

设计期：Staging ✓ 确认 **assert/condition 节点**时，会在含尚未确认 Staging 上下游的**预览图**上按上游接口**响应 schema** 校验 `http.body…` 左值（禁 `.items` / `http.body.$.…` 等结构错误 → **硬拦**；schema 缺字段或无 schema → **警告**，允许确认与保存；过滤器与 `data[*].字段` 对齐）；预览图仍无上游 project HTTP 则硬拦。错误挂在该断言/条件单元，**不会**因路径问题拦住边确认。AI submit 对本单元硬拦；**保存**不拦全图断言路径；**运行**对全图跑结构 + 断言路径 + AUTH/必填 readiness。响应 **example 只给人看**，属性面板可对照 example 软试算（空结果标红），**不参与硬拦**。

---

## Condition（`condition`）

扫描 `data.branches[]`（顺序 IF → ELIF → ELSE）：

- **if / elif**：`conditions[]` 全部成立则命中（AND）
- **else**：前序均未命中时兜底

命中分支有非空 `target`（下一节点 id）则继续走；无 `target` 则本流正常结束。结果写入步骤 `branchTaken`（`branchId` / `kind`）。

---

## Assign（`assign`）

处理 `data.assignments[]`，**只写 `flow` 作用域**：

| op | 行为 |
|----|------|
| `set` | 解析 value 占位符后覆盖 |
| `add` / `sub` / `mul` / `div` | 在现有值或 `ifMissing` 上按 `step` 运算 |

每条赋值的 before/after 写入步骤报告。正式 Run 使用严格占位符：未定义占位符会失败。

---

## Delay（`delay`）

按 `data.ms` 阻塞当前执行线程。单步上限 **60_000 ms**，防止配置过大拖垮线程。

---

## Input（`input`）

运行时暂停等待人工填写，将值写入 `flow` 后从下一节点继续。无媒体预览配置（看图请打开上一步 HTTP 步骤）。

| 字段 | 说明 |
|------|------|
| `prompt` | 暂停面板提示文案 |
| `fields[]` | 输入项；每项含 `name` / `label` / `type` / `required` / `placeholder` / `defaultValue`；`select`/`multiselect` 另需静态 `options` |

`type` 缺省 `text`；合法值：`text` / `textarea` / `password` / `number` / `boolean` / `select` / `multiselect` / `date` / `datetime`。未知 type **运行**硬拦（保存可带错落盘）。

暂停原因 `await_input`；可用决策 `continueWithInput`（提交 `inputs`）与 `abort`。CI / 无人值守碰到即 paused；自动化主路径可用外联打码子流 `tpl_login_captcha`，人手填码可用 `tpl_login_captcha_manual`。

---

## Script（`script`）

| 字段 | 说明 |
|------|------|
| `language` | `javascript`（默认）或 `python` |
| `source` | 脚本正文 |
| `timeoutMs` | 超时（有上下限归一化） |

在 GraalVM 沙箱中执行：可读写 `flow`、`session`；可读 `env` / `asset`；可选受控 `ctx.http`；`ctx.log` 写入步骤 `script.logs`。`ctx.setFlow` 合并进当前 Run。失败时步骤带脚本错误码与消息。

适合项目特有签名、字段拼装等「画布节点不够用」的逻辑；能用 HTTP / Assign / Assert 表达的优先用声明式节点。

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
| `rsaSign(data, privateKey, alg?)` / `rsaVerify(data, sign, publicKey, alg?)` | 默认 `SHA256withRSA`，签名为 Base64；私钥支持 PKCS#8 / PKCS#1 PEM 或裸 Base64，公钥支持公钥 PEM、证书 PEM 或裸 Base64 |
| `rsaEncrypt` / `rsaDecrypt(data, key, padding?)` | `OAEP`（默认，SHA-1）/ `OAEP256` / `PKCS1` |
| `aesGcmEncrypt` / `aesGcmDecrypt(data, key, nonce, aad?)` | key / nonce / aad 按 UTF-8；密文为 Base64(密文 + tag)，即微信支付 v3 `resource.ciphertext` 格式 |
| `aesEncrypt` / `aesDecrypt(data, key, opts?)` | `mode`（`CBC` 默认 / `ECB`）、`iv`、`keyEncoding` / `ivEncoding`（`utf8` 默认 / `base64` / `hex`）、`padding`（`PKCS5Padding` 默认 / `NoPadding`）、`encoding`（密文 `base64` 默认 / `hex`）；CBC 未传 iv 用全 0 |
| `sm2Sign` / `sm2Verify(data, …, key, opts?)` | `userId`（默认 `1234567812345678`）、`format`（`der` 默认 / `rs`）、`encoding` |
| `sm2Encrypt` / `sm2Decrypt(data, key, opts?)` | `mode`（`C1C3C2` 默认 / `C1C2C3`）、`encoding`；解密兼容缺 `04` 前缀 |
| `sm4Encrypt` / `sm4Decrypt(data, key, opts?)` | 参数同 `aesEncrypt`，`keyEncoding` 默认 `hex` |

国密密钥支持 hex（私钥 d、`04` 开头公钥）、PEM 与裸 Base64。PEM 存在 JSON 素材里时可用字面量 `\n` 换行。仿渠道回调时，被测系统须配置与脚本私钥对应的公钥 / 证书，验签才会通过。

---

## Subflow（`subflow`）

将同项目另一张测试流折叠为单步（**另一张图**，不是同一画布上的子图）：

1. 按 `subflowId` 加载（须同 `testProjectId`）
2. `inputs` 在**父上下文**解析占位符，写入子上下文独立的 `flow` 种子（父子 `flow` 默认隔离；`asset` / `env` / `session` 共享）
3. 内存跑子图；嵌套深度有限（主→子→孙，最多 **2** 层 subflow）
4. 子流**成功**后按节点 `outputs` 或子图 `meta.flowOutputs` 同名默认映射写回父 `flow`
5. 步骤 `subflow.childSteps` 附内层摘要，供 Run 详情与 MCP `get_run_failure`

| versionPolicy | 行为 |
|---------------|------|
| `latest` | 每次 Run 取子流当前图 |
| `pinned` | 优先用节点 `pinnedGraphJson` 固化快照 |

节点 `data.alwaysRun=true`（JSON 布尔）时：父图业务失败进入「失败后继续」阶段仍会执行本子流节点，便于整章串多个子流时一次看到后续模块结果。

平台提供登录 / OAuth / 验证码等 **子流模板**，可 fork 后改参数。

MCP：`submit_subflow_node` 可写 `inputs` / `outputs` / `alwaysRun`；子图对外产出声明用 `update_flow_meta.flowOutputs`（整表替换）。

---

## 设计建议

1. **主路径**：HTTP（project）→ Assert；变量用 extracts / Assign。
2. **分支**：Condition 后各臂汇合前注意合并语义；复杂逻辑可拆子流。
3. **复用**：登录、鉴权等多步 → Subflow，父流只传输入输出（鉴权口径见 [project-summary.md](./project-summary.md)）。
4. **兜底**：签名、动态拼装 → Script；保持脚本短小、可测。
5. **破坏性写**：开 `snapshotBefore` + 环境允许还原。项目跑流并发默认 **按环境串行**（可改并行；排队见运行中心）。

更多：[project-summary.md](./project-summary.md) · [ai-staging.md](./ai-staging.md) · [flow-variables-and-values.md](./flow-variables-and-values.md) · [deploy.md](./deploy.md) · [mcp.md](./mcp.md) · [Demo AI 提示集](../../qualitest-demo-mall/docs/ai-test-flow-prompts.md)
