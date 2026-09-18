package com.qualitest.project.support.cursorskill;

import com.qualitest.ai.mcp.McpPromptResourceService;
import com.qualitest.project.support.testableprompt.TestablePromptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 组装顶栏「MCP 造流 Agent 规程」弹框数据：人话用法、示例提问、各编辑器可复制规程正文。
 * <p>
 * Cursor 页内容为 YAML 头加造流硬规矩；其它编辑器页为各自抬头加造流硬规矩正文。
 */
@Service
@RequiredArgsConstructor
public class McpCursorSkillService {

    /** 单功能 / 指定范围 / 整项目造流提示词加载 */
    private final TestablePromptService testablePromptService;
    /** 造流硬规矩、Cursor Skill 全文、同步本地 Skill 说明 */
    private final McpPromptResourceService mcpPromptResourceService;

    /**
     * 组装弹框完整载荷：怎么用、提示、示例提问、各编辑器规程列表。
     *
     * @return 弹框载荷
     */
    public McpAgentGuidesPayload loadPayload() {
        return McpAgentGuidesPayload.builder()
                .howToUse("不需要特殊唤醒词。在已接好质衡 MCP 的业务仓窗口里，直接用自然语言说目标即可，"
                        + "模型会自己调工具。想改画布或跑流时，说清「用 MCP / 质衡」会更稳。"
                        + "也可复制下方造流提示词，让编辑器产出短提示再贴回 Web 造流。")
                .tips(List.of(
                        "Token 只绑定项目身份；是否出现 submit_* / create_flow / run_test_flow，由项目设置里的「允许 MCP 全自动写流」决定。",
                        "只读勘察随时可用；要经 MCP 直接造流 / 修流 / 跑流，须先开启并保存写流开关。开启后若编辑器仍只列只读，请重连或刷新 MCP。",
                        "没有合适测试流时可用 create_flow 新建空画布；「单功能」= 刚改完一块；「指定范围」= 只扫某模块/包/目录；「整项目」= 整仓按模块补测。",
                        "写工具成功后已落库，不用再找 commit。"
                ))
                .examples(List.of(
                        McpExamplePrompt.builder()
                                .label("列出测试流")
                                .text("用 qualitest MCP 的 list_flows 列出当前项目测试流，返回 testFlowId 和名称。")
                                .build(),
                        McpExamplePrompt.builder()
                                .label("新建空流")
                                .text("用 qualitest MCP 的 create_flow 新建一条空画布测试流，名称自拟，返回 testFlowId。")
                                .build(),
                        McpExamplePrompt.builder()
                                .label("同步/更新本地 Skill")
                                .text(mcpPromptResourceService.loadSyncLocalSkillText().trim())
                                .build(),
                        McpExamplePrompt.builder()
                                .label("单功能")
                                .text(testablePromptService.loadPromptText(TestablePromptService.KIND_INCREMENTAL))
                                .build(),
                        McpExamplePrompt.builder()
                                .label("指定范围")
                                .text(testablePromptService.loadPromptText(TestablePromptService.KIND_SCOPED))
                                .build(),
                        McpExamplePrompt.builder()
                                .label("整项目")
                                .text(testablePromptService.loadPromptText(TestablePromptService.KIND_FULL))
                                .build()
                ))
                .guides(loadGuides())
                .build();
    }

