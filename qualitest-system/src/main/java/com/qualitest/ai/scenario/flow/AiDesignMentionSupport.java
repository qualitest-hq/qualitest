package com.qualitest.ai.scenario.flow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.scenario.flow.model.AiDesignMention;
import com.qualitest.ai.tools.FlowDesignIds;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 设计面板 @ 引用的集中处理。
 * <p>
 * 职责：
 * <ul>
 *   <li>将 mentions 列表归类为工具调用所需的 API 范围、节点范围、Run 上下文</li>
 *   <li>拼接送入 LLM 的 user 消息正文（含 testFlowId、mentions 块、用户描述）</li>
 *   <li>从会话落库的 result_meta_json 中解析 mentions，供多轮历史重建 LLM 上下文</li>
 * </ul>
 */
public final class AiDesignMentionSupport {

    private AiDesignMentionSupport() {
    }

    /**
     * 从 mentions 解析出的工具调用范围字段。
     */
    @Getter
    public static final class ResolvedMentionContext {
        /** search_apis 等工具可限定的 API id 列表 */
        private final List<Long> scopeApiIds;
        /** get_node_detail 等工具可聚焦的画布节点 id */
        private final List<String> contextNodeIds;
        /** get_run_failure 等工具关联的运行记录 id，多条 run mention 时取首条 */
        private final Long contextRunId;

        ResolvedMentionContext(List<Long> scopeApiIds, List<String> contextNodeIds, Long contextRunId) {
            this.scopeApiIds = scopeApiIds;
            this.contextNodeIds = contextNodeIds;
            this.contextRunId = contextRunId;
        }
    }

    /**
     * 按 mention.type 归类为 api / node / run 范围。
     * var 等类型仅出现在 LLM 文本中，不进入工具范围。
     */
    public static ResolvedMentionContext resolve(List<AiDesignMention> mentions) {
        List<Long> scopeApiIds = new ArrayList<>();
        List<String> contextNodeIds = new ArrayList<>();
        Long contextRunId = null;
        if (mentions == null) {
            return new ResolvedMentionContext(scopeApiIds, contextNodeIds, contextRunId);
        }
        for (AiDesignMention mention : mentions) {
            if (mention == null || mention.getType() == null) {
                continue;
            }
            switch (mention.getType()) {
                case "api" -> addApiId(scopeApiIds, mention.getId());
                case "node" -> addNodeId(contextNodeIds, mention.getId());
                case "run" -> {
                    if (contextRunId == null) {
                        contextRunId = FlowDesignIds.parseLongId(mention.getId());
                    }
                }
                default -> {
                    // var 等类型仅写入 user 文本，不进入工具范围
                }
            }
        }
        return new ResolvedMentionContext(scopeApiIds, contextNodeIds, contextRunId);
    }

    /**
     * 将单条 mention 格式化为 LLM mentions 块中的一行。
     * var 类型输出 {@code var:subtype:id (label)}，其余为 {@code type:id (label)}。
     */
    public static String formatMentionLine(AiDesignMention mention) {
        String label = mention.getLabel() != null ? mention.getLabel().trim() : "";
        if ("var".equals(mention.getType())) {
            String subtype = mention.getSubtype() != null ? mention.getSubtype() : "";
            return "var:" + subtype + ":" + mention.getId() + (label.isEmpty() ? "" : " (" + label + ")");
        }
        return mention.getType() + ":" + mention.getId() + (label.isEmpty() ? "" : " (" + label + ")");
    }

    /**
     * 拼接本轮送入 LLM 的 user 消息正文。
     * 结构：testFlowId / testProjectId → mentions 列表 → 用户自然语言描述。
     */
    public static String buildUserLlmContent(Long testProjectId,
                                             Long testFlowId,
                                             String prompt,
                                             List<AiDesignMention> mentions) {
        StringBuilder user = new StringBuilder();
        if (testFlowId != null) {
            user.append("testFlowId: ").append(testFlowId).append("\n");
        }
        if (testProjectId != null) {
            user.append("testProjectId: ").append(testProjectId).append("\n");
        }
        if (mentions != null && !mentions.isEmpty()) {
            user.append("mentions:\n");
            for (AiDesignMention mention : mentions) {
                if (mention == null || mention.getType() == null) {
                    continue;
                }
                user.append("- ").append(formatMentionLine(mention)).append("\n");
            }
        }
        String trimmedPrompt = prompt != null ? prompt.trim() : "";
        user.append("\n用户描述：\n").append(trimmedPrompt);
        return user.toString();
    }

    /**
     * 从 user 消息落库的 result_meta_json 读取 mentions 数组。
     * 无 mentions 或解析失败时返回空列表，调用方可回退为纯 message_content 文本。
     */
    public static List<AiDesignMention> parseMentionsFromMeta(String resultMetaJson) {
        if (resultMetaJson == null || resultMetaJson.isBlank()) {
            return List.of();
        }
        try {
            JSONObject meta = JSON.parseObject(resultMetaJson);
            JSONArray mentionsArr = meta.getJSONArray("mentions");
            if (mentionsArr != null && !mentionsArr.isEmpty()) {
                List<AiDesignMention> parsed = mentionsArr.toList(AiDesignMention.class);
                return parsed != null ? parsed : List.of();
            }
        } catch (Exception ignored) {
            // 解析失败时返回空列表，由调用方回退纯文本
        }
        return List.of();
    }

    /** 去重追加 API id 到 scope 列表 */
    private static void addApiId(List<Long> scopeApiIds, String id) {
        Long parsed = FlowDesignIds.parseLongId(id);
        if (parsed != null && !scopeApiIds.contains(parsed)) {
            scopeApiIds.add(parsed);
        }
    }

    /** 去重追加节点 id 到 context 列表 */
    private static void addNodeId(List<String> contextNodeIds, String id) {
        if (id != null && !id.isBlank() && !contextNodeIds.contains(id)) {
            contextNodeIds.add(id);
        }
    }
}
