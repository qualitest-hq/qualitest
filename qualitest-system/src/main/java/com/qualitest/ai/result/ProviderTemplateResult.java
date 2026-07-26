package com.qualitest.ai.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

/**
 * 内置厂商模板，供新建厂商时选择并自动填充默认配置。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderTemplateResult implements Serializable {

    /**
     * 模板唯一标识
     */
    private String templateId;

    /**
     * 模板展示名称
     */
    private String displayName;

    /**
     * 图标标识，供前端展示厂商图标
     */
    private String icon;

    /**
     * 协议类型（如 openai_compatible、anthropic_compatible），决定 LLM 请求格式
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
     * 推荐预置的模型 ID 列表，同步时可优先展示
     */
    private List<String> recommendedModels;

    /**
     * 厂商官方文档链接
     */
    private String docUrl;
}
