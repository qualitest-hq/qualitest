package com.qualitest.project.support.testableprompt;

import com.qualitest.common.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 TestablePromptService：分隔线截取与类型加载。
 */
class TestablePromptServiceTest {

    private final TestablePromptService service = new TestablePromptService();

    @Test
    @DisplayName("分隔线后取正文")
    void extractAfterSeparator() {
        String raw = "说明\n\n---\n\n正文一行\n";
        assertEquals("正文一行", TestablePromptService.extractCopyablePrompt(raw));
    }

    @Test
    @DisplayName("无分隔线取全文")
    void extractWholeWhenNoSeparator() {
        assertEquals("只有正文", TestablePromptService.extractCopyablePrompt("只有正文"));
    }

    @Test
    @DisplayName("空正文抛异常")
    void extractEmptyThrows() {
        assertThrows(ServiceException.class, () -> TestablePromptService.extractCopyablePrompt("头\n---\n   "));
    }

    @Test
    @DisplayName("增量提示词可加载且含关键句")
    void loadIncremental() {
        String text = service.loadPromptText(TestablePromptService.KIND_INCREMENTAL);
        assertTrue(text.contains("本次改动"));
        assertTrue(text.contains("质衡测试流 AI"));
    }

    @Test
    @DisplayName("全量提示词可加载且含关键句")
    void loadFull() {
        String text = service.loadPromptText(TestablePromptService.KIND_FULL);
        assertTrue(text.contains("按模块"));
        assertTrue(text.contains("质衡测试流 AI"));
    }

    @Test
    @DisplayName("未知类型抛异常")
    void unknownKindThrows() {
        assertThrows(ServiceException.class, () -> service.loadPromptText("other"));
    }
}
