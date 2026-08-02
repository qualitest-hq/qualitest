package com.qualitest.ai.scenario.flow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 测 FlowDesignScriptNodeNormalizer：空 language 默认 javascript、别名归一、timeoutMs 补默认。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowDesignScriptNodeNormalizerTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowDesignScriptNodeNormalizerTest {

    @Test
    @Order(1)
    @DisplayName("缺 language 时默认 javascript")
    void normalize_missingLanguage_defaultsJavascript() {
        Map<String, Object> data = new HashMap<>();
        data.put("source", "ctx.setFlow('x', 1)");
        FlowDesignScriptNodeNormalizer.normalize(data);
        assertEquals("javascript", data.get("language"));
    }

    @Test
    @Order(2)
    @DisplayName("空串 language 默认 javascript")
    void normalize_blankLanguage_defaultsJavascript() {
        Map<String, Object> data = new HashMap<>();
        data.put("language", "  ");
        data.put("source", "print(1)");
        FlowDesignScriptNodeNormalizer.normalize(data);
        assertEquals("javascript", data.get("language"));
    }

    @Test
    @Order(3)
    @DisplayName("js / JavaScript 别名归一为 javascript；Python 归一为 python")
    void normalize_aliases() {
        assertEquals("javascript", FlowDesignScriptNodeNormalizer.normalizeLanguage("js"));
        assertEquals("javascript", FlowDesignScriptNodeNormalizer.normalizeLanguage("JavaScript"));
        assertEquals("python", FlowDesignScriptNodeNormalizer.normalizeLanguage("Python"));
        assertEquals("python", FlowDesignScriptNodeNormalizer.normalizeLanguage("py"));
    }

    @Test
    @Order(4)
    @DisplayName("缺 timeoutMs 时写入默认值")
    void normalize_missingTimeout_defaults() {
        Map<String, Object> data = new HashMap<>();
        data.put("language", "javascript");
        FlowDesignScriptNodeNormalizer.normalize(data);
        assertEquals(5000L, data.get("timeoutMs"));
    }
}
