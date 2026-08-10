# 测试流节点说明

画布固定 **7** 种节点类型（`FlowNodeType`），不可自定义 `type`。持久化值为小写 code；UI 展示用英文 label。

英文版：[test-flow-nodes.en.md](./test-flow-nodes.en.md)

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

执行时还有审计步（非画布拖拽节点）：`run_config`、`snapshot` / `restore` 等，见 Run 详情。

---

## HTTP（`http`）

按 `data.callMode` 分两条链路，共用转发与 `extracts` 抽变量：

| callMode | 说明 |
|----------|------|
| `project` | 绑定 `testProjectApiId`，合并项目接口资产；pre/post 脚本来自接口定义 |
| `external` | 使用节点 `externalUrl` / `httpMethod` / `headers` / `requestBody`；校验外联权限；步骤报告会脱敏敏感字段 |

常用能力：

- 占位符解析请求参数 / 体（`flow` / `env` / `session` 等）
- **项目鉴权补头**（见下方「项目鉴权」）
- 成功判定：先 HTTP 状态码非 2xx 失败；再可选业务码白名单（`successCheck`）
- 通过后写入 `lastResponse`，再执行 `extracts`

### 项目鉴权

配置挂**测试项目**（设置抽屉「项目鉴权」），不挂环境。造流 Normalizer、Run、调试台共用同一解析器。

| 层级 | 要点 |
|------|------|
| 项目 `authProfiles` | 头模板只在此维护；`defaultProfileId` 兜底；可选 `anonymousPathExact` / `anonymousPathPrefix`（导入命中 → 接口 `mode=none`） |
| 接口 `auth.mode` | `inherit` 按项目 Profile；`none` 不加头；`override` 用本接口 `header.name` + `valueTemplate` |
| 节点 headers | 托管头带 `profileManaged`，Run 时按**当前**项目/接口配置刷新；无该标记的显式头永不被静默改掉 |

**Profile 匹配**：接口已指定 `authProfileId` 则用之；否则在 `authProfiles` 中取命中的最长 `pathPrefix`；无人命中用 `defaultProfileId`。**禁止** `pathPrefix="/"`。

**登录抽凭证**：extracts 的 `from`+`expr`（`body`+JSONPath 或 `setCookie`+Cookie 名）与 Profile `loginHint` 对齐，写入 `flow.token` / `flow.adminToken` 等。**不要**再写节点 `useRunSession`（已忽略）。

**门禁与操作**：缺对应端 `flow.token` / `flow.adminToken` 来源时，AI submit / Staging 确认 / 保存硬拦（`AUTH_TOKEN_MISSING`）；托管头补全为 soft warning（`AUTH_HEADER_MANAGED`）。画布顶栏「刷新鉴权头」批量提案写回托管头（经 Staging）。Run 鉴权失败用「AI 修复」（含 401 预填）。

**种子**：项目级上传且 `auth_config` 为空 → 通用单套 Bearer；demo 双端可在设置里「套用双端（demo）」（`/api/` → `flow.token`，`/system/` 等 → `flow.adminToken`）。

**写操作建议**：节点可勾选 **执行前快照**（`snapshotBefore`）。失败可暂停，并由用户选择还原被测数据再重试 / 原地重试 / 跳过 / 中止。被测方需提供 `/test-support`；环境 `allowDestructiveReset=0`（生产默认）时 checkpoint/restore 静默跳过。**开启数据还原时，同一环境请串行跑。**

---

## Assert（`assert`）

对 `data.rules[]` 逐条求值（`CompareRuleEvaluator`），**全部通过才算通过**。  
不负责 HTTP 状态码（由 HTTP 节点在本步处理）。规则字段常见：`left`、`operator`（默认 `eq`）、`right`（可含占位符）。

### 路径方言

| 场景 | 写法 | 说明 |
|------|------|------|
| 断言 / 条件左值 | `http.body.data.code`、`http.body.data[?(@.cartId=='5001')].quantity` | 相对上一步响应 body；支持下标、过滤器、`[*]`、`length()`。**数组字段不要写 JSON Schema 关键字 `.items`**（真实 JSON 无此层） |
| 简写（会规范化） | `$.data.code` | 设计态 / 运行态规范为 `http.body.data.code` |
| Extract / 业务码 | `$.data.token` | **必须以 `$` 开头**，相对 body 根 |
| 其它上下文 | `flow.*` / `env.*` / `asset.*` / `http.status` / `http.duration` | **不走 JsonPath** |

