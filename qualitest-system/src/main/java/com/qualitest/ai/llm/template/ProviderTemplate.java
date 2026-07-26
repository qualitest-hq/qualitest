package com.qualitest.ai.llm.template;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 内置厂商模板配置，定义协议、默认地址、模型发现策略及过滤规则。
 */
@Getter
@Setter
public class ProviderTemplate {

    /**
     * 模板唯一标识
     */
    private String templateId;

    /**
     * 模板展示名称
     */
    private String displayName;

    /**
     * 图标标识
     */
    private String icon;

    /**
     * 协议类型（如 openai_compatible、anthropic_compatible）
     */
    private String provider;

    /**
     * 默认 API Base URL
     */
    private String defaultBaseUrl;

    /**
     * 是否允许用户修改 Base URL
     */
    private Boolean baseUrlEditable;

    /**
     * 模型发现策略（openai_models、anthropic_models、ollama_tags、static_list、none）
     */
    private String discoveryType;

    /**
     * 模型发现接口相对路径
     */
    private String discoveryPath;

    /**
     * 远端模型列表的包含/排除过滤规则
     */
    private ModelFilters modelFilters;

    /**
     * 静态模型 ID 列表；discoveryType 为 static_list 时使用
     */
    private List<String> staticModels;

    /**
     * 推荐预置的模型 ID 列表
     */
    private List<String> recommendedModels;

    /**
     * 厂商官方文档链接
     */
    private String docUrl;

    /**
     * 模型发现结果的通配符过滤规则
     */
    @Getter
    @Setter
    public static class ModelFilters {

        /**
         * 包含规则，模型 ID 需匹配其中任一模式才保留
         */
        private List<String> includePatterns;

        /**
         * 排除规则，模型 ID 匹配其中任一模式则丢弃
         */
        private List<String> excludePatterns;
    }
}
