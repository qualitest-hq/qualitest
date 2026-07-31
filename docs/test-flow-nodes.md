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
- 可选 `useRunSession=true`：Run 级 Cookie Jar 注入与吸收
- 成功判定：先 HTTP 状态码非 2xx 失败；再可选业务码白名单（`successCheck`）
- 通过后写入 `lastResponse`，再执行 `extracts`

**写操作建议**：节点可勾选 **执行前快照**（`snapshotBefore`）。失败可暂停，并由用户选择还原被测数据再重试 / 原地重试 / 跳过 / 中止。被测方需提供 `/test-support`；环境 `allowDestructiveReset=0`（生产默认）时 checkpoint/restore 静默跳过。**开启数据还原时，同一环境请串行跑。**

---

## Assert（`assert`）

对 `data.rules[]` 逐条求值（`CompareRuleEvaluator`），**全部通过才算通过**。  
不负责 HTTP 状态码（由 HTTP 节点在本步处理）。规则字段常见：`left`、`operator`（默认 `eq`）、`right`（可含占位符）。

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
