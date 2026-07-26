package com.qualitest.ai.scenario.flow;

import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.scenario.flow.model.AiDesignMention;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AiDesignMentionSupport} 单元测试。
 */
class AiDesignMentionSupportTest {

    @Test
    void formatMentionLine_api() {
        AiDesignMention mention = new AiDesignMention();
        mention.setType("api");
        mention.setId("100");
        mention.setLabel("GET /login");
        assertEquals("api:100 (GET /login)", AiDesignMentionSupport.formatMentionLine(mention));
    }

    @Test
    void formatMentionLine_var() {
        AiDesignMention mention = new AiDesignMention();
        mention.setType("var");
        mention.setSubtype("flow");
        mention.setId("token");
        mention.setLabel("flow.token");
        assertEquals("var:flow:token (flow.token)", AiDesignMentionSupport.formatMentionLine(mention));
    }

    @Test
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

    @Test
    void buildUserLlmContent_withoutMentions() {
        String content = AiDesignMentionSupport.buildUserLlmContent(10L, 20L, "纯文本", List.of());
        assertFalse(content.contains("mentions:"));
        assertTrue(content.contains("用户描述："));
        assertTrue(content.contains("纯文本"));
    }

    @Test
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

    @Test
    void parseMentionsFromMeta_returnsEmptyWhenNoMentions() {
        assertTrue(AiDesignMentionSupport.parseMentionsFromMeta("{}").isEmpty());
        assertTrue(AiDesignMentionSupport.parseMentionsFromMeta(null).isEmpty());
    }

    @Test
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
