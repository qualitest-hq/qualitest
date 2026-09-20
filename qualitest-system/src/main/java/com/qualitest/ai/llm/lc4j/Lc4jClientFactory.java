package com.qualitest.ai.llm.lc4j;

import com.qualitest.ai.llm.LlmClientException;
import com.qualitest.ai.llm.LlmModelConfig;
import com.qualitest.ai.llm.LlmProviderTypes;
import com.qualitest.ai.llm.ThinkingControlStyles;
import com.qualitest.ai.llm.discovery.LlmDiscoveryUrlUtils;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiModelCatalog;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OpenAI 兼容厂商的客户端工厂。
 * <p>
 * 根据厂商 baseUrl、apiKey、模型名等连接信息，分别构建：
 * <ul>
 *   <li>模型目录客户端：管理端测连、拉取可用模型列表</li>
 *   <li>同步对话客户端：会话摘要等一次性调用</li>
 *   <li>流式对话客户端：造流 / API 设计等 Agent 工具循环</li>
 * </ul>
 * 当前仅支持 openai_compatible；anthropic_compatible 会直接报错。
 */
@Component
public class Lc4jClientFactory {

    /**
     * 构建模型目录客户端。
     * 用于按 baseUrl + apiKey 请求上游「列出模型」接口。
     *
     * @param baseUrl          厂商 API 根地址
     * @param apiKey           API Key
     * @param connectTimeoutMs 连接超时（毫秒），小于 1000 时按 1000 处理
     * @param readTimeoutMs    读超时（毫秒），小于 1000 时按 1000 处理
     */
    public OpenAiModelCatalog openAiModelCatalog(String baseUrl, String apiKey,
                                                 int connectTimeoutMs, int readTimeoutMs) {
        requireOpenAiKey(apiKey);
        return OpenAiModelCatalog.builder()
                .baseUrl(normalizeOpenAiBaseUrl(baseUrl))
                .apiKey(apiKey.trim())
                .connectTimeout(Duration.ofMillis(Math.max(connectTimeoutMs, 1000)))
                .readTimeout(Duration.ofMillis(Math.max(readTimeoutMs, 1000)))
                .build();
    }

    /**
     * 构建同步对话客户端。
     * 一次请求返回完整助手消息，不做 SSE 增量推送。
     *
     * @param modelConfig      已解析的模型运行时配置（含 baseUrl、密钥、模型名、超时、maxTokens、思考控制方式）
     * @param reasoningEnabled 本轮是否开启思考链（影响是否回传/下发 thinking，以及请求体里的思考开关参数）
     */
    public ChatModel chatModel(LlmModelConfig modelConfig, boolean reasoningEnabled) {
        requireOpenAiCompatible(modelConfig);
        requireOpenAiKey(modelConfig.getApiKey());
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .baseUrl(normalizeOpenAiBaseUrl(modelConfig.getBaseUrl()))
                .apiKey(modelConfig.getApiKey().trim())
                .modelName(modelConfig.getModelName())
                .timeout(Duration.ofMillis(Math.max(modelConfig.getReadTimeoutMs(), 1000)))
                .maxTokens(modelConfig.getMaxTokens() > 0 ? modelConfig.getMaxTokens() : null)
                .returnThinking(reasoningEnabled)
                .sendThinking(reasoningEnabled)
                .defaultRequestParameters(buildThinkingParameters(modelConfig, reasoningEnabled));
        return builder.build();
    }

