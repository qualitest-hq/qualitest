package com.qualitest.flow.context;

import com.qualitest.flow.session.FlowRunSession;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FlowRunContextPersistenceTest {

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
