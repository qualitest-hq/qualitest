package com.qualitest.project.support.cursorskill;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 顶栏「MCP 造流 Agent 规程」弹框用的完整载荷：
 * 人话用法说明 + 示例提问 + 各编辑器规程 Tab。
 */
@Data
@Builder
public class McpAgentGuidesPayload {

    /** 怎么跟 AI 说话（弹框顶部，与具体编辑器无关） */
    private String howToUse;
    /** 简短提醒条目 */
    private List<String> tips;
    /** 可直接粘贴的示例提问 */
    private List<McpExamplePrompt> examples;
    /** 各编辑器 Tab */
    private List<McpAgentGuide> guides;
}
