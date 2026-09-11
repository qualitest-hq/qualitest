package com.qualitest.ai.tools;

import com.qualitest.ai.scenario.flow.FlowDesignPatchNormalizer;
import com.qualitest.ai.scenario.flow.model.DesignValidationResult;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
import com.qualitest.flow.model.GraphNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 FlowDesignSubmitCapture：成功累积、失败不覆盖、同 unitId 覆盖。
 * 边界：纯内存，无 Spring。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowDesignSubmitCaptureTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowDesignSubmitCaptureTest {

    @Test
    @Order(1)
    @DisplayName("失败不覆盖已接受单元")
    void record_failure_doesNotOverwriteAccepted() {
        FlowDesignSubmitCapture capture = new FlowDesignSubmitCapture();
        FlowDesignPatch ok = nodePatch("n1");
        capture.record(new FlowDesignPatchNormalizer.NormalizeResult(ok, okValidation()));
        assertTrue(capture.hasAccepted());

        FlowDesignPatch bad = nodePatch("n2");
        capture.record(new FlowDesignPatchNormalizer.NormalizeResult(bad, failValidation()));
        assertEquals(1, capture.getNormalizedPatch().getAddNodes().size());
        assertEquals("n1", capture.getNormalizedPatch().getAddNodes().get(0).getId());
        assertFalse(capture.getValidation().isOk());
    }

    @Test
    @Order(2)
    @DisplayName("成功累积多个不同单元")
    void record_success_accumulatesUnits() {
        FlowDesignSubmitCapture capture = new FlowDesignSubmitCapture();
        capture.record(new FlowDesignPatchNormalizer.NormalizeResult(nodePatch("n1"), okValidation()));
        capture.record(new FlowDesignPatchNormalizer.NormalizeResult(nodePatch("n2"), okValidation()));
        assertEquals(2, capture.getNormalizedPatch().getAddNodes().size());
    }

    @Test
    @Order(3)
    @DisplayName("同 unitId 再次成功则覆盖")
    void record_sameUnitId_replacesSlice() {
        FlowDesignSubmitCapture capture = new FlowDesignSubmitCapture();
        FlowDesignPatch first = nodePatch("n1");
        first.getAddNodes().get(0).getData().put("name", "旧");
        capture.record(new FlowDesignPatchNormalizer.NormalizeResult(first, okValidation()));

        FlowDesignPatch second = nodePatch("n1");
        second.getAddNodes().get(0).getData().put("name", "新");
        capture.record(new FlowDesignPatchNormalizer.NormalizeResult(second, okValidation()));

        assertEquals(1, capture.getNormalizedPatch().getAddNodes().size());
        assertEquals("新", capture.getNormalizedPatch().getAddNodes().get(0).getData().get("name"));
    }

    private static FlowDesignPatch nodePatch(String id) {
        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setAddNodes(new ArrayList<>(List.of(
                GraphNode.builder().id(id).type("http").data(new HashMap<>()).build())));
        return patch;
    }

    private static DesignValidationResult okValidation() {
        return DesignValidationResult.builder().ok(true).errors(List.of()).warnings(List.of()).build();
    }

    private static DesignValidationResult failValidation() {
        return DesignValidationResult.builder().ok(false).errors(List.of("x")).warnings(List.of()).build();
    }
}
