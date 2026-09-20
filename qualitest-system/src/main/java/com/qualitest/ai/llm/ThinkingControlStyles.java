package com.qualitest.ai.llm;

/**
 * 思考请求风格常量：决定请求体里如何开关「思考/推理」。
 * <p>
 * 取值写在厂商模板 provider-templates.json 的 thinkingControl 字段，解析模型配置时带入运行时。
 */
public final class ThinkingControlStyles {

    /**
     * 仅在开启思考时写入 reasoning_effort；关闭时不写任何思考相关字段。
     */
    public static final String OPENAI_REASONING_EFFORT = "openai_reasoning_effort";

    /**
     * 用 thinking.type 显式开关思考：开启写 enabled，关闭写 disabled。
     * 该风格下上游默认会开思考，关闭时必须显式发 disabled；开启时可再带 reasoning_effort。
     */
    public static final String DEEPSEEK_THINKING = "deepseek_thinking";

    private ThinkingControlStyles() {
    }
}
