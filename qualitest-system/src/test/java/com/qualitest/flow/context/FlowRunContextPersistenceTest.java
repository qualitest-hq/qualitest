package com.qualitest.flow.context;

import com.qualitest.flow.session.FlowRunSession;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 测 FlowRunContextPersistence：上下文 Map 往返序列化。
 * 边界：纯内存，无 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowRunContextPersistenceTest
 */
class FlowRunContextPersistenceTest {

    /**
     * 前提：ctx 含 flow / env / cookies / testProjectId。
     * 期望：toMap → fromMap 后字段完整保留，会话非空。
     */
    @Test
    void roundTrip_preservesFlowAndCookies() {
        FlowRunSession session = new FlowRunSession();
        session.getCookies().put("sid", "abc");

        FlowRunContext ctx = FlowRunContext.builder()
                .flow(Map.of("code", 0))
                .env(Map.of("baseUrl", "http://localhost"))
                .runSession(session)
                .testProjectId(42L)
                .build();

        FlowRunContext restored = FlowRunContextPersistence.fromMap(FlowRunContextPersistence.toMap(ctx));
        assertEquals(0, restored.getFlow().get("code"));
        assertEquals("http://localhost", restored.getEnv().get("baseUrl"));
        assertEquals(42L, restored.getTestProjectId());
        assertEquals("abc", restored.getRunSession().getCookies().get("sid"));
        assertFalse(restored.getRunSession().isEmpty());
    }
}
