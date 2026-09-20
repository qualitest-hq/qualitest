package com.qualitest.ai.llm.modelsdev;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 models.dev 目录查找：精确 id、family、未命中兜底。
 * 边界：禁用远程，只读 classpath models.json 快照；不连 Redis。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ModelsDevCatalogTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ModelsDevCatalogTest {

    private ModelsDevCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new ModelsDevCatalog();
        ReflectionTestUtils.setField(catalog, "enabled", false);
        catalog.init();
    }

    /**
     * 前提：快照含 family=deepseek-flash 的模型。
     * 期望：按 family 命中可思考，且非兜底。
     */
    @Test
    @Order(1)
    @DisplayName("family 命中 deepseek-flash")
    void lookup_familyDeepseekFlash() {
        ModelsDevModelInfo info = catalog.lookup(null, "deepseek-flash");

        assertNotNull(info);
        assertFalse(info.isFallback());
        assertTrue(info.isThinkingCapable());
        assertFalse(info.isThinkingDefault());
        assertTrue(info.getContextWindow() > 32768);
        assertNotNull(info.getDisplayName());
    }

    /**
     * 前提：快照含 deepseek/deepseek-v4.1-flash。
     * 期望：按 id 末段可命中。
     */
    @Test
    @Order(2)
    @DisplayName("id 末段命中 V4.1 flash")
    void lookup_shortId() {
        ModelsDevModelInfo info = catalog.lookup(null, "deepseek-v4.1-flash");

        assertNotNull(info);
        assertFalse(info.isFallback());
        assertTrue(info.isThinkingCapable());
    }

    /**
     * 前提：完全未知 modelId。
     * 期望：lookup 为 null；lookupOrDefault 返回可思考兜底。
     */
    @Test
    @Order(3)
    @DisplayName("未命中返回兜底")
    void lookupOrDefault_unknown() {
        assertNull(catalog.lookup(null, "totally-unknown-model-xyz"));

        ModelsDevModelInfo info = catalog.lookupOrDefault(null, "totally-unknown-model-xyz");
        assertTrue(info.isFallback());
        assertTrue(info.isThinkingCapable());
        assertFalse(info.isThinkingDefault());
        assertEquals("totally-unknown-model-xyz", info.getDisplayName());
        assertEquals(ModelsDevCatalog.DEFAULT_CONTEXT_WINDOW, info.getContextWindow());
    }
}
