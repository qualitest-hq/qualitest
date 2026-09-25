# 跑流变量与 HTTP 测值

> 跑一条测试流时，两件事别混：**`{{…}}` 变量从哪取值**，以及 **HTTP 节点最终发出什么参数**（接口默认 vs 节点覆盖）。  
> 英文：[flow-variables-and-values.en.md](./flow-variables-and-values.en.md)

断言 / Extract 的另一种写法见 [test-flow-nodes.md](./test-flow-nodes.md)。素材库结构见 [assets.md](./assets.md)。

---

## 1. `{{…}}` 怎么写

正式 Run 解析 **严格**：变量不存在 → `TF_PLACEHOLDER_UNDEFINED`。调试台部分场景会宽松（未定义当空串）。

语法：`{{作用域.路径}}`（双花括号）。

| 作用域 | 含义 | 示例 |
| ------ | ---- | ---- |
| `flow` | 本条 Run 里产生的变量 | `{{flow.token}}`、`{{flow.orderId}}` |
| `env` | 当前测试环境 | `{{env.baseUrl}}`（键名以环境配置为准） |
| `asset` | 项目素材库 | `{{asset.clientAuth.password}}`、`{{asset.cover.storagePath}}` |
| `http` | 上一步 HTTP 响应快照 | 多用于断言；请求体里较少 |

**没有** `{{session.*}}`——`session` 只在 Script 节点里用 `ctx.session`，不能写在 `{{…}}` 里。

素材用 **点分**路径（不是 JsonPath）：`{{asset.clientAuth.mobile}}`。文件上传引用磁盘路径：`{{asset.<key>.storagePath}}`。

---

## 2. `flow` 变量从哪来

设计期检查「token 有没有来源」时，也认下面这些：

| 来源 | 说明 |
| ---- | ---- |
| HTTP **extracts** | 从响应抽到 `flow` 或 `asset` |
| **Assign** 节点 | 写入 `flow` |
| **Subflow** 输出 | 子流 `outputs` / `meta.flowOutputs` 回写父流 |
| 场景 **flowSeed** | Run 开始前注入；可用于预置 token 等，不要塞明文口令 |

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

与默认相同的字段**不必**在节点重复写。跑流与调试台同一叠层；结构层 `request_config.body.json.example`（OpenAPI 残留）不发出，层 ① 是 `test_value_config`。

---

## 4. 口令与场景账号放哪

| 场景 | 推荐 |
| ---- | ---- |
| 主测号、可复用 | 素材库 + `{{asset.*}}` |
| 失败用例（停用号、错密码） | 节点 **字面量**；配合「预期业务拒绝」模板 |
| 调试跳过登录 | flowSeed / 素材库预置 token，或探活分支跳过登录 HTTP |

---

## 5. 别和断言 / Extract 混写

| 用在哪 | 写法 |
| ------ | ---- |
| 请求参数、Header、Body | `{{flow.token}}` / `{{asset.*.token}}` / `{{env.*}}` |
| HTTP 节点 **extract**、业务码字段 | 以 **`$` 开头**的 JSONPath，如 `$.data.token` |
| 断言 / 条件左值 | `http.body.data.code`，或简写 `$.data.code`（会自动加 `http.body` 前缀） |

禁止：`http.body.$.…`。细节见节点文档。

---

## 6. 常见报错

| 现象 | 先查 |
| ---- | ---- |
| `TF_PLACEHOLDER_UNDEFINED: {{…token…}}` | 是否已有对应凭证来源（extract / flowSeed / env）；登录口是否误补 Bearer |
| HTTP 200 但业务码失败 | 失败用例是否应关「业务 Code 校验」 |
| 某字段没出现在请求里 | `requestValueOverrides` 形状不对；或字段写在 overrides 根上 |
| 文件变成普通字符串 | form-data 该行 `type=file`，且值为 `{{asset.*.storagePath}}` |
