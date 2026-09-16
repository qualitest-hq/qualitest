package com.qualitest.ai.llm.template;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 单个上游模型的静态元数据：展示名、是否支持思考、默认是否开思考、思考请求风格等。
 * 发现模型与组装运行时配置时用来补全能力字段。
 */
@Getter
@Setter
public class ModelMetadata {

    /** 界面展示名；通配条目可为 null，此时用上游 modelId 代替 */
    private String displayName;

    /** 是否支持思考模式：0 否，1 是 */
    private Integer thinkingCapable;

    /** 选中该模型时默认是否开启思考：0 关，1 开 */
    private Integer thinkingDefault;

    /**
     * 思考请求风格，决定请求体如何开关思考。
     * 空或未填：不写思考相关字段；
     * openai_reasoning_effort：开启时写 reasoning_effort；
     * deepseek_thinking：用 thinking.type=enabled|disabled，关闭必须显式 disabled。
     */
    private String thinkingControl;

    /** 上下文窗口 token 上限，可空 */
    private Integer contextWindow;

    /** 模型标签，如 chat、reasoning、vision */
    private List<String> tags;
}