    /**
     * 构建流式对话客户端。
     * 通过 SSE 推送正文增量与思考增量，供前端实时展示；工具调用 id 不做跨 chunk 累加合并。
     *
     * @param modelConfig      已解析的模型运行时配置
     * @param reasoningEnabled 本轮是否开启思考链
     */
    public StreamingChatModel streamingChatModel(LlmModelConfig modelConfig, boolean reasoningEnabled) {
        requireOpenAiCompatible(modelConfig);
        requireOpenAiKey(modelConfig.getApiKey());
        return OpenAiStreamingChatModel.builder()
                .baseUrl(normalizeOpenAiBaseUrl(modelConfig.getBaseUrl()))
                .apiKey(modelConfig.getApiKey().trim())
                .modelName(modelConfig.getModelName())
                .timeout(Duration.ofMillis(Math.max(modelConfig.getReadTimeoutMs(), 1000)))
                .maxTokens(modelConfig.getMaxTokens() > 0 ? modelConfig.getMaxTokens() : null)
                .returnThinking(reasoningEnabled)
                .sendThinking(reasoningEnabled)
                .accumulateToolCallId(false)
                .defaultRequestParameters(buildThinkingParameters(modelConfig, reasoningEnabled))
                .build();
    }

    /**
     * 按厂商模板的思考控制方式，组装请求默认参数。
     * <ul>
     *   <li>deepseek_thinking：写入 customParameters.thinking.type = enabled/disabled；开启时另设 reasoning_effort=medium</li>
     *   <li>openai_reasoning_effort：仅在开启思考时设 reasoning_effort=medium</li>
     *   <li>未配置控制方式：不附加额外参数</li>
     * </ul>
     *
     * @param modelConfig      模型配置（读取 thinkingControl）
     * @param reasoningEnabled 本轮是否开启思考
     */
    public static OpenAiChatRequestParameters buildThinkingParameters(LlmModelConfig modelConfig,
                                                                      boolean reasoningEnabled) {
        String style = modelConfig != null ? modelConfig.getThinkingControl() : null;
        OpenAiChatRequestParameters.Builder builder = OpenAiChatRequestParameters.builder();
        if (style == null || style.isBlank()) {
            return builder.build();
        }
        if (ThinkingControlStyles.DEEPSEEK_THINKING.equals(style)) {
            Map<String, Object> thinking = new LinkedHashMap<>();
            thinking.put("type", reasoningEnabled ? "enabled" : "disabled");
            Map<String, Object> custom = new LinkedHashMap<>();
            custom.put("thinking", thinking);
            builder.customParameters(custom);
            if (reasoningEnabled) {
                builder.reasoningEffort("medium");
            }
            return builder.build();
        }
        if (ThinkingControlStyles.OPENAI_REASONING_EFFORT.equals(style) && reasoningEnabled) {
            builder.reasoningEffort("medium");
        }
        return builder.build();
    }

    /**
     * 将厂商填写的 Base URL 规范为带 /v1/ 后缀的地址，供 OpenAI 兼容客户端拼接路径。
     * 已以 /v1 结尾时只补尾斜杠；否则追加 /v1/。
     */
    public static String normalizeOpenAiBaseUrl(String baseUrl) {
        String normalized = LlmDiscoveryUrlUtils.normalizeBaseUrl(baseUrl);
        if (normalized.endsWith("/v1")) {
            return normalized + "/";
        }
        return normalized + "/v1/";
    }

    /** 校验模型配置可用且协议为 OpenAI 兼容；Anthropic 或空模型名直接抛业务异常。 */
    private static void requireOpenAiCompatible(LlmModelConfig modelConfig) {
        if (modelConfig == null) {
            throw new LlmClientException("模型配置为空");
        }
        if (LlmProviderTypes.isAnthropic(modelConfig.getProvider())) {
            throw new LlmClientException("PoC 暂未接入 Anthropic，请选用 OpenAI 兼容厂商（如 DeepSeek）");
        }
        if (!LlmProviderTypes.isOpenAiCompatible(modelConfig.getProvider())) {
            throw new LlmClientException("不支持的协议标识: " + modelConfig.getProvider());
        }
        if (modelConfig.getModelName() == null || modelConfig.getModelName().isBlank()) {
            throw new LlmClientException("模型名称未配置");
        }
    }

    /** 校验 API Key 非空。 */
    private static void requireOpenAiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new LlmClientException("API Key 未配置");
        }
    }
}
