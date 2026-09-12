package com.qualitest.ai.tools.apidesign;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.scenario.apidesign.ApiDesignPatchNormalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 SubmitApiDesignPatchTool：半自动 hint 含「等待用户确认」；全自动 hint 含「自动应用」；
 * ApiDesignRequest.isAutopilotEnabledEffective 仅 true 为开。
 * 边界：真实 Normalizer，无 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=SubmitApiDesignPatchToolTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SubmitApiDesignPatchToolTest {

    private final SubmitApiDesignPatchTool tool = new SubmitApiDesignPatchTool(new ApiDesignPatchNormalizer());

    private static Map<String, Object> validScriptArgs() {
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("target", "script");
        change.put("phase", "pre");
        change.put("action", "update");
        change.put("content", "api.variables.set('k', 'v');");
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("summary", "测签名");
        args.put("changes", List.of(change));
        return args;
    }

    @Test
    @Order(1)
    @DisplayName("半自动 hint 等待用户确认")
    void execute_semiAuto_hintWaitsConfirm() {
        ApiDesignToolContext ctx = ApiDesignToolContext.builder()
                .submitCapture(new ApiDesignSubmitCapture())
                .autopilotEnabled(false)
                .build();
        JSONObject root = JSON.parseObject(tool.execute(validScriptArgs(), ctx));
        assertTrue(root.getBooleanValue("received"));
        assertTrue(root.getString("hint").contains("等待用户确认"));
        assertFalse(root.getString("hint").contains("全自动"));
    }

    @Test
    @Order(2)
    @DisplayName("全自动 hint 说明自动应用草稿")
    void execute_autopilot_hintAutoApply() {
        ApiDesignToolContext ctx = ApiDesignToolContext.builder()
                .submitCapture(new ApiDesignSubmitCapture())
                .autopilotEnabled(true)
                .build();
        JSONObject root = JSON.parseObject(tool.execute(validScriptArgs(), ctx));
        assertTrue(root.getBooleanValue("received"));
        assertTrue(root.getString("hint").contains("全自动"));
        assertTrue(root.getString("hint").contains("自动应用"));
        assertFalse(root.getString("hint").contains("等待用户确认"));
    }

    @Test
    @Order(3)
    @DisplayName("请求 isAutopilotEnabledEffective 仅 true 为开")
    void request_autopilotEffective() {
        com.qualitest.ai.scenario.apidesign.model.ApiDesignRequest req =
                new com.qualitest.ai.scenario.apidesign.model.ApiDesignRequest();
        assertFalse(req.isAutopilotEnabledEffective());
        req.setAutopilotEnabled(false);
        assertFalse(req.isAutopilotEnabledEffective());
        req.setAutopilotEnabled(true);
        assertTrue(req.isAutopilotEnabledEffective());
    }
}
