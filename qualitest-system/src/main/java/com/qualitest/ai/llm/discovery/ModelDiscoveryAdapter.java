package com.qualitest.ai.llm.discovery;

import java.util.List;

/**
 * 模型发现适配器，按 discoveryType 从上游拉取可用模型列表。
 */
public interface ModelDiscoveryAdapter {

    /**
     * 适配器支持的发现策略标识
     */
    String discoveryType();

    /**
     * 执行模型发现，返回经过模板过滤后的远端模型列表
     */
    List<DiscoveredModel> discover(ModelDiscoveryContext context);
}
