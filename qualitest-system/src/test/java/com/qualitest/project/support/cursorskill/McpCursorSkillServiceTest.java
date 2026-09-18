package com.qualitest.project.support.cursorskill;

import com.qualitest.project.support.testableprompt.TestablePromptService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 MCP 造流规程加载：人话用法、各编辑器独立 Tab、共用造流硬规矩。
 * 边界：读 classpath 资源；不启 Spring。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=McpCursorSkillServiceTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class McpCursorSkillServiceTest {

    private final McpCursorSkillService service =
            new McpCursorSkillService(new TestablePromptService());

    /**
     * 前提：加载弹框完整载荷。
     * 期望：含人话说明；示例为列出测试流 + 新建空流 + 单功能 + 指定范围 + 整项目。
     */
    @Test
    @Order(1)
    @DisplayName("弹框载荷含人话用法与示例")
    void loadPayload_includesHowToAndExamples() {
        McpAgentGuidesPayload payload = service.loadPayload();
        assertTrue(payload.getHowToUse().contains("不需要特殊唤醒词"));
        assertFalse(payload.getTips().isEmpty());
        assertEquals(5, payload.getExamples().size());
        assertEquals("列出测试流", payload.getExamples().get(0).getLabel());
        assertTrue(payload.getExamples().get(0).getText().contains("list_flows"));
        assertEquals("新建空流", payload.getExamples().get(1).getLabel());
        assertTrue(payload.getExamples().get(1).getText().contains("create_flow"));
        assertEquals("单功能", payload.getExamples().get(2).getLabel());
        assertTrue(payload.getExamples().get(2).getText().contains("本次改动")
                || payload.getExamples().get(2).getText().contains("刚改过"));
        assertEquals("指定范围", payload.getExamples().get(3).getLabel());
        assertTrue(payload.getExamples().get(3).getText().contains("<指定范围>"));
        assertEquals("整项目", payload.getExamples().get(4).getLabel());
        assertTrue(payload.getExamples().get(4).getText().contains("按模块"));
        assertEquals(7, payload.getGuides().size());
    }

    /**
     * 前提：加载各编辑器规程。
     * 期望：7 个独立 Tab；Cursor 为完整 SKILL；其它含共用写流规矩。
     */
    @Test
    @Order(2)
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
        assertTrue(cursor.getIntro().contains("自然语言") || cursor.getSteps().stream()
                .anyMatch(s -> s.contains("自然语言")));

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
}