    /**
     * 组装全部编辑器规程项（顺序即弹框 Tab 顺序）。
     *
     * @return 规程列表
     */
    public List<McpAgentGuide> loadGuides() {
        String core = mcpPromptResourceService.loadCoreText();
        return List.of(
                McpAgentGuide.builder()
                        .id("cursor")
                        .title("Cursor")
                        .intro("把规程存成 Cursor Skill 后，Agent 需要时会自动加载。日常对话不用背口令，直接说要查流、改流或跑流即可。")
                        .steps(List.of(
                                "点「复制规程」，得到完整 SKILL.md。",
                                "保存到业务仓 .cursor/skills/qualitest/SKILL.md"
                                        + "（或本机 ~/.cursor/skills/qualitest/SKILL.md）。",
                                "用 Cursor 打开业务仓；在项目设置里配好 MCP Token。"
                                        + "要改流 / 跑流前，开启并保存「允许 MCP 全自动写流」。",
                                "在 Agent / Chat 里用自然语言提问（可参考上方示例）；写完后刷新画布查看结果。"
                        ))
                        .saveHint(".cursor/skills/qualitest/SKILL.md")
                        .content(mcpPromptResourceService.loadCursorSkillText())
                        .build(),
                guide(
                        "claude-code",
                        "Claude Code",
                        "把规程放进项目 CLAUDE.md，并按 Claude Code 文档接好质衡 MCP。之后直接用中文说目标即可。",
                        List.of(
                                "点「复制规程」，得到 CLAUDE.md 正文。",
                                "保存到业务仓根目录 CLAUDE.md。",
                                "在 Claude Code 里配置质衡 MCP（Token）；"
                                        + "要改流 / 跑流前，在质衡开启并保存「允许 MCP 全自动写流」。",
                                "直接用自然语言提问（可参考上方示例）。"
                        ),
                        "CLAUDE.md",
                        """
                        # 质衡 MCP 造流 / 修流（Claude Code）

                        将本文件保存为业务仓根目录 `CLAUDE.md`。须已配置质衡 MCP。

                        """,
                        core),
                guide(
                        "copilot",
                        "VS Code + Copilot",
                        "用仓库自定义指令承载规程，并确认当前环境能调用质衡 MCP。之后直接说要查流、改流或跑流。",
                        List.of(
                                "点「复制规程」，得到自定义指令正文。",
                                "保存到 .github/copilot-instructions.md"
                                        + "（或写入 Copilot Chat 自定义指令）。",
                                "确认能调用质衡 MCP；要改流 / 跑流前开启并保存「允许 MCP 全自动写流」。",
                                "直接用自然语言提问（可参考上方示例）。"
                        ),
                        ".github/copilot-instructions.md",
                        """
                        # 质衡 MCP 造流 / 修流（GitHub Copilot）

                        将本文件保存为 `.github/copilot-instructions.md`。须能调用质衡 MCP。

                        """,
                        core),
                guide(
                        "windsurf",
                        "Windsurf",
                        "把规程放进 Windsurf 项目 Rules，并在设置里接入质衡 MCP。之后直接说目标即可。",
                        List.of(
                                "点「复制规程」，得到 Rules 正文。",
                                "粘贴到 Windsurf 项目 Rules / Memories。",
                                "在 Windsurf 中配置质衡 MCP；要改流 / 跑流前开启并保存「允许 MCP 全自动写流」。",
                                "直接用自然语言提问（可参考上方示例）。"
                        ),
                        "Windsurf 项目 Rules",
                        """
                        # 质衡 MCP 造流 / 修流（Windsurf）

                        将本内容放入 Windsurf 项目 Rules。须已配置质衡 MCP。

                        """,
                        core),
                guide(
                        "continue",
                        "Continue",
                        "用 Continue 的 rules 承载规程，并接入质衡 MCP。之后直接说要查流、改流或跑流。",
                        List.of(
                                "点「复制规程」，得到 rules 正文。",
                                "保存到业务仓 .continue/rules/qualitest.md"
                                        + "（或写入 Continue 的 rules 配置）。",
                                "按 Continue 文档配置质衡 MCP；要改流 / 跑流前开启并保存「允许 MCP 全自动写流」。",
                                "直接用自然语言提问（可参考上方示例）。"
                        ),
                        ".continue/rules/qualitest.md",
                        """
                        # 质衡 MCP 造流 / 修流（Continue）

                        将本文件保存为 `.continue/rules/qualitest.md`。须已配置质衡 MCP。

                        """,
                        core),
                guide(
                        "trae",
                        "Trae",
                        "放入 Trae（或同类 IDE）的项目规则 / 自定义提示，并确认支持 MCP。之后直接说目标即可。",
                        List.of(
                                "点「复制规程」，得到规则正文。",
                                "粘贴到 Trae（或同类 IDE）的项目规则 / 自定义提示入口。",
                                "配置质衡 MCP；要改流 / 跑流前开启并保存「允许 MCP 全自动写流」。",
                                "直接用自然语言提问（可参考上方示例）。"
                        ),
                        "Trae 项目规则 / 自定义提示",
                        """
                        # 质衡 MCP 造流 / 修流（Trae）

                        将本内容放入 Trae 项目规则或自定义提示。须已配置质衡 MCP。

                        """,
                        core),
                guide(
                        "agents-md",
                        "AGENTS.md",
                        "不少 Agent 会读仓库根目录 AGENTS.md，适合不想绑死某一家编辑器时使用。配好 MCP 后直接说目标即可。",
                        List.of(
                                "点「复制规程」，得到 AGENTS.md 正文。",
                                "保存到业务仓根目录 AGENTS.md。",
                                "用任意已接质衡 MCP 的编辑器打开该仓；"
                                        + "要改流 / 跑流前开启并保存「允许 MCP 全自动写流」。",
                                "直接用自然语言提问（可参考上方示例）。"
                        ),
                        "AGENTS.md",
                        """
                        # 质衡 MCP 造流 / 修流（AGENTS.md）

                        将本文件保存为业务仓根目录 `AGENTS.md`。须已配置质衡 MCP。

                        """,
                        core)
        );
    }

    /**
     * 组装单个非 Cursor 编辑器规程项：抬头 Markdown 加造流硬规矩正文。
     *
     * @param id       规程项 id（弹框 Tab）
     * @param title    显示标题
     * @param intro    简介
     * @param steps    安装步骤
     * @param saveHint 建议保存路径提示
     * @param header   文件抬头 Markdown
     * @param core     造流硬规矩正文
     * @return 规程项
     */
    private static McpAgentGuide guide(String id,
                                       String title,
                                       String intro,
                                       List<String> steps,
                                       String saveHint,
                                       String header,
                                       String core) {
        return McpAgentGuide.builder()
                .id(id)
                .title(title)
                .intro(intro)
                .steps(steps)
                .saveHint(saveHint)
                .content((header + core).replace("\r\n", "\n").trim() + "\n")
                .build();
    }
}
