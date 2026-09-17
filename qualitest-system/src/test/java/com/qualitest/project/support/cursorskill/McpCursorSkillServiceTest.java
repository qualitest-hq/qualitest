package com.qualitest.project.support.cursorskill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 MCP 造流规程加载：各编辑器独立 Tab，共用造流硬规矩。
 */
class McpCursorSkillServiceTest {

    private final McpCursorSkillService service = new McpCursorSkillService();

    @Test
    @DisplayName("规程按编辑器拆分为独立项")
    void loadGuides_splitByEditor() {
        List<McpAgentGuide> guides = service.loadGuides();
        Set<String> ids = guides.stream().map(McpAgentGuide::getId).collect(Collectors.toSet());
        assertTrue(ids.containsAll(Set.of(
                "cursor", "claude-code", "copilot", "windsurf", "continue", "trae", "agents-md")));
        assertEquals(7, guides.size());

        McpAgentGuide cursor = guides.get(0);
        assertEquals("cursor", cursor.getId());
        assertTrue(cursor.getContent().contains("name: qualitest"));
        assertTrue(cursor.getContent().contains("submit_*"));

        McpAgentGuide claude = guides.stream()
                .filter(g -> "claude-code".equals(g.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals("CLAUDE.md", claude.getSaveHint());
        assertTrue(claude.getContent().contains("Claude Code"));
        assertTrue(claude.getContent().contains("run_test_flow"));
        assertTrue(claude.getContent().contains("立即写库")
                || claude.getContent().contains("已立即写库"));
    }

    @Test
    @DisplayName("兼容接口仍返回 Cursor Skill")
    void loadSkillMarkdown_returnsCursor() {
        String text = service.loadSkillMarkdown();
        assertTrue(text.contains("name: qualitest"));
        assertTrue(text.contains("run_test_flow"));
    }
}
