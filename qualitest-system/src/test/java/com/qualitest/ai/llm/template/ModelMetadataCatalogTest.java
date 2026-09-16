package com.qualitest.ai.llm.template;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测模型元数据目录的精确命中与前缀通配。
 * 边界：只读 classpath 的 model-metadata.json，不连库。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ModelMetadataCatalogTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ModelMetadataCatalogTest {

    private ModelMetadataCatalog catalog;

    @BeforeEach
    void setUp() throws Exception {
        catalog = new ModelMetadataCatalog();
        catalog.load();
    }

    /**
     * 前提：目录含 deepseek-v4-flash 精确条目。
     * 期望：可思考、默认关、deepseek_thinking 风格。
     */
    @Test
    @Order(1)
    @DisplayName("精确命中 V4 flash 元数据")
    void get_exactDeepseekV4Flash() {
        ModelMetadata meta = catalog.get("deepseek-v4-flash");

        assertNotNull(meta);
        assertEquals(1, meta.getThinkingCapable());
        assertEquals(0, meta.getThinkingDefault());
        assertEquals("deepseek_thinking", meta.getThinkingControl());
        assertEquals("DeepSeek V4 Flash", meta.getDisplayName());
    }

    /**
     * 前提：未知 V4 变体 id，只能靠 deepseek-v4-* 通配命中。
     * 期望：可思考、默认关、风格为 deepseek_thinking。
     */
    @Test
    @Order(2)
    @DisplayName("通配命中未单独收录的 V4 变体")
    void get_wildcardDeepseekV4() {
        ModelMetadata meta = catalog.get("deepseek-v4-mini-future");

        assertNotNull(meta);
        assertEquals(1, meta.getThinkingCapable());
        assertEquals(0, meta.getThinkingDefault());
        assertEquals("deepseek_thinking", meta.getThinkingControl());
    }

    /**
     * 前提：完全未收录的 modelId。
     * 期望：返回 null。
     */
    @Test
    @Order(3)
    @DisplayName("未收录模型返回 null")
    void get_unknownReturnsNull() {
        assertNull(catalog.get("totally-unknown-model-xyz"));
    }
}
