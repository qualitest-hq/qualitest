# MCP 全自动：直写测试流 + 跑

> **状态**：产品思路草案（未实现）  
> **选定模式**：与 Web AI「全自动」同语义——**隐式落盘 + `run_test_flow`**，不做 Staging / 不做 MCP「只提案」变体  
> **相关**：[ai-staging.md](./ai-staging.md)（Web 全自动）、[mcp.md](./mcp.md)（现状只读）；轻量入口见项目设置「可测提示词」

---

## 1. 要解决什么

今天 Cursor 经 MCP **只能读**接口 / 流 / 失败现场；真要造流、改图、跑通，必须回到质衡 Web AI。

选定「全自动直写 + 跑」之后，目标闭环变为：

```text
业务仓（Cursor）读代码 / diff
    → MCP 查接口库、已有流、失败 Run
    → MCP submit_* / upsert_* 直写
    → MCP run_test_flow 开跑
    → 失败则同会话再修（有限轮次）
    → 人在 Web 画布上看结果（可选）
```

「可测提示词」仍可作轻量入口（人只复制提示、自己粘贴）；本方案是**重入口**：编辑器 Agent 直接改质衡资产，不必再绕 Web 对话。

---

## 2. 与 Web 全自动对齐（不另起语义）

Web 面板「全自动」已约定（见 [ai-staging.md](./ai-staging.md) §1）：

| 点 | 口径 |
|----|------|
| 落盘 | `submit_*` 后由 `run_test_flow`（或回合结束）**隐式落盘**，无独立 commit 工具 |
| 素材 / 鉴权 | `upsert_asset_variables` / `upsert_auth_profile` **工具内直写** |
| 门禁 | 落盘门槛 = 人手「保存」；运行时再硬拦 AUTH / 断言路径等 |
| 修轮 | 同会话：upsert → submit_* → run → 失败再修，**最多再修 2 轮** |
| 纯答疑 | 不强制 `run_test_flow` |

**MCP 全自动必须复用同一套工具实现与门禁**，避免两套造流逻辑。差异只在通道：

| | Web 全自动 | MCP 全自动（本稿） |
|--|------------|-------------------|
| 谁驱动多轮 | 质衡后端 Agent（`TestFlowDesignAgent` + SSE） | **Cursor 等客户端 Agent**（多轮 `tools/call`） |
| Staging UI | 无（隐式落盘后清） | 无 |
| 开关 | 面板「全自动」 | **项目显式开启 MCP 写权限**（见 §4） |
| 结果可见 | SSE `graphCommitted` / `runStarted` + 画布 | 工具返回 JSON；可用 `get_run_failure` 续查 |

也就是说：MCP 不新造「改图方言」，只是把 Web 全自动已允许的写工具，在**显式授权**后挂到 MCP 白名单上，由外部 Agent 按步调用。

---

## 3. 目标形态（用户感知）

1. 项目设置打开 **「允许 MCP 全自动写流」**（默认关）。
2. 复制 / 刷新带写权限的 Project Token（或同 Token + 服务端开关校验，见 §4）。
3. Cursor 已配 MCP；用户说：「给刚改的下单接口造一条冒烟流并跑通」。
4. Agent：`search_apis` →（可选 `upsert_*`）→ 多次 `submit_*` → `run_test_flow` → 失败则 `get_run_failure` 再修。
5. 用户打开质衡画布，流已落盘且有最新 Run；无需在 Web 里再点全自动造一遍。

---

## 4. 安全与开关（必须先有）

全自动直写权限重，**不能**仅凭「配了 MCP」就默认可写。

### 4.1 默认

- 现状保持：**MCP 只读**（拒绝一切 `submit_*` / 写类 upsert / `run_test_flow`）。
- 新能力：**项目级开关**，默认 `false`。

### 4.2 建议开关形态（实现时可二选一，文档先定产品口径）

| 方案 | 做法 | 取舍 |
|------|------|------|
| **A. 项目设置布尔** | `mcpAutopilotEnabled`；Token 仍用现有 Project Token，服务端每次 tools/call 校验开关 | 实现简单；Token 泄露 = 可写（与今日 Token 能读敏感摘要同级风险放大） |
| **B. 写权限 Token 标记** | 签发 Token 时可选「只读 / 全自动写」；写 Token 可单独刷新作废 | 更贴最小权限；UI 稍重 |

**推荐先做 A + 醒目文案**（「开启后，持有本项目 Token 的 Cursor 可直接改测试流并触发运行」）；后续有需要再拆 B。

### 4.3 硬规则

1. 开关关闭时：行为与今日完全一致（只读 + 明确错误文案）。
2. 开关开启时：才把写工具加入 MCP 工具列表（或列表仍可见但调用时拒绝——**推荐列表也按开关裁剪**，避免模型误调）。
3. Token 与项目绑定校验不变；禁止跨项目写。
4. 不在 MCP 暴露「关掉开关 / 刷新他人 Token」类管理工具。
5. 审计：写类 tools/call 记操作日志（谁 Token、哪条流、工具名、是否 run），便于追责。

---

## 5. 工具面（相对今日 MCP）

今日只读工具保留。全自动开启后**追加**与 Web 全自动同名的写工具（名称与参数与 `FlowDesignToolNames` / 执行器一致，禁止 MCP 专用改图协议）：

