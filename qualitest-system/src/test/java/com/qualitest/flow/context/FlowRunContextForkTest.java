package com.qualitest.flow.context;

import com.qualitest.flow.delivery.EntryKind;
import com.qualitest.flow.delivery.RunTrigger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 FlowRunContext.forkForSubflow：
 * 子流继承项目/环境/运行 id、外联与暂停许可，并与父共享 env/session 引用；
 * flow 隔离为空，in 仅灌入种子。
 * 边界：纯内存。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowRunContextForkTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowRunContextForkTest {

    /**
     * 前提：父上下文已绑环境 / 运行 id / 外联与暂停许可。
     * 期望：子上下文继承身份字段，共享 env/session 引用，flow 为空且 in 为种子。
     */
    @Test
    @Order(1)
    @DisplayName("fork：继承环境与运行 id，隔离 flow")
    void fork_inheritsRunIdentity() {
        Map<String, Object> env = new HashMap<>();
        env.put("baseUrl", "http://sut");
        Map<String, Object> session = new HashMap<>();
        session.put("t", "1");
        FlowRunContext parent = FlowRunContext.builder()
                .testProjectId(10L)
                .testProjectEnvId(20L)
                .testFlowRunId(30L)
                .projectAuthConfig("{}")
                .externalHttpPermitted(true)
                .pausePermitted(false)
                .env(env)
                .session(session)
                .subflowDepth(0)
                .build();

        FlowRunContext child = FlowRunContext.forkForSubflow(
                parent,
                Map.of("x", 1),
                "sub-1",
                "entry-a",
                EntryKind.SUBFLOW.getCode(),
                RunTrigger.SUBFLOW.getCode());

        assertEquals(10L, child.getTestProjectId());
        assertEquals(20L, child.getTestProjectEnvId());
        assertEquals(30L, child.getTestFlowRunId());
        assertEquals("{}", child.getProjectAuthConfig());
        assertTrue(child.isExternalHttpPermitted());
        assertEquals(false, child.isPausePermitted());
        assertEquals(1, child.getSubflowDepth());
        assertSame(env, child.getEnv());
        assertSame(session, child.getSession());
        assertEquals(1, child.getIn().get("x"));
        assertTrue(child.getFlow().isEmpty());
        assertSame(parent, child.getParentContext());
        assertEquals("sub-1", child.getParentSubflowNodeId());
    }
}