禁止：`http.body.$.…`（Confirm / 保存会失败）。

### 运算符

UI 提供：`eq/ne/gt/gte/lt/lte/contains/not_contains/exists`。  
别名：`equals`/`==` → `eq`；`notempty`/`not_empty` → `exists`（勿再写 `notempty`）。

集合语义：

- `exists`：非 null、非空串；列表/数组非空才通过  
- `eq`：多元素列表仅当恰好 1 个元素时拆箱再比，否则失败  
- `contains`：左值为列表时**任一**元素包含右值即可  

购物车「是否含某 cartId」推荐：`http.body.data[?(@.cartId=='5001')]` + `exists`（若 `data` 直接是数组；勿写成 `data.items[…]`）。

设计期：Staging ✓ 确认 **assert/condition 节点**时，会在含尚未确认 Staging 上下游的**预览图**上按上游接口**响应 schema** 校验 `http.body…` 左值（禁 `.items`；过滤器与 `data[*].字段` 对齐）；预览图仍无上游 project HTTP 则硬拦。错误挂在该断言/条件单元，**不会**因路径问题拦住边确认。AI submit / **保存** 仍对全图跑同一门禁。响应 **example 只给人看**，属性面板可对照 example 软试算（空结果标红），**不参与硬拦**。无 schema 时跳过不报错。正式 **Run** 只跑图结构校验。

---

## Condition（`condition`）

扫描 `data.branches[]`（顺序 IF → ELIF → ELSE）：

- **if / elif**：`conditions[]` 全部成立则命中（AND）
- **else**：前序均未命中时兜底

命中分支须有非空 `target`（下一节点 id）；结果写入步骤 `branchTaken`（`branchId` / `kind`）。

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

## Script（`script`）

| 字段 | 说明 |
|------|------|
| `language` | `javascript`（默认）或 `python` |
| `source` | 脚本正文 |
| `timeoutMs` | 超时（有上下限归一化） |

在 GraalVM 沙箱中执行：可读写 `flow`、`session`；可读 `env` / `asset`；可选受控 `ctx.http`。`ctx.setFlow` 合并进当前 Run。失败时步骤带脚本错误码与消息。

适合项目特有签名、字段拼装等「画布节点不够用」的逻辑；能用 HTTP / Assign / Assert 表达的优先用声明式节点。

---

## Subflow（`subflow`）

将同项目另一张测试流折叠为单步：

1. 按 `subflowId` 加载（须同 `testProjectId`）
2. `inputs` 解析占位符写入子上下文 `flow`
3. 内存跑子图（fail-fast）；嵌套深度有限（主→子→孙，最多 **2** 层 subflow）
4. 按 `outputs` 或子图 `meta.flowOutputs` 写回父 `flow`
5. 步骤 `subflow.childSteps` 附内层摘要，供 Run 详情与 MCP `get_run_failure`

| versionPolicy | 行为 |
|---------------|------|
| `latest` | 每次 Run 取子流当前图 |
| `pinned` | 优先用节点 `pinnedGraphJson` 固化快照 |

平台提供登录 / OAuth / 验证码等 **子流模板**，可 fork 后改参数。

---

## 设计建议

1. **主路径**：HTTP（project）→ Assert；变量用 extracts / Assign。
2. **分支**：Condition 后各臂汇合前注意合并语义；复杂逻辑可拆子流。
3. **复用**：登录、鉴权等多步 → Subflow，父流只传输入输出。
4. **兜底**：签名、动态拼装 → Script；保持脚本短小、可测。
5. **破坏性写**：开 `snapshotBefore` + 环境允许还原；并行勿抢同一环境。

更多：部署见 [deploy.md](./deploy.md)；MCP 勘察见 [mcp.md](./mcp.md)；靶场造流提示见 [qualitest-demo AI 提示集](https://github.com/qualitest-hq/qualitest-demo/blob/main/docs/ai-test-flow-prompts.md)。
