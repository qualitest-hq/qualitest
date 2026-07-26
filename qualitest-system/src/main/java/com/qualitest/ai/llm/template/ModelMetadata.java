package com.qualitest.ai.llm.template;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 模型元数据，用于在发现或展示时补全展示名与思考能力等属性。
 */
@Getter
@Setter
public class ModelMetadata {

    /**
     * 展示名称
     */
    private String displayName;

    /**
     * 是否支持思考模式（0否 1是）
     */
    private Integer thinkingCapable;

    /**
     * 默认是否开启思考（0关 1开）
     */
    private Integer thinkingDefault;

    /**
     * 上下文窗口 token 上限
     */
    private Integer contextWindow;

    /**
     * 模型标签，如 chat、reasoning、vision
     */
    private List<String> tags;
}
