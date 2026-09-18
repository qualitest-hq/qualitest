# 质衡 MCP：修失败 / 跑通

本地已有 `.cursor/skills/qualitest/SKILL.md` 且文内 `guideVersion` 与 `get_mcp_guide_version` 一致时：**不要**再 `prompts/get` 本 Prompt（防叠灌）。版本不一致或未装 Skill 时再用。

## 前置

- 须已开启「允许 MCP 全自动写流」才能 `submit_*` / `run_test_flow`。
- 改图必须带已有 `testFlowId`。

## 流程

1. `get_run_failure`（或看上一次 `run_test_flow` 回执）定位失败步骤。
2. 每次只调 **一个** `submit_*` 单元修复；成功即写库。
3. 立刻 `run_test_flow` 验证。
4. 失败后再修最多 **2** 轮（合计最多 3 次 run）；仍失败则停，中文说明原因与下一步。
5. 回执 `paused`（await-input）→ 停，须用户处理，勿盲跑。

## 注意

- 不要输出整份 `graphJson`；拓扑用 `get_graph_summary` / `get_node_detail`。
- HTTP 优先 `callMode=project` + `testProjectApiId`。
