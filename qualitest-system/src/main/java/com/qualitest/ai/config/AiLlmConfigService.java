package com.qualitest.ai.config;

import org.springframework.stereotype.Service;

/**
 * AI 运行时默认常量。
 * <p>
 * 超时、token 上限、Agent 步数、tool 体积、search_apis 条数等均使用本类常量，不入 {@code sys_config}。
 * 厂商 base_url、api_key 由 {@code ai_llm_vendor} 表维护；默认模型按厂商/模型 sort_num 排序选取。
 */
@Service
public class AiLlmConfigService {

    /** OkHttp 连接超时（毫秒） */
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 10000;
    /** OkHttp 读超时（毫秒） */
    public static final int DEFAULT_READ_TIMEOUT_MS = 120000;
    /** OkHttp 写超时（毫秒） */
    public static final int DEFAULT_WRITE_TIMEOUT_MS = 30000;
    /** 上游 chat/completions 默认 max_tokens */
    public static final int DEFAULT_MAX_TOKENS = 8192;
    /** 单次 Agent 最大 tool 调用轮数 */
    public static final int DEFAULT_MAX_AGENT_STEPS = 8;
    /** 单次 tool 返回 JSON 字节兜底上限（语义裁剪后仍超限才触发） */
    public static final int DEFAULT_MAX_TOOL_RESULT_BYTES = 8192;
    /** search_apis 单次返回条数上限 */
    public static final int DEFAULT_MAX_SEARCH_APIS = 10;
    /** list_flows / list_subflow_templates 单次返回条数上限 */
    public static final int DEFAULT_MAX_LIST_FLOWS = 50;
    /** Anthropic API 版本号，写入 anthropic-version 请求头 */
    public static final String DEFAULT_ANTHROPIC_VERSION = "2023-06-01";
    /** Extended Thinking 单次请求的 token 预算默认值 */
    public static final int DEFAULT_THINKING_BUDGET_TOKENS = 8192;
    /** Agent 是否在 Anthropic 协议下启用 Prompt Caching（长 system 提示缓存） */
    public static final boolean DEFAULT_AGENT_PROMPT_CACHING = true;
    /** Agent 是否在 Anthropic 协议下启用 Extended Thinking（已由模型/会话开关取代，保留常量供测试参考） */
    public static final boolean DEFAULT_AGENT_EXTENDED_THINKING = false;

    /** 送入 LLM 的多轮历史条数上限（user + assistant 合计） */
    public static final int DEFAULT_HISTORY_COUNT_LIMIT = 10;

    /** 多轮历史 token 预算默认值（与 contextWindow 取 min 后再乘安全系数） */
    public static final int DEFAULT_HISTORY_TOKEN_BUDGET = 12000;

    public int getConnectTimeoutMs() {
        return DEFAULT_CONNECT_TIMEOUT_MS;
    }

    public int getReadTimeoutMs() {
        return DEFAULT_READ_TIMEOUT_MS;
    }

    public int getWriteTimeoutMs() {
        return DEFAULT_WRITE_TIMEOUT_MS;
    }

    public int getMaxTokens() {
        return DEFAULT_MAX_TOKENS;
    }

    public int getMaxSteps() {
        return DEFAULT_MAX_AGENT_STEPS;
    }

    public int getMaxToolResultBytes() {
        return DEFAULT_MAX_TOOL_RESULT_BYTES;
    }

    public int getMaxSearchApis() {
        return DEFAULT_MAX_SEARCH_APIS;
    }

    public int getMaxListFlows() {
        return DEFAULT_MAX_LIST_FLOWS;
    }

    /** Anthropic Messages API 版本号 */
    public String getAnthropicVersion() {
        return DEFAULT_ANTHROPIC_VERSION;
    }

    /** Extended Thinking token 预算 */
    public int getThinkingBudgetTokens() {
        return DEFAULT_THINKING_BUDGET_TOKENS;
    }

    /** Agent 是否启用 Prompt Caching */
    public boolean isAgentPromptCaching() {
        return DEFAULT_AGENT_PROMPT_CACHING;
    }

    /** Agent 是否启用 Extended Thinking */
    public boolean isAgentExtendedThinking() {
        return DEFAULT_AGENT_EXTENDED_THINKING;
    }

    /** 多轮历史条数上限 */
    public int getHistoryCountLimit() {
        return DEFAULT_HISTORY_COUNT_LIMIT;
    }

    /** 多轮历史 token 预算 */
    public int getHistoryTokenBudget() {
        return DEFAULT_HISTORY_TOKEN_BUDGET;
    }
}