| 工具 | MCP 全自动 |
|------|------------|
| 现有只读（`list_flows` / `search_apis` / `get_run_failure` …） | 始终可用 |
| `submit_*` 单元写工具 | **开放**（每次一单元；服务端按全自动路径隐式可落盘） |
| `upsert_asset_variables` | **开放**（直写） |
| `upsert_auth_profile` | **开放**（直写） |
| `run_test_flow` | **开放**（跑前自动落盘；语义同 Web） |
| `append_api_design_hints` | 可选一并开放（与 Web 一致则开） |

**仍不提供**：半自动 Staging 确认/取消、独立 commit、Web SSE 推送。Cursor 侧用工具返回值判断成败即可。

### 5.1 落盘时机（与 Web 对齐的实现注意）

Web 全自动里，落盘挂在 Agent 回合与 `run_test_flow` 上。MCP 是**单次 tools/call、无质衡多轮 Agent**时：

- **推荐**：每次成功的写工具（或每次 `run_test_flow`）走与 Web 全自动相同的「隐式落盘」入口；避免 Cursor 调了十几次 `submit_*` 却只在内存、进程一断全丢。
- **禁止**：要求 Cursor 再调一个 MCP 专用 `commit_graph`（与 Web「无独立 commit」口径冲突，且易漏调）。

具体是「每个 `submit_*` 立即持久化」还是「`run_test_flow` 前合并持久化」，实现阶段与现有 `FlowDesignToolExecutor` / 全自动 capture 对齐后定一种，并在工具描述里写死，免得模型假设错误。

---

## 6. Cursor 侧推荐工作流

与 Web 全自动同一剧本，只是驱动方换成 Cursor：

```text
1. list_flows / search_apis / get_graph_summary   # 摸底
2. upsert_asset_variables / upsert_auth_profile     # 需要时
3. submit_* × N                                   # 改图
4. run_test_flow                                  # 落盘并跑
5. 失败 → get_run_failure → 回到 3，最多再 2 轮
6. 纯答疑则不要 run
```

提示策略（可写进 MCP 工具 description / 项目「可测提示词」附录）：

- 短意图 + 业务测值；少贴整份 graphJson。
- 优先挂已有登录子流；不要在提示里重写登录，除非测登录本身。
- 一次只推进一条流；勿并行改多条流（并发写同一 `graph_json` 易互相覆盖）。

---

## 7. 与「可测提示词」的关系

| 能力 | 角色 |
|------|------|
| 可测提示词（已落地） | 人从质衡复制规矩 → Cursor 产出自然语言 → **人**贴回 Web AI |
| MCP 全自动（本稿） | Cursor **直接**调写工具；提示词变成 Agent 系统习惯，不必再回 Web 粘贴 |

两者并存：未开写权限时用提示词桥接；开启后 Agent 可端到端，提示词仍可用于约束「怎么写短提示 / 测什么」，不必废弃。

---

## 8. 风险与边界

| 风险 | 应对 |
|------|------|
| Token 泄露导致任意改流 / 跑环境 | 默认关；文案警告；可刷新 Token；后续 Token 分级 |
| 与 Web 画布同时编辑冲突 | 工具返回当前版本 / hash；冲突时拒绝覆盖并提示刷新（实现期对齐现有保存冲突策略） |
| 模型一次改太大导致半截图 | 工具描述强调短步；步数/单次 submit 体量沿用 Web 限制 |
| 跑挂到错误环境 | `run_test_flow` 必须显式环境或沿用流默认场景；禁止静默切生产 |
| 用户以为 MCP 仍只读 | 开关旁说明；工具列表随开关变化 |
| 绕过 Staging 后悔 | 产品口径即「全自动」；可用 Run 历史与 git/导出流做回滚，不在 MCP 做撤销栈 |

**非目标（本稿不做）**

- MCP 半自动 / Staging 提案模式  
- Cursor 内嵌画布可视化  
- 用 MCP 替代 IDEA 插件做接口上传  
- 跨项目、平台级一键全自动

---

## 9. 实现分期（建议）

### 一期：开关 + 写工具白名单 + 跑通一条冒烟

1. 项目设置：`允许 MCP 全自动写流`（默认关）+ 风险说明。  
2. `McpToolInvokeService`：开关开时允许 `submit_*` / 写 upsert / `run_test_flow`；关时保持今日拒绝文案。  
3. 工具列表（tools/list）随开关裁剪。  
4. 落盘与 `run_test_flow` 复用 Web 全自动执行器路径。  
5. 更新 [mcp.md](./mcp.md)：只读为默认；专节描述全自动。  
6. 验收：Cursor 对 demo 项目造「登录后探活」类短流并跑通；关开关后写工具不可用。

### 二期：冲突、审计、体验

1. 写操作审计日志。  
2. 与 Web 同时编辑的冲突提示。  
3. 项目设置展示「最近 MCP 写操作」摘要（可选）。  
4. Token 分级（§4 方案 B）若一期反馈需要再做。

---

## 10. 验收想象

1. 开关关闭：Cursor 调 `submit_http_node` 得到明确拒绝，与现网一致。  
2. 开关打开：Cursor 仅用 MCP 完成「查 API → 造短流 → run → 失败再修 ≤2 轮」，Web 不打开 AI 面板也能在画布看到节点与 Run。  
3. 同一套门禁：缺凭证等运行硬拦时，`run_test_flow` 失败原因与 Web 全自动可读性同级。  
4. 关开关或刷新 Token 后，写立即失效。

---

## 11. 一句话

**MCP 全自动 = 把 Web「全自动」的直写 + 跑能力，在项目显式授权后交给 Cursor 多轮调用；不另造改图协议，默认仍只读。**
