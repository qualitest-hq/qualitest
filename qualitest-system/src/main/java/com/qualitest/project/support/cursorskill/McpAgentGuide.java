package com.qualitest.project.support.cursorskill;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 一份可复制的 MCP 造流 Agent 规程（Cursor Skill 或其它编辑器规则）。
 */
@Data
@Builder
public class McpAgentGuide {

    /** 稳定 id，供前端 Tab 切换 */
    private String id;
    /** Tab 标题 */
    private String title;
    /** 弹窗内简短说明 */
    private String intro;
    /** 使用步骤（展示用） */
    private List<String> steps;
    /** 复制成功后提示的保存位置 */
    private String saveHint;
    /** 可复制全文 */
    private String content;
}
