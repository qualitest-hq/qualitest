package com.qualitest.ai.tools.apidesign;

import com.qualitest.ai.config.AiLlmConfigService;
import com.qualitest.ai.scenario.apidesign.model.ApiDesignRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 从设计请求体组装工具上下文。
 */
@Component
@RequiredArgsConstructor
public class ApiDesignToolContextFactory {

    private final AiLlmConfigService aiLlmConfigService;

    /** 从设计请求组装工具上下文，不捕获 submit 结果。 */
    public ApiDesignToolContext fromDesignRequest(ApiDesignRequest request) {
        return fromDesignRequest(request, null);
    }

    /**
     * 从设计请求组装工具上下文。
     *
     * @param submitCapture 非空时记录 submit_api_design_patch 的规范化 patch 与校验结果
     */
    public ApiDesignToolContext fromDesignRequest(ApiDesignRequest request,
                                                   ApiDesignSubmitCapture submitCapture) {
        return ApiDesignToolContext.builder()
                .testProjectId(request.getTestProjectId())
                .testProjectApiId(request.getTestProjectApiId())
                .preRequestScript(request.getPreRequestScript())
                .postRequestScript(request.getPostRequestScript())
                .workbenchSnapshot(request.getWorkbenchSnapshot())
                .maxToolResultBytes(aiLlmConfigService.getMaxToolResultBytes())
                .submitCapture(submitCapture)
                .autopilotEnabled(request.isAutopilotEnabledEffective())
                .build();
    }
}
