# 质衡 MCP：修失败 / 跑通

本地已有 `.cursor/skills/qualitest/SKILL.md` 且文内 `guideVersion` 与 `get_mcp_guide_version` **字符串全等**时：**不要**再 `prompts/get` 本 Prompt（防叠灌）。版本不一致或未装 Skill 时再用。

<!-- mcp:autowrite -->
## 前置（写流）

- 须已开启「允许 MCP 自动写流」才能 `submit_*`（且 `tools/list` 须已出现这些工具）。
- 改图必须带已有 `testFlowId`。
<!-- /mcp:autowrite -->

<!-- mcp:autorun -->
## 前置（跑流）

- 须已开启「允许 MCP 自动跑流」才能 `run_test_flow`（且 `tools/list` 须已出现该工具）。

## 流程

1. `get_run_failure`（或看上一次 `run_test_flow` 回执）定位失败步骤。
2. 每次只调 **一个** `submit_*` 单元修复；成功即写库。
3. 立刻 `run_test_flow` 验证。
4. 失败后再修最多 **2** 轮（合计最多 3 次 run）；仍失败则停，中文说明原因与下一步。
5. 回执 `paused`（await-input）→ 停，须用户处理，勿盲跑。

## 注意

- 不要输出整份 `graphJson`；拓扑用 `get_graph_summary` / `get_node_detail`。
- HTTP 优先 `callMode=project` + `testProjectApiId`。
<!-- /mcp:autorun -->

未开启写流时：本 Prompt 无写流步骤可用；用只读工具勘察后，请用户开启「允许 MCP 自动写流」、重连 MCP 并再 sync Skill。
未开启自动跑流时：可改图落盘，但勿调用 `run_test_flow`；请用户在 Web 点 Run，或开启「允许 MCP 自动跑流」并重连后再 sync Skill。
