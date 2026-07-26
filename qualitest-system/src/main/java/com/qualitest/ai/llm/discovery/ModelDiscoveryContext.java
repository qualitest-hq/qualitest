package com.qualitest.ai.llm.discovery;

import com.qualitest.ai.llm.template.ProviderTemplate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 模型发现执行上下文，携带连接参数与模板过滤规则。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelDiscoveryContext {

    /**
     * API Base URL
     */
    private String baseUrl;

    /**
     * API 密钥
     */
    private String apiKey;

    /**
     * 模型发现策略
     */
    private String discoveryType;

    /**
     * 模型发现接口相对路径；为空时使用适配器默认值
     */
    private String discoveryPath;

    /**
     * 关联的厂商模板，提供过滤规则与静态模型列表
     */
    private ProviderTemplate template;

    /**
     * HTTP 连接超时（毫秒）
     */
    private int connectTimeoutMs;

    /**
     * HTTP 读取超时（毫秒）
     */
    private int readTimeoutMs;
}
