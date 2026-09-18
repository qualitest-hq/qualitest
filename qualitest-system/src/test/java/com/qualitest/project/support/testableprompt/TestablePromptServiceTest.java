package com.qualitest.project.support.testableprompt;

import com.qualitest.common.exception.ServiceException;
import com.qualitest.common.utils.ClasspathMarkdownSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 TestablePromptService：分隔线截取与类型加载。
 * 边界：classpath 资源与 --- 截取；不启 Spring。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=TestablePromptServiceTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestablePromptServiceTest {

    private final TestablePromptService service = new TestablePromptService();

    /**
     * 前提：原文含说明头与 --- 分隔线。
     * 期望：只返回分隔线后正文。
     */
    @Test
    @Order(1)
    @DisplayName("分隔线后取正文")
    void extractAfterSeparator() {
        String raw = "说明\n\n---\n\n正文一行\n";
        assertEquals("正文一行", ClasspathMarkdownSupport.extractCopyablePrompt(raw, "可测提示词"));
    }

    /**
     * 前提：原文无分隔线。
     * 期望：返回全文 trim。
     */
    @Test
    @Order(2)
    @DisplayName("无分隔线取全文")
    void extractWholeWhenNoSeparator() {
        assertEquals("只有正文", ClasspathMarkdownSupport.extractCopyablePrompt("只有正文", "可测提示词"));
    }

    /**
     * 前提：分隔线后仅空白。
     * 期望：抛业务异常。
     */
    @Test
    @Order(3)
    @DisplayName("空正文抛异常")
    void extractEmptyThrows() {
        assertThrows(ServiceException.class,
                () -> ClasspathMarkdownSupport.extractCopyablePrompt("头\n---\n   ", "可测提示词"));
    }

    /**
     * 前提：加载增量类型。
     * 期望：含关键句。
     */
    @Test
    @Order(4)
    @DisplayName("增量提示词可加载且含关键句")
    void loadIncremental() {
        String text = service.loadPromptText(TestablePromptService.KIND_INCREMENTAL);
        assertTrue(text.contains("本次改动"));
        assertTrue(text.contains("质衡测试流 AI"));
    }

    /**
     * 前提：加载全量类型。
     * 期望：含按模块相关句。
     */
    @Test
    @Order(5)
    @DisplayName("全量提示词可加载且含关键句")
    void loadFull() {
        String text = service.loadPromptText(TestablePromptService.KIND_FULL);
        assertTrue(text.contains("按模块"));
        assertTrue(text.contains("质衡测试流 AI"));
    }

    /**
     * 前提：加载指定范围类型。
     * 期望：含占位符。
     */
    @Test
    @Order(6)
    @DisplayName("指定范围提示词可加载且含占位符")
    void loadScoped() {
        String text = service.loadPromptText(TestablePromptService.KIND_SCOPED);
        assertTrue(text.contains("<指定范围>"));
        assertTrue(text.contains("质衡测试流 AI"));
    }

    /**
     * 前提：未知 kind。
     * 期望：抛业务异常。
     */
    @Test
    @Order(7)
    @DisplayName("未知类型抛异常")
    void unknownKindThrows() {
        assertThrows(ServiceException.class, () -> service.loadPromptText("other"));
    }
}
