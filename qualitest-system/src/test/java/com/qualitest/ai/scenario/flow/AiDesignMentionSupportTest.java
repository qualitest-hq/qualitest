package com.qualitest.ai.scenario.flow;

import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.scenario.flow.model.AiDesignMention;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 AiDesignMentionSupport：@提及格式化、拼进 LLM 用户内容、从 meta 解析与分类。
 * 边界：纯函数，无 DB / LLM。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=AiDesignMentionSupportTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AiDesignMentionSupportTest {

    /**
     * 前提：type=api，id=100，label=GET /login。
     * 期望：格式化为 api:100 (GET /login)。
     */
    @Test
    @Order(1)
    @DisplayName("格式化：api 提及含 id 与 label")
    void formatMentionLine_api() {
        AiDesignMention mention = new AiDesignMention();
        mention.setType("api");
        mention.setId("100");
        mention.setLabel("GET /login");
        assertEquals("api:100 (GET /login)", AiDesignMentionSupport.formatMentionLine(mention));
    }

    /**
     * 前提：type=var，subtype=flow，id=token。
     * 期望：格式化为 var:flow:token (flow.token)。
     */
    @Test
    @Order(2)
    @DisplayName("格式化：var 提及含 subtype 与 id")
    void formatMentionLine_var() {
        AiDesignMention mention = new AiDesignMention();
        mention.setType("var");
        mention.setSubtype("flow");
        mention.setId("token");
        mention.setLabel("flow.token");
        assertEquals("var:flow:token (flow.token)", AiDesignMentionSupport.formatMentionLine(mention));
    }

    /**
     * 前提：带一条 api 提及与用户描述。
     * 期望：内容含项目/流 id、mentions 列表，并以用户描述结尾。
     */
    @Test
    @Order(3)
    @DisplayName("拼内容：含 mentions 列表并以用户描述结尾")
    void buildUserLlmContent_withMentions() {
        AiDesignMention api = new AiDesignMention();
        api.setType("api");
        api.setId("1");
        api.setLabel("登录");

        String content = AiDesignMentionSupport.buildUserLlmContent(10L, 20L, "请补充登录节点", List.of(api));

        assertTrue(content.contains("testFlowId: 20"));
        assertTrue(content.contains("testProjectId: 10"));
        assertTrue(content.contains("mentions:"));
        assertTrue(content.contains("- api:1 (登录)"));
        assertTrue(content.endsWith("请补充登录节点"));
    }

    /**
     * 前提：mentions 为空。
     * 期望：不含 mentions: 段，仍含用户描述。
     */
    @Test
    @Order(4)
    @DisplayName("拼内容：无 mentions 时仍含用户描述")
    void buildUserLlmContent_withoutMentions() {
        String content = AiDesignMentionSupport.buildUserLlmContent(10L, 20L, "纯文本", List.of());
        assertFalse(content.contains("mentions:"));
        assertTrue(content.contains("用户描述："));
        assertTrue(content.contains("纯文本"));
    }

    /**
     * 前提：meta JSON 含 mentions 数组一条 run。
     * 期望：解析出 type=run、id=99。
     */
    @Test
    @Order(5)
    @DisplayName("解析 meta：读取 mentions 数组")
    void parseMentionsFromMeta_readsMentionsArray() {
        JSONObject meta = new JSONObject();
        AiDesignMention run = new AiDesignMention();
        run.setType("run");
        run.setId("99");
        run.setLabel("Run#99");
        meta.put("mentions", List.of(run));

        List<AiDesignMention> parsed = AiDesignMentionSupport.parseMentionsFromMeta(meta.toJSONString());
        assertEquals(1, parsed.size());
        assertEquals("run", parsed.get(0).getType());
        assertEquals("99", parsed.get(0).getId());
    }

    /**
     * 前提：meta 无 mentions，或 meta 为 null。
     * 期望：返回空列表。
     */
    @Test
    @Order(6)
    @DisplayName("解析 meta：无 mentions 时返回空列表")
    void parseMentionsFromMeta_returnsEmptyWhenNoMentions() {
        assertTrue(AiDesignMentionSupport.parseMentionsFromMeta("{}").isEmpty());
        assertTrue(AiDesignMentionSupport.parseMentionsFromMeta(null).isEmpty());
    }

    /**
     * 前提：同时有 api / node / run，且 api 重复。
     * 期望：去重后归入 scopeApiIds、contextNodeIds、contextRunId。
     */
    @Test
    @Order(7)
    @DisplayName("分类：api/node/run 去重后写入对应字段")
    void resolve_classifiesApiNodeRun() {
        AiDesignMention api = new AiDesignMention();
        api.setType("api");
        api.setId("1");
        AiDesignMention node = new AiDesignMention();
        node.setType("node");
        node.setId("node-a");
        AiDesignMention run = new AiDesignMention();
        run.setType("run");
        run.setId("7");

        var resolved = AiDesignMentionSupport.resolve(List.of(api, node, run, api));

        assertEquals(List.of(1L), resolved.getScopeApiIds());
        assertEquals(List.of("node-a"), resolved.getContextNodeIds());
        assertEquals(7L, resolved.getContextRunId());
    }
}
