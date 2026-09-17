通过已配置的质衡 MCP（服务名一般为 `qualitest`）操作当前 Token 绑定的测试项目。
业务仓代码只作上下文；改图画布与跑流一律走 MCP 工具。编辑器须已接入 MCP（HTTP + `X-Project-Token`）。

## 前置

1. 编辑器已接入质衡 MCP 并配好 Project Token（Token 只绑定项目身份，不区分读写权限）。
2. **写流 / 跑流 / 新建流**前：质衡「项目设置」已开启并保存「允许 MCP 全自动写流」。未开则只能只读勘察。开启后若编辑器仍只列只读工具，请重连或刷新 MCP。
3. **改图必须带已有 `testFlowId`**。没有则先 `list_flows`；仍没有合适流则用 `create_flow` 新建空画布后再继续。

## 硬规矩

- **每次成功的 `submit_*` 已立即写库**。不要找 commit / 保存工具，也不要假设还要 Staging 确认。
- 造流、修流、修失败、用户要「跑通/验证」时：必要 `submit_*`（及缺省时的 upsert）完成后 **立刻** `run_test_flow`，不要只改图就结束。
- 纯答疑（解释节点/字段、只要建议不改库）时：只用只读工具；不要 `submit_*` / `create_flow` / `run_test_flow`。
- 不要输出整份 `graphJson`；拓扑用 `get_graph_summary` / `get_node_detail`。
- 不要编造接口 path / 测值；先 `search_apis` → `get_api_details`，测值从业务仓或素材键读取，读不到标不确定。
- 本规程 **不是**「可测提示词」路径：不要只汇总短提示贴回 Web；开了写权限就直接 MCP 改流。

## 推荐顺序

### 只读勘察

`list_flows` → 记下 `testFlowId` → `get_graph_summary` / `get_run_failure`（按需 `search_apis`、`get_api_details`）。

### 造流 / 扩流

1. 确认 `testFlowId`；没有合适流时先 `create_flow`，再读相关接口。
2. 缺素材 / 鉴权时先 `upsert_asset_variables` / `upsert_auth_profile`（调用即写库）。
3. 每次只调 **一个** `submit_*` 单元（一个节点 / 一条边 / 一个场景 / 一次删除）。
4. 空画布：先连续 `submit_*_node`（`op=add`），再 `submit_edge`。
5. 至少成功一次改图后 → `run_test_flow`。

### 修失败

`get_run_failure`（或看 `run_test_flow` 回执）→ `submit_*` 修复 → 再 `run_test_flow`。  
失败后再修最多 **2** 轮（合计最多 3 次 run）；仍失败则停，用中文说明原因与下一步。  
`paused`（await-input）→ 停，说明须用户处理，勿盲跑。

## 写工具速记

| 工具 | 要点 |
|------|------|
| `create_flow` | 新建空画布测试流并写库；返回 `testFlowId`；不要求已有流 id |
| `submit_http_node` 等 `submit_*` | 每次 1 单元；成功即落盘；须 `testFlowId` |
| `upsert_asset_variables` / `upsert_auth_profile` | 工具内直写库；不要求 `testFlowId` |
| `append_api_design_hints` | 追加接口短提示并落库 |
| `run_test_flow` | 跑库中最新图；须 `testFlowId`；可选场景/环境 id |

调用写工具时把 `testFlowId`（及工具要求的节点/边字段）放进 MCP arguments；项目由 Token 绑定，一般不必改 `testProjectId`。

## 对用户说明

- 简短中文汇报：改了什么、跑通与否、失败点。
- Token 等同项目凭证：勿写入业务仓明文、勿要求用户把 Token 贴进对话持久文件。
