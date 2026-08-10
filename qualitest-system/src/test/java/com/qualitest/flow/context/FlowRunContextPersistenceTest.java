package com.qualitest.flow.context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 测 FlowRunContextPersistence：上下文 Map 往返序列化。
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
}
