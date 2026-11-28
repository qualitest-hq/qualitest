# 跑流变量与 HTTP 测值

> 跑一条测试流时，两件事别混：**`{{…}}` 变量从哪取值**，以及 **HTTP 节点最终发出什么参数**（接口默认 vs 节点覆盖）。  
> 英文：[flow-variables-and-values.en.md](./flow-variables-and-values.en.md)

断言 / Extract 的另一种写法见 [test-flow-nodes.md](./test-flow-nodes.md)。素材库结构见 [assets.md](./assets.md)。

---

## 1. `{{…}}` 怎么写

正式 Run 解析 **严格**：变量不存在 → `TF_PLACEHOLDER_UNDEFINED`。调试台发送前按当前环境与素材库做宽松替换（未定义当空串），并在响应区提示未解析的占位路径。需要登录的接口会在调试台 Headers 里显示一行托管鉴权头（如 `Authorization: Bearer {{asset.clientAuth.token}}`），不能删、不能改，取消勾选则这趟不发送。

语法：`{{作用域.路径}}`（双花括号）。

| 作用域 | 含义 | 示例 |
| ------ | ---- | ---- |
| `in` | 本次 Run 入参（场景 / 开放请求 / 子流父灌入） | `{{in.body.orderId}}` |
| `out` | 定稿出参（返回晋升后；含 `status` / `headers` / `body`） | `{{out.body.code}}` |
| `flow` | 本条 Run 里产生的业务变量 | `{{flow.token}}`、`{{flow.out}}`（Assign 打包的回包对象） |
| `env` | 当前测试环境 | `{{env.baseUrl}}`（键名以环境配置为准） |
| `asset` | 项目素材库（Run 启动拷入内存；extract `persist:false` / `ctx.setAsset` 只改本 Run） | `{{asset.clientAuth.password}}`、`{{asset.cover.storagePath}}` |
| `http` | 本上下文**最近一次** HTTP 响应快照 | 多用于断言；中间非 HTTP 节点不覆盖 |
| `nodes` | 按画布节点 id 引用该节点 HTTP 快照或挂载的 `out` | `{{nodes.123.http.body.data.code}}`、`{{nodes.sub1.out.body.code}}` |

`in` / `out` / `flow` **三桶分离**：不要把入参写进 `flow`，也不要把业务变量当作出参交付源（交付前先经 `http-return` / `subflow-return` 的 `from` 晋升到 `out`）。

**没有** `{{session.*}}`——`session` 只在 Script 节点里用 `ctx.session`，不能写在 `{{…}}` 里。

素材用 **点分**路径（不是 JsonPath）：`{{asset.clientAuth.mobile}}`。文件上传引用磁盘路径：`{{asset.<key>.storagePath}}`。

---

## 2. `flow` 变量从哪来

设计期检查「token 有没有来源」时，也认下面这些：

| 来源 | 说明 |
| ---- | ---- |
| HTTP **extracts** | 从响应抽到 `flow` 或 `asset` |
| **Assign** 节点 | 写入 `flow` |
| **Subflow** 定稿 | 子图 `subflow-return` 的 `from` 晋升后挂父 `nodes.<子流节点id>.out`（与父子步成败分离；失败后父仍可读） |
| **Subflow** 输入 | 子流节点 `inputs`：在父上下文解析后写入子 `flow`（父子 `flow` 默认不共享） |
| 场景 **inSeed** | Run 开始前灌 `ctx.in`；占位符 `{{in.*}}`；可用于预置 token 等，不要塞明文口令 |

登录 extract 应对齐项目 Profile 托管头占位符（如 `{{asset.adminAuth.token}}`）。登录口由节点 extracts 与 Profile `headerValueTemplate` 对齐推断。

---

## 3. HTTP 请求测值从哪来

节点上填的值 ≠ 最终请求。合并顺序：

```text
① 接口资产里的默认测值（bodyExample、paramDefaults 等）
  → ② 节点 requestValueOverrides（只覆盖与默认不同的部分）
       · paramDefaults：按参数名覆盖 query / path / header / form-data
       · bodyExample：覆盖 JSON body
  → ③ 解析 {{…}}，组包发出
```

节点里合法顶层键只有 **`paramDefaults`**、**`bodyExample`**。别把 `cartIds` 这类字段直接写在 overrides 根上（运行时会忽略）。

与默认相同的字段**不必**在节点重复写。跑流与调试台共用**同一套叠层算法与装箱规则**（Cookie 合成单条头、path/query 空格 `%20`、空 query 不发出）；测值来源可以不同（台本屏试调 vs 流节点覆盖）。结构层 `request_config.body.json.example`（OpenAPI 残留）不发出，层 ① 是 `test_value_config`。

---

## 4. 口令与场景账号放哪

| 场景 | 推荐 |
| ---- | ---- |
| 主测号、可复用 | 素材库 + `{{asset.*}}` |
| 失败用例（停用号、错密码） | 节点 **字面量**；配合「预期业务拒绝」模板 |
| 调试跳过登录 | inSeed / 素材库预置 token，或探活分支跳过登录 HTTP |

---

## 5. 别和断言 / Extract 混写

| 用在哪 | 写法 |
| ------ | ---- |
| 请求参数、Header、Body | `{{flow.token}}` / `{{asset.*.token}}` / `{{env.*}}`（入参传递优先 extract → `flow`，少用 `nodes.*`） |
| HTTP 节点 **extract**、业务码字段 | 以 **`$` 开头**的 JSONPath，如 `$.data.token` |
| 断言 / 条件左值 | `http.body.data.code`，或简写 `$.data.code`；延迟核对更早 HTTP 用 `nodes.<节点ID>.http.body…` |

禁止：`http.body.$.…`。`nodes.` 只认节点 **id**（不认显示名）；目标不存在或不是 HTTP → 画布顶部**校验条**报错。细节见节点文档。

---

## 6. 常见报错

| 现象 | 先查 |
| ---- | ---- |
| `TF_PLACEHOLDER_UNDEFINED: {{…token…}}` | 是否已有对应凭证来源（extract / inSeed / env）；登录口是否误补 Bearer |
| HTTP 200 但业务码失败 | 失败用例是否应关「业务 Code 校验」 |
| 某字段没出现在请求里 | `requestValueOverrides` 形状不对；或字段写在 overrides 根上 |
| 文件变成普通字符串 | form-data 该行 `type=file`，且值为 `{{asset.*.storagePath}}` |
