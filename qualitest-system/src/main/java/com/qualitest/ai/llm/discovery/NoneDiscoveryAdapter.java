package com.qualitest.ai.llm.discovery;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 无远端列表接口的厂商：不发起 HTTP 请求，始终返回空列表（模型需手工录入）。
 */
@Component
public class NoneDiscoveryAdapter implements ModelDiscoveryAdapter {

    @Override
    public String discoveryType() {
        return "none";
    }

    @Override
    public List<DiscoveredModel> discover(ModelDiscoveryContext context) {
        return Collections.emptyList();
    }
}
