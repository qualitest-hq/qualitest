package com.qualitest.project.support.cursorskill;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.qualitest.common.exception.ServiceException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 加载质衡 MCP 造流用的多份 Agent 规程正文。
 * <p>
 * 每种编辑器一份独立 Tab；造流硬规矩共用 CORE.md，避免多份正文漂移。
 */
@Service
public class McpCursorSkillService {

    private static final String CORE_PATH = "cursor-skill/qualitest/CORE.md";
    private static final String CURSOR_SKILL_PATH = "cursor-skill/qualitest/SKILL.md";

    /**
     * 返回全部规程（顺序即 Tab 顺序），每种编辑器单独一项。
     */
    public List<McpAgentGuide> loadGuides() {
        String core = loadClasspathUtf8(CORE_PATH, "造流规程正文");
        return List.of(
                McpAgentGuide.builder()
                        .id("cursor")
                        .title("Cursor")
                        .intro("Cursor 用 Skill 目录承载规程，Agent 会按需加载。")
                        .steps(List.of(
                                "点「复制规程」，得到完整 SKILL.md。",
                                "保存到业务仓 .cursor/skills/qualitest/SKILL.md"
                                        + "（或本机 ~/.cursor/skills/qualitest/SKILL.md）。",
                                "用 Cursor 打开业务仓；项目设置配好 MCP Token，"
                                        + "写流前开启「允许 MCP 全自动写流」。"
                        ))
                        .saveHint(".cursor/skills/qualitest/SKILL.md")
                        .content(loadClasspathUtf8(CURSOR_SKILL_PATH, "Cursor Skill"))
                        .build(),
                guide(
                        "claude-code",
                        "Claude Code",
                        "Anthropic Claude Code：把规程写入项目 CLAUDE.md，并按 Claude Code 文档配置 MCP。",
                        List.of(
                                "点「复制规程」，得到 CLAUDE.md 正文。",
                                "保存到业务仓根目录 CLAUDE.md。",
                                "在 Claude Code 中配置质衡 MCP（Token）；"
                                        + "写流前在质衡开启「允许 MCP 全自动写流」。"
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
                        "GitHub Copilot Chat：用仓库自定义指令承载规程，并接入支持 MCP 的工具链后再写流。",
                        List.of(
                                "点「复制规程」，得到自定义指令正文。",
                                "保存到 .github/copilot-instructions.md"
                                        + "（或写入 Copilot Chat 自定义指令）。",
                                "确认当前环境能调用质衡 MCP；写流前开启「允许 MCP 全自动写流」。"
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
                        "Windsurf（Cascade）：把规程放进项目 Rules，并在 Windsurf 设置中接入质衡 MCP。",
                        List.of(
                                "点「复制规程」，得到 Rules 正文。",
                                "粘贴到 Windsurf 项目 Rules / Memories。",
                                "在 Windsurf 中配置质衡 MCP；写流前开启「允许 MCP 全自动写流」。"
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
                        "Continue（VS Code / JetBrains 插件）：用 rules 目录或 Continue 配置承载规程，并接入 MCP。",
                        List.of(
                                "点「复制规程」，得到 rules 正文。",
                                "保存到业务仓 .continue/rules/qualitest.md"
                                        + "（或写入 Continue 的 rules 配置）。",
                                "按 Continue 文档配置质衡 MCP；写流前开启「允许 MCP 全自动写流」。"
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
                        "Trae 等带 Agent 的 IDE：放入各自的项目规则 / 自定义提示，并确认支持 MCP。",
                        List.of(
                                "点「复制规程」，得到规则正文。",
                                "粘贴到 Trae（或同类 IDE）的项目规则 / 自定义提示入口。",
                                "配置质衡 MCP；写流前开启「允许 MCP 全自动写流」。"
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
                        "通用：不少 Agent 会读仓库根目录 AGENTS.md，适合不想绑死某一家编辑器时使用。",
                        List.of(
                                "点「复制规程」，得到 AGENTS.md 正文。",
                                "保存到业务仓根目录 AGENTS.md。",
                                "用任意已接质衡 MCP 的编辑器打开该仓；写流前开启「允许 MCP 全自动写流」。"
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
     * 仅 Cursor Skill 全文（兼容旧调用）。
     */
    public String loadSkillMarkdown() {
        return loadGuides().stream()
                .filter(g -> "cursor".equals(g.getId()))
                .map(McpAgentGuide::getContent)
                .findFirst()
                .orElseThrow(() -> new ServiceException("Cursor Skill 为空"));
    }

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

    private static String loadClasspathUtf8(String path, String label) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            String text = IoUtil.read(in, StandardCharsets.UTF_8);
            if (StrUtil.isBlank(text)) {
                throw new ServiceException(label + "为空");
            }
            return text.replace("\r\n", "\n").trim() + "\n";
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("无法读取" + label + ": " + e.getMessage());
        }
    }
}
