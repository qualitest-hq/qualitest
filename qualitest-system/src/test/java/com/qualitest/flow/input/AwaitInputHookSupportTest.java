package com.qualitest.flow.input;

import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.plugin.hook.PluginHookRunner;
import com.qualitest.project.service.ITestProjectEnvService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 测 AwaitInputHookSupport：环境未启用 onAwaitInput 挂点时不调度、返回 empty，
 * 由 Input 节点再按 pausePermitted 决定暂停或失败。
 * 边界：Mock PluginHookRunner / ITestProjectEnvService。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=AwaitInputHookSupportTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AwaitInputHookSupportTest {

    /**
     * 前提：环境未启用任何 onAwaitInput 插件。
     * 期望：返回 empty，且不查环境、不 invoke。
     */
    @Test
    @Order(1)
    @DisplayName("无挂点：直接 empty")
    void tryAutoFill_noHook_returnsEmpty() {
        PluginHookRunner hookRunner = mock(PluginHookRunner.class);
        ITestProjectEnvService envService = mock(ITestProjectEnvService.class);
        when(hookRunner.hasEnabledHook(anyLong(), eq(AwaitInputHookSupport.HOOK_ON_AWAIT_INPUT)))
                .thenReturn(false);

        AwaitInputHookSupport support = new AwaitInputHookSupport(hookRunner, envService);
        FlowRunContext ctx = FlowRunContext.builder()
                .testProjectEnvId(9L)
                .testFlowRunId(1L)
                .build();
        GraphNode node = GraphNode.builder().id("in1").type("input").data(Map.of()).build();

        Optional<Map<String, Object>> result = support.tryAutoFill(ctx, node);

        assertTrue(result.isEmpty());
        verify(envService, never()).selectTestProjectEnvById(anyLong());
    }
}
