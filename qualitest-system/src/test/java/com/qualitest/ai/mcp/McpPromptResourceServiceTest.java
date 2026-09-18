package com.qualitest.ai.mcp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 MCP Prompt/Resource 组装与 guideVersion。
 * 边界：读 classpath；不启 Spring。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=McpPromptResourceServiceTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class McpPromptResourceServiceTest {

    private final McpPromptResourceService service = new McpPromptResourceService();

    /**
     * 前提：加载 prompts/list。
     * 期望：四条固定 Prompt 名；description 非空。
     */
    @Test
    @Order(1)
    @DisplayName("listPrompts 返回四条")
    void listPrompts_returnsFour() {
        List<Map<String, Object>> prompts = service.listPrompts();
        assertEquals(4, prompts.size());
        Set<String> names = prompts.stream()
                .map(p -> String.valueOf(p.get("name")))
                .collect(Collectors.toSet());
        assertTrue(names.containsAll(Set.of(
                McpPromptResourceService.PROMPT_CORE,
                McpPromptResourceService.PROMPT_SURVEY,
                McpPromptResourceService.PROMPT_FIX_RUN,
                McpPromptResourceService.PROMPT_SYNC_LOCAL_SKILL)));
        assertFalse(String.valueOf(prompts.get(0).get("description")).isBlank());
    }

    /**
     * 前提：getPrompt(core) 与 guideVersion。
     * 期望：正文含写库规矩；version 为 12 位十六进制。
     */
    @Test
    @Order(2)
    @DisplayName("getPrompt 与 guideVersion")
    void getPrompt_andGuideVersion() {
        Map<String, Object> payload = service.getPrompt(McpPromptResourceService.PROMPT_CORE);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) payload.get("messages");
        @SuppressWarnings("unchecked")
        Map<String, Object> content = (Map<String, Object>) messages.get(0).get("content");
        String text = String.valueOf(content.get("text"));
        assertTrue(text.contains("已立即写库") || text.contains("立即写库"));

        String version = service.guideVersion();
        assertEquals(12, version.length());
        assertTrue(version.matches("[0-9a-f]{12}"));
        assertEquals(version, service.guideVersion());
    }

    /**
     * 前提：sync Prompt。
     * 期望：文案含版本比对与写入 SKILL 步骤。
     */
    @Test
    @Order(3)
    @DisplayName("sync Prompt 含版本比对步骤")
    void getPrompt_sync_containsVersionSteps() {
        Map<String, Object> payload = service.getPrompt(McpPromptResourceService.PROMPT_SYNC_LOCAL_SKILL);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) payload.get("messages");
        @SuppressWarnings("unchecked")
        Map<String, Object> content = (Map<String, Object>) messages.get(0).get("content");
        String text = String.valueOf(content.get("text"));
        assertTrue(text.contains("guideVersion"));
        assertTrue(text.contains("get_mcp_guide_version"));
        assertTrue(text.contains("SKILL.md"));
        assertTrue(text.contains("qualitest_core"));
    }

    /**
     * 前提：read 未知 uri。
     * 期望：抛业务异常。
     */
    @Test
    @Order(4)
    @DisplayName("readResource 未知 uri 抛错")
    void readResource_unknownUri_throws() {
        assertThrows(com.qualitest.common.exception.ServiceException.class,
                () -> service.readResource("qualitest://docs/nope"));
    }

    /**
     * 前提：拼接 Cursor Skill。
     * 期望：含 frontmatter、guideVersion、与 CORE 同源的写库规矩。
     */
    @Test
    @Order(5)
    @DisplayName("loadCursorSkillText 为 frontmatter+CORE")
    void loadCursorSkillText_composesFrontmatterAndCore() {
        String skill = service.loadCursorSkillText();
        assertTrue(skill.startsWith("---"));
        assertTrue(skill.contains("name: qualitest"));
        assertTrue(skill.contains("guideVersion: " + service.guideVersion()));
        assertTrue(skill.contains("已立即写库") || skill.contains("立即写库"));
        assertTrue(skill.contains(service.loadCoreText().trim()));
    }
}
