package com.qualitest.flow.subflow;

import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.node.StepError;
import com.qualitest.flow.node.StepResult;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.qualitest.flow.support.FlowTestSections.begin;
import static com.qualitest.flow.support.FlowTestSections.end;
import static com.qualitest.flow.support.FlowTestSections.log;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SubflowIoSupport} 单元测试：子流 inputs 种子、outputs 合并与 childSteps 摘要。
 * <p>
 * 运行（qualitest 目录）：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=SubflowIoSupportTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SubflowIoSupportTest {

    /**
     * inputs 配置应从父 flow 解析占位符，并对数字/boolean 做类型推断。
     * 期望：childIn=abc；maxRetry=3L；enabled=true。
     */
    @Test
    @Order(1)
    void resolveInputSeed_placeholderAndCoercion() {
        begin("resolveInputSeed_placeholderAndCoercion");
        Map<String, Object> flow = new HashMap<>();
        flow.put("token", "abc");
        flow.put("retry", "3");
        FlowRunContext parent = FlowRunContext.builder().flow(flow).build();

        List<Map<String, Object>> inputs = List.of(
                inputRow("childIn", "{{flow.token}}"),
                inputRow("maxRetry", "{{flow.retry}}"),
                inputRow("enabled", "true")
        );
        Map<String, Object> seed = SubflowIoSupport.resolveInputSeed(parent, inputs);

        assertEquals("abc", seed.get("childIn"));
        assertEquals(3L, seed.get("maxRetry"));
        assertEquals(true, seed.get("enabled"));
        log("seed=" + seed);
        end("resolveInputSeed_placeholderAndCoercion");
    }

    /**
     * 子图成功后 outputs 应把子 flow 键映射到父 flow 的 flowKey。
     * 期望：parent.flow.parentToken=tok-9；merged 含同名键。
     */
    @Test
    @Order(2)
    void applyOutputs_mapsChildToParent() {
        begin("applyOutputs_mapsChildToParent");
        FlowRunContext parent = FlowRunContext.builder().flow(new HashMap<>()).build();
        Map<String, Object> childFlow = new HashMap<>();
        childFlow.put("accessToken", "tok-9");

        List<Map<String, Object>> outputs = List.of(
                outputRow("accessToken", "parentToken")
        );
        Map<String, Object> merged = SubflowIoSupport.applyOutputs(parent, childFlow, outputs);

        assertEquals("tok-9", parent.getFlow().get("parentToken"));
        assertEquals("tok-9", merged.get("parentToken"));
        log("parentToken=" + parent.getFlow().get("parentToken"));
        end("applyOutputs_mapsChildToParent");
    }

    /**
     * 失败子步应写入 error 摘要字段，供 Run 详情 childSteps 展示。
     * 期望：status=failed；error.code=TF_ASSERT_FAILED。
     */
    @Test
    @Order(3)
    void toChildStepSummaries_includesError() {
        begin("toChildStepSummaries_includesError");
        StepResult failed = StepResult.builder()
                .nodeId("n1")
                .nodeType("assert")
                .nodeName("断言")
                .status(StepResult.STATUS_FAILED)
                .durationMs(2)
                .error(StepError.of(FlowErrorCode.TF_ASSERT_FAILED, "断言失败"))
                .build();

        List<Map<String, Object>> rows = SubflowIoSupport.toChildStepSummaries(List.of(failed));
        assertEquals(1, rows.size());
        assertEquals("failed", rows.get(0).get("status"));
        @SuppressWarnings("unchecked")
        Map<String, Object> err = (Map<String, Object>) rows.get(0).get("error");
        assertNotNull(err);
        assertEquals(FlowErrorCode.TF_ASSERT_FAILED.getCode(), err.get("code"));
        log("childStep error=" + err.get("message"));
        end("toChildStepSummaries_includesError");
    }

    /**
     * inputs/outputs 映射列表空值判断。
     * 期望：null 与空列表为 true；非空列表为 false。
     */
    @Test
    @Order(4)
    void isEmptyMappingList_detectsEmpty() {
        begin("isEmptyMappingList_detectsEmpty");
        assertTrue(SubflowIoSupport.isEmptyMappingList(null));
        assertTrue(SubflowIoSupport.isEmptyMappingList(List.of()));
        assertFalse(SubflowIoSupport.isEmptyMappingList(List.of(outputRow("token", "token"))));
        log("null=true empty=true nonEmpty=false");
        end("isEmptyMappingList_detectsEmpty");
    }

    private static Map<String, Object> inputRow(String name, String value) {
        Map<String, Object> row = new HashMap<>();
        row.put("name", name);
        row.put("value", value);
        return row;
    }

    private static Map<String, Object> outputRow(String name, String flowKey) {
        Map<String, Object> row = new HashMap<>();
        row.put("name", name);
        row.put("flowKey", flowKey);
        return row;
    }
}
