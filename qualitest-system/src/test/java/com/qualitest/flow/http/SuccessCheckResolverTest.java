package com.qualitest.flow.http;

import com.qualitest.project.domain.TestProjectApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 SuccessCheckResolver：节点 successValues 优先于 API / Profile 约定。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SuccessCheckResolverTest {

    private static final String PROJECT_200 =
            "{\"codePath\":\"code\",\"successValues\":[200],\"messagePath\":\"msg\",\"dataPath\":\"data\"}";

    @Test
    @Order(1)
    @DisplayName("节点 successValues=[1] 优先于 Profile [200]")
    void nodeSuccessValues_overrideProject() {
        Map<String, Object> data = Map.of(
                "successCheck", Map.of("mode", "inherit", "successValues", List.of(1)));
        SuccessCheckResolver.Resolved r = SuccessCheckResolver.resolve(
                data, "project", null, PROJECT_200);

        assertTrue(r.shouldApply());
        assertTrue(r.isSuccess(1));
        assertFalse(r.isSuccess(200));
        assertEquals(List.of(1), r.successValuesForReport());
    }

    @Test
    @Order(2)
    @DisplayName("无节点值时接口 biz_code 覆盖 Profile")
    void apiBizCode_overridesProject() {
        TestProjectApi api = TestProjectApi.builder()
                .bizCodeConfig("{\"successValues\":[0]}")
                .build();
        SuccessCheckResolver.Resolved r = SuccessCheckResolver.resolve(
                Map.of("successCheck", Map.of("mode", "inherit")),
                "project", api, PROJECT_200);

        assertTrue(r.isSuccess(0));
        assertFalse(r.isSuccess(200));
    }

    @Test
    @Order(3)
    @DisplayName("节点值优先于接口 biz_code")
    void node_overridesApiBizCode() {
        TestProjectApi api = TestProjectApi.builder()
                .bizCodeConfig("{\"successValues\":[0]}")
                .build();
        Map<String, Object> data = Map.of(
                "successCheck", Map.of("mode", "inherit", "successValues", List.of(1)));
        SuccessCheckResolver.Resolved r = SuccessCheckResolver.resolve(
                data, "project", api, PROJECT_200);

        assertTrue(r.isSuccess(1));
        assertFalse(r.isSuccess(0));
    }

    @Test
    @Order(4)
    @DisplayName("mode=off 不应用成功值")
    void modeOff_skips() {
        Map<String, Object> data = Map.of(
                "successCheck", Map.of("mode", "off", "successValues", List.of(1)));
        SuccessCheckResolver.Resolved r = SuccessCheckResolver.resolve(
                data, "project", null, PROJECT_200);

        assertFalse(r.shouldApply());
    }
}
