package com.qualitest.flow.context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 FlowRunContextPersistence：暂停落盘用的上下文 Map 往返。
 * 覆盖变量区、项目 id，以及环境/运行 id、外联与暂停许可、HTTP 快照。
 * 边界：纯内存，无 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowRunContextPersistenceTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowRunContextPersistenceTest {

    /**
     * 前提：ctx 含 flow / env / session / testProjectId。
     * 期望：toMap → fromMap 后字段完整保留。
     */
    @Test
    @Order(1)
    @DisplayName("往返：保留 flow、env、session 与项目 id")
    void roundTrip_preservesScopes() {
        FlowRunContext ctx = FlowRunContext.builder()
                .flow(Map.of("code", 0))
                .env(Map.of("baseUrl", "http://localhost"))
                .session(Map.of("cached", "token-1"))
                .testProjectId(42L)
                .build();

        FlowRunContext restored = FlowRunContextPersistence.fromMap(FlowRunContextPersistence.toMap(ctx));
        assertEquals(0, restored.getFlow().get("code"));
        assertEquals("http://localhost", restored.getEnv().get("baseUrl"));
        assertEquals(42L, restored.getTestProjectId());
        assertEquals("token-1", restored.getSession().get("cached"));
    }

    /**
     * 前提：ctx 含环境 id、运行 id、HTTP 快照、暂停许可。
     * 期望：往返后运行身份与 nodeResponses / lastResponse 仍可读。
     */
    @Test
    @Order(2)
    @DisplayName("往返：保留运行身份与 HTTP 快照")
    void roundTrip_preservesRunIdentityAndHttpSnapshots() {
        FlowRunContext.HttpResponseSnapshot snap = FlowRunContext.HttpResponseSnapshot.builder()
                .status(200)
                .body(Map.of("ok", true))
                .bodyText("{\"ok\":true}")
                .durationMs(12L)
                .responseKind("json")
                .expectedMatch(true)
                .headers(Map.of("Content-Type", "application/json"))
                .build();
        FlowRunContext ctx = FlowRunContext.builder()
                .testProjectId(1L)
                .testProjectEnvId(9001L)
                .testFlowRunId(77L)
                .externalHttpPermitted(false)
                .pausePermitted(true)
                .lastResponse(snap)
                .nodeResponses(Map.of("n1", snap))
                .build();

        FlowRunContext restored = FlowRunContextPersistence.fromMap(FlowRunContextPersistence.toMap(ctx));
        assertEquals(9001L, restored.getTestProjectEnvId());
        assertEquals(77L, restored.getTestFlowRunId());
        assertFalse(restored.isExternalHttpPermitted());
        assertTrue(restored.isPausePermitted());
        assertNotNull(restored.getLastResponse());
        assertEquals(200, restored.getLastResponse().getStatus());
        assertEquals(true, ((Map<?, ?>) restored.getLastResponse().getBody()).get("ok"));
        assertNotNull(restored.getNodeResponse("n1"));
        assertEquals(200, restored.getNodeResponse("n1").getStatus());
    }
}
