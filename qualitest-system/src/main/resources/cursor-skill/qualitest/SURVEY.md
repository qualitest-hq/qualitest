# 质衡 MCP：只读勘察

本地已有 `.cursor/skills/qualitest/SKILL.md` 且文内 `guideVersion` 与 `get_mcp_guide_version` 一致时：**不要**再 `prompts/get` 本 Prompt（防叠灌）。版本不一致或未装 Skill 时再用。

## 推荐顺序

1. `list_flows`（可按名称 keyword）→ 记下目标 `testFlowId`。
2. `get_graph_summary`（或 `get_subflow_detail`）了解节点、主路径、断言位置；不要贴整份 `graphJson`。
3. 上次跑挂：`get_run_failure`；接口绑定问题：`get_flow_api_health`。
4. 查接口：`search_apis` → `get_api_details`；环境 / 素材键 / 鉴权：`list_project_envs` / `list_asset_variables` / `list_project_auth_profiles`。

## 硬约束

- 纯答疑：只用只读工具；不要 `submit_*` / `create_flow` / `import_apis` / `run_test_flow`。
- 用户要看图：把 `get_graph_summary` 的 `mermaid` 原样放进 ` ```mermaid ` 代码块。
