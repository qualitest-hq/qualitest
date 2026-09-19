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
 * 测 MCP Prompt/Resource 组装、门控裁剪与合成 guideVersion。
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
     * 前提：只读门控 getPrompt(core) 与 base / 合成 guideVersion。
     * 期望：无 import_apis / submit_；base 为 12 位；合成串四种后缀正确。
     */
    @Test
    @Order(2)
    @DisplayName("只读 core 与合成 guideVersion")
    void getPrompt_readonly_andCompositeGuideVersion() {
        Map<String, Object> payload = service.getPrompt(McpPromptResourceService.PROMPT_CORE, false, false);
        String text = promptText(payload);
        assertFalse(text.contains("import_apis"));
        assertFalse(text.contains("submit_*") || text.contains("`submit_"));
        assertFalse(text.contains("create_flow"));
        assertTrue(text.contains("tools/list"));
        assertTrue(text.contains("Web / IDEA") || text.contains("IDEA 插件"));

        String base = service.baseGuideVersion();
        assertEquals(12, base.length());
        assertTrue(base.matches("[0-9a-f]{12}"));
        assertEquals(base, service.guideVersion(false, false));
        assertEquals(base + "+autopilot", service.guideVersion(true, false));
        assertEquals(base + "+importApis", service.guideVersion(false, true));
        assertEquals(base + "+autopilot+importApis", service.guideVersion(true, true));
    }

    /**
     * 前提：仅开导入门控。
     * 期望：含 import_apis；不含造流 submit / create_flow 段。
     */
    @Test
    @Order(3)
    @DisplayName("仅导入开：core 有 import_apis 无写流")
    void getPrompt_importOnly() {
        String text = promptText(service.getPrompt(McpPromptResourceService.PROMPT_CORE, false, true));
        assertTrue(text.contains("import_apis"));
        assertTrue(text.contains("缺接口时充实接口库"));
        assertFalse(text.contains("create_flow"));
        assertFalse(text.contains("`submit_"));
    }

    /**
     * 前提：仅开写流门控。
     * 期望：含 submit / create_flow / update_flow_meta；不含 import_apis。
     */
    @Test
    @Order(4)
    @DisplayName("仅写流开：core 有 submit 无 import_apis")
    void getPrompt_autopilotOnly() {
        String text = promptText(service.getPrompt(McpPromptResourceService.PROMPT_CORE, true, false));
        assertTrue(text.contains("create_flow"));
        assertTrue(text.contains("update_flow_meta"));
        assertTrue(text.contains("submit_*") || text.contains("`submit_"));
        assertTrue(text.contains("run_test_flow"));
        assertFalse(text.contains("import_apis"));
    }

    /**
     * 前提：sync Prompt。
     * 期望：文案含合成串比对与写入 SKILL 步骤。
     */
    @Test
    @Order(5)
    @DisplayName("sync Prompt 含版本比对步骤")
    void getPrompt_sync_containsVersionSteps() {
        Map<String, Object> payload = service.getPrompt(
                McpPromptResourceService.PROMPT_SYNC_LOCAL_SKILL, false, false);
        String text = promptText(payload);
        assertTrue(text.contains("guideVersion"));
        assertTrue(text.contains("get_mcp_guide_version"));
        assertTrue(text.contains("SKILL.md"));
        assertTrue(text.contains("qualitest_core"));
        assertTrue(text.contains("+autopilot") || text.contains("合成串"));
    }

    /**
     * 前提：read 未知 uri。
     * 期望：抛业务异常。
     */
    @Test
    @Order(6)
    @DisplayName("readResource 未知 uri 抛错")
    void readResource_unknownUri_throws() {
        assertThrows(com.qualitest.common.exception.ServiceException.class,
                () -> service.readResource("qualitest://docs/nope", false, false));
    }

    /**
     * 前提：拼接 Cursor Skill（两道都开）。
     * 期望：含 frontmatter、合成 guideVersion、import 与写流规矩。
     */
    @Test
    @Order(7)
    @DisplayName("loadCursorSkillText 门控全开")
    void loadCursorSkillText_bothGates() {
        String skill = service.loadCursorSkillText(true, true);
        assertTrue(skill.startsWith("---"));
        assertTrue(skill.contains("name: qualitest"));
        assertTrue(skill.contains("guideVersion: " + service.guideVersion(true, true)));
        assertTrue(skill.contains("import_apis"));
        assertTrue(skill.contains("已立即写库") || skill.contains("立即写库"));
        assertTrue(skill.contains("create_flow"));
    }

    /**
     * 前提：applyGates 静态裁剪。
     * 期望：关块删除；开块仅剥标记。
     */
    @Test
    @Order(8)
    @DisplayName("applyGates 裁剪注释块")
    void applyGates_stripsBlocks() {
        String src = "A\n<!-- mcp:import -->\nIMP\n<!-- /mcp:import -->\n"
                + "<!-- mcp:autopilot -->\nAP\n<!-- /mcp:autopilot -->\nB\n";
        assertEquals("A\nB\n", McpPromptResourceService.applyGates(src, false, false));
        assertTrue(McpPromptResourceService.applyGates(src, false, true).contains("IMP"));
        assertFalse(McpPromptResourceService.applyGates(src, false, true).contains("AP"));
        assertFalse(McpPromptResourceService.applyGates(src, false, true).contains("mcp:import"));
        assertTrue(McpPromptResourceService.applyGates(src, true, true).contains("IMP"));
        assertTrue(McpPromptResourceService.applyGates(src, true, true).contains("AP"));
    }

    @SuppressWarnings("unchecked")
    private static String promptText(Map<String, Object> payload) {
        List<Map<String, Object>> messages = (List<Map<String, Object>>) payload.get("messages");
        Map<String, Object> content = (Map<String, Object>) messages.get(0).get("content");
        return String.valueOf(content.get("text"));
    }
}
