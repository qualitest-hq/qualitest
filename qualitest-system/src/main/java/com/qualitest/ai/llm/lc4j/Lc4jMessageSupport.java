package com.qualitest.ai.llm.lc4j;

import com.qualitest.ai.llm.LlmContentPart;
import com.qualitest.ai.llm.LlmMessage;
import com.qualitest.ai.llm.LlmToolCall;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话历史消息格式转换。
 * <p>
 * 把库内持久化的会话消息（role / content / toolCalls / contentParts）
 * 转成模型客户端可直接发送的消息对象。
 * 仅用于组装首轮上下文；Agent 循环内产生的新消息不再经此转换。
 */
public final class Lc4jMessageSupport {

    private Lc4jMessageSupport() {
    }

    /**
     * 批量转换历史消息。
     * 空列表返回空；role 无法识别的条目跳过。
     */
    public static List<ChatMessage> fromLlmMessages(List<LlmMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<ChatMessage> result = new ArrayList<>(messages.size());
        for (LlmMessage msg : messages) {
            ChatMessage converted = fromLlmMessage(msg);
            if (converted != null) {
                result.add(converted);
            }
        }
        return result;
    }

    /**
     * 单条转换。
     * <ul>
     *   <li>system → 系统消息</li>
     *   <li>user → 用户消息（支持纯文本或多段图文）</li>
     *   <li>assistant → 助手消息（可带 tool_calls）</li>
     *   <li>tool → 工具执行结果消息</li>
     * </ul>
     */
    public static ChatMessage fromLlmMessage(LlmMessage msg) {
        if (msg == null || msg.getRole() == null) {
            return null;
        }
        return switch (msg.getRole()) {
            case "system" -> SystemMessage.from(nullToEmpty(msg.getContent()));
            case "user" -> toUserMessage(msg);
            case "assistant" -> toAiMessage(msg);
            case "tool" -> ToolExecutionResultMessage.builder()
                    .id(msg.getToolCallId())
                    .text(nullToEmpty(msg.getContent()))
                    .isError(msg.isToolError())
                    .build();
            default -> null;
        };
    }

    /**
     * 用户消息：优先按 contentParts 组装图文；无有效分段时退回纯文本 content。
     * image 段支持 URL 或 base64（缺省媒体类型为 image/png）。
     */
    private static UserMessage toUserMessage(LlmMessage msg) {
        if (msg.getContentParts() != null && !msg.getContentParts().isEmpty()) {
            List<Content> contents = new ArrayList<>();
            for (LlmContentPart part : msg.getContentParts()) {
                if (part == null) {
                    continue;
                }
                if ("image".equals(part.getType())) {
                    if (part.getImageUrl() != null && !part.getImageUrl().isBlank()) {
                        contents.add(ImageContent.from(part.getImageUrl()));
                    } else if (part.getImageBase64() != null && !part.getImageBase64().isBlank()) {
                        String mediaType = part.getImageMediaType() != null ? part.getImageMediaType() : "image/png";
                        contents.add(ImageContent.from(part.getImageBase64(), mediaType));
                    }
                } else if (part.getText() != null) {
                    contents.add(TextContent.from(part.getText()));
                }
            }
            if (!contents.isEmpty()) {
                return UserMessage.from(contents);
            }
        }
        return UserMessage.from(nullToEmpty(msg.getContent()));
    }

    /**
     * 助手消息：正文 + 可选工具调用列表（id / name / argumentsJson）。
     */
    private static AiMessage toAiMessage(LlmMessage msg) {
        AiMessage.Builder builder = AiMessage.builder().text(msg.getContent());
        if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
            List<ToolExecutionRequest> requests = new ArrayList<>();
            for (LlmToolCall tc : msg.getToolCalls()) {
                if (tc == null) {
                    continue;
                }
                requests.add(ToolExecutionRequest.builder()
                        .id(tc.getId())
                        .name(tc.getName())
                        .arguments(tc.getArgumentsJson())
                        .build());
            }
            builder.toolExecutionRequests(requests);
        }
        return builder.build();
    }

    private static String nullToEmpty(String content) {
        return content == null ? "" : content;
    }
}
