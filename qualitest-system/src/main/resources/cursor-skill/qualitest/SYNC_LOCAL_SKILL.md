用质衡 MCP 执行 qualitest_sync_local_skill（安装或更新本地 Skill）：

1. 先 `get_mcp_guide_version`（或看 initialize.serverInfo.guideVersion），记下服务端 `guideVersion`（合成串：`{指纹}[+autopilot][+importApis]`，与当前项目写流/导入开关一致）。
2. 若业务仓已有 `.cursor/skills/qualitest/SKILL.md` 且文内 `guideVersion` 与服务端**字符串全等**：停止；勿再 get 正文（防叠灌）。
3. 否则 `prompts/get`：`qualitest_core`（一次即可；返回正文已按当前开关裁剪；勘察/修失败要点已含在 CORE 内，不必再拉 survey/fix_run 叠灌）。
4. 写入 `.cursor/skills/qualitest/SKILL.md`：YAML frontmatter（`name: qualitest`，`description` 覆盖当前已开通能力，并写入 `guideVersion: <服务端合成串>`）+ 空行 + `qualitest_core` 正文全文。勿手改规程正文另存第二份。
5. 汇报已安装或已更新到该版本。之后日常造流只靠本地 Skill；仅当 version 不一致（含改开关导致后缀变化）或用户明确要求更新时再跑本流程。改开关后须先重连 MCP 再 sync。
