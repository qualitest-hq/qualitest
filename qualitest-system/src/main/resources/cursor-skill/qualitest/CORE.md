通过已配置的质衡 MCP（服务名一般为 `qualitest`）操作当前 Token 绑定的测试项目。
业务仓代码只作上下文；改图画布与跑流一律走 MCP 工具。编辑器须已接入 MCP（HTTP + `X-Project-Token`）。

本地已有 `.cursor/skills/qualitest/SKILL.md` 且文内 `guideVersion` 与 `get_mcp_guide_version`（或 initialize.serverInfo.guideVersion）**字符串全等**时：日常只用 Skill，**不要**再 `prompts/get` `qualitest_core` / `qualitest_survey` / `qualitest_fix_run`（防叠灌）。版本不一致或用户要求更新时，执行 `qualitest_sync_local_skill`。

`guideVersion` 为合成串：`{规程指纹}[+autopilot][+importApis]`。改项目写流/导入开关并保存后须重连或刷新 MCP，并再跑同步 Skill，否则本地规程与 `tools/list` 可能不一致。

**只调用当前 `tools/list` 里出现的工具**；列表没有的工具当作本项目未开通，勿尝试调用。

## 前置

1. 编辑器已接入质衡 MCP 并配好 Project Token（Token 只绑定项目身份，不区分读写权限）。
<!-- mcp:autopilot -->
2. **写流 / 跑流 / 新建流**前：质衡「项目设置」已开启并保存「允许 MCP 全自动写流」。保存后请重连或刷新 MCP，否则编辑器常仍只列只读工具。
3. **改图必须带已有 `testFlowId`**。没有则先 `list_flows`；仍没有合适流则用 `create_flow` 新建空画布后再继续。
<!-- /mcp:autopilot -->
<!-- mcp:import -->
4. **导入接口**前：另开「允许 MCP 导入接口」。只开导入不能改图；只开写流不能调用 `import_apis`。保存后请重连 MCP 并再 sync Skill。
<!-- /mcp:import -->

## 硬规矩

<!-- mcp:autopilot -->
- **每次成功的 `submit_*` 已立即写库**。不要找 commit / 保存工具，也不要假设还要 Staging 确认。
- 用户若在 Web **打开了同一测试流**，画布会自动跟上；不要提示「请刷新画布」。
- 造流、修流、修失败、用户要「跑通/验证」时：必要 `submit_*`（及缺省时的 upsert）完成后 **立刻** `run_test_flow`，不要只改图就结束。
- 本规程 **不是**「可测提示词」路径：不要只汇总短提示贴回 Web；开了写权限就直接 MCP 改流。
- 若工具报写锁仍被占用（lockHeldBy）：可能是 Web 脏稿/其它标签或 MCP 占用，等待数秒后重试，或换一流再写。
- HTTP 节点优先 `callMode=project` + `testProjectApiId`；测值从业务仓或素材键读取，读不到标不确定。
<!-- /mcp:autopilot -->
<!-- mcp:import -->
- **`import_apis` 成功即写接口库**；不会改写接口上的设计提示字段（设计提示用专门的追加工具写入）。
<!-- /mcp:import -->
- 纯答疑（解释节点/字段、只要建议不改库）时：只用只读工具；不要调用写流 / 导入 / 跑流类工具。
- 不要输出整份 `graphJson`；拓扑用 `get_graph_summary` / `get_node_detail`。
- 用户要看图时：把 `get_graph_summary` 返回的 `mermaid` 原样放进 ` ```mermaid ` 代码块，勿手搓图。
- 不要编造接口 path / 测值；先 `search_apis` → `get_api_details`。
<!-- mcp:import -->
- 库无目标接口时：从业务仓抽取后 `import_apis`，再 `get_api_details`；造流时 HTTP 用 `callMode=project`，避免退化为 external+绝对 URL。
<!-- /mcp:import -->
- 库无目标接口且当前 `tools/list` 无导入类工具时：请用户在 Web / IDEA 插件入库，或开启「允许 MCP 导入接口」并重连后再 sync Skill；不要假装已导入。

## 推荐顺序

### 只读勘察

`list_flows` → 记下 `testFlowId` → `get_graph_summary` / `get_run_failure`（按需 `search_apis`、`get_api_details`）。

<!-- mcp:import -->
## 接口分组 / 注释占位（优先）

先用本占位（对齐 IDEA 插件默认）；skill 缺失时再试读本机 `%APPDATA%\JetBrains\<产品版本>\options\qualitest-settings.xml` 的 `groupTag` / `ignoreFirstGroupLevel`（**只取这两项，勿泄露 projectToken**）。

```text
分组标签 groupTag = api.group {group}   → 源码写 @api.group …
忽略分组第一级 ignoreFirstGroupLevel = false
  （为 true：按首个「.」去掉左侧前缀，如 模块.管理端.登录 → 管理端.登录）
注释：方法 JavaDoc 正文（跳过首行摘要，到 @ 标签前）
名称：@Operation.summary → JavaDoc 首行 → 方法名
```

从**业务仓** Controller 抽 method/path/参数及上述分组注释；有标注则传入 `import_apis` 覆盖；省略则更新保留库内、新增落入默认分组。不要用包名瞎编分组覆盖已有库内值。看回执 `warnings` / `metaGroupSource`。

### 缺接口时充实接口库

`search_apis` 无目标 → 按占位从业务仓抽 → `import_apis` → `get_api_details`。
<!-- /mcp:import -->

<!-- mcp:autopilot -->
### 造流 / 扩流

1. 确认 `testFlowId`；没有合适流时先 `create_flow`，再读相关接口（接口库缺目标时按上文「缺接口」路径处理）。
2. 缺素材 / 多端配置（鉴权头或响应约定）时先 `upsert_asset_variables` / `upsert_auth_profile`（调用即写库；改约定用 `patch.responseConvention`）。
3. 每次只调 **一个** `submit_*` 单元（一个节点 / 一条边 / 一个场景 / 一次删除）；HTTP 优先 `callMode=project` + `testProjectApiId`。
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
| `upsert_asset_variables` / `upsert_auth_profile` | 工具内直写库；不要求 `testFlowId`；约定改 `patch.responseConvention` |
| `append_api_design_hints` | 追加接口短提示并落库 |
| `run_test_flow` | 跑库中最新图；须 `testFlowId`；可选场景/环境 id |

<!-- mcp:import -->
另有 `import_apis`：结构化 items 幂等 upsert；成功即落库；返回 created/updated/skipped/conflicts 与 testProjectApiId。
<!-- /mcp:import -->

调用写工具时把 `testFlowId`（及工具要求的节点/边字段）放进 MCP arguments；项目由 Token 绑定，一般不必改 `testProjectId`。
<!-- /mcp:autopilot -->

## 对用户说明

- 简短中文汇报：改了什么、跑通与否、失败点。
- Token 等同项目凭证：勿写入业务仓明文、勿要求用户把 Token 贴进对话持久文件。
