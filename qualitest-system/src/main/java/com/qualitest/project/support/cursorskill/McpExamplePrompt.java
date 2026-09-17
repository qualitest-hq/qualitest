package com.qualitest.project.support.cursorskill;

import lombok.Builder;
import lombok.Data;

/**
 * 一条可直接贴进编辑器对话的示例提问。
 */
@Data
@Builder
public class McpExamplePrompt {

    /** 短标题，如「列出测试流」 */
    private String label;
    /** 完整提示词正文 */
    private String text;
}
