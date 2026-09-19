package com.qualitest.flow.subflow;


import com.qualitest.flow.run.RunStatus;
import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.node.StepError;
import com.qualitest.flow.node.StepResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 SubflowIoSupport：inputs 种子、outputs 合并、childSteps 摘要与空映射判定。
 * 边界：占位符与类型推断；无 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=SubflowIoSupportTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SubflowIoSupportTest {

    /**
     * 前提：父 flow 含 token/retry；inputs 用占位符与字面 true。
     * 期望：childIn=abc；maxRetry=3L；enabled=true。
     */
    @Test
    @Order(1)
    @DisplayName("inputs：占位符解析与类型推断")
    void resolveInputSeed_placeholderAndCoercion() {
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
    }

    /**
     * 前提：子 flow.accessToken=tok-9；outputs 映射到 parentToken。
     * 期望：父 flow.parentToken=tok-9；merged 含同名键。
     */
    @Test
    @Order(2)
    @DisplayName("outputs：子 flow 合并到父 flow")
    void applyOutputs_mapsChildToParent() {
        FlowRunContext parent = FlowRunContext.builder().flow(new HashMap<>()).build();
        Map<String, Object> childFlow = new HashMap<>();
        childFlow.put("accessToken", "tok-9");

        List<Map<String, Object>> outputs = List.of(
                outputRow("accessToken", "parentToken")
        );
        Map<String, Object> merged = SubflowIoSupport.applyOutputs(parent, childFlow, outputs);

        assertEquals("tok-9", parent.getFlow().get("parentToken"));
        assertEquals("tok-9", merged.get("parentToken"));
    }

    /**
     * 前提：失败 StepResult（TF_ASSERT_FAILED）。
     * 期望：摘要 status=failed；error.code=TF_ASSERT_FAILED。
     */
    @Test
    @Order(3)
    @DisplayName("摘要：childSteps 含失败错误码")
    void toChildStepSummaries_includesError() {
        StepResult failed = StepResult.builder()
                .nodeId("n1")
                .nodeType("assert")
                .nodeName("断言")
                .status(RunStatus.FAILED.getCode())
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
    }

    /**
     * 前提：映射列表为 null / 空 / 非空。
     * 期望：前两者 true；非空 false。
     */
    @Test
    @Order(4)
    @DisplayName("判定：空映射列表识别")
    void isEmptyMappingList_detectsEmpty() {
        assertTrue(SubflowIoSupport.isEmptyMappingList(null));
        assertTrue(SubflowIoSupport.isEmptyMappingList(List.of()));
        assertFalse(SubflowIoSupport.isEmptyMappingList(List.of(outputRow("token", "token"))));
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
