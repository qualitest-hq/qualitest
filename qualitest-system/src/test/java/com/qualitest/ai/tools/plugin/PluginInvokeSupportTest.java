package com.qualitest.ai.tools.plugin;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolKind;
import com.qualitest.plugin.PluginErrorCode;
import com.qualitest.plugin.PluginException;
import com.qualitest.plugin.invoke.PluginInvokeRequest;
import com.qualitest.plugin.invoke.PluginInvokeResult;
import com.qualitest.plugin.invoke.PluginInvoker;
import com.qualitest.plugin.manifest.PluginAiTool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 测插件贡献 AI 工具执行。
 */
@ExtendWith(MockitoExtension.class)
class PluginInvokeSupportTest {

    @Mock
    private PluginAiToolCatalog catalog;
    @Mock
    private PluginInvoker pluginInvoker;

    @InjectMocks
    private PluginInvokeSupport support;

    @Test
    @DisplayName("未贡献工具返回 error")
    void invoke_unknownTool_returnsError() {
        when(catalog.findContributedTool(1L, "sut_clock_advance")).thenReturn(null);
        FlowDesignToolContext ctx = FlowDesignToolContext.builder().testProjectId(1L).build();
        String json = support.invoke("sut_clock_advance", Map.of("by", "P2D"), ctx);
        assertTrue(JSON.parseObject(json).containsKey("error"));
    }

    @Test
    @DisplayName("成功按贡献 capability 调 Invoker")
    void invoke_success_forwardsInvoker() {
        stubTool();
        when(pluginInvoker.invoke(any())).thenReturn(PluginInvokeResult.builder()
                .ok(true)
                .exitCode(0)
                .stdout("{\"ok\":true}")
                .build());
        FlowDesignToolContext ctx = FlowDesignToolContext.builder()
                .testProjectId(1L)
                .defaultTestProjectEnvId(9L)
                .build();
        ctx.setEditLeaseEndpoint("mcp:sess-1");
        String json = support.invoke("sut_clock_advance", Map.of("by", "P2D"), ctx);
        JSONObject obj = JSON.parseObject(json);
        assertFalse(obj.containsKey("error"));
        assertTrue(obj.getBooleanValue("ok"));
        assertEquals("sut_clock_advance", obj.getString("toolName"));
        assertEquals("qualitest.sut-sys-clock-dial", obj.getString("pluginId"));

        ArgumentCaptor<PluginInvokeRequest> cap = ArgumentCaptor.forClass(PluginInvokeRequest.class);
        verify(pluginInvoker).invoke(cap.capture());
        assertEquals("mcp-invoke", cap.getValue().getSource());
        assertEquals("clock.advance", cap.getValue().getCapability());
        assertEquals("P2D", cap.getValue().getArgs().get("by"));
        assertFalse(cap.getValue().getArgs().containsKey("envId"));
    }

    @Test
    @DisplayName("PluginException 映射为 error JSON")
    void invoke_pluginException_mapsError() {
        stubTool();
        when(pluginInvoker.invoke(any())).thenThrow(
                new PluginException(PluginErrorCode.GATE_DENIED, "破坏性总闸未开"));
        FlowDesignToolContext ctx = FlowDesignToolContext.builder()
                .testProjectId(1L)
                .defaultTestProjectEnvId(9L)
                .build();
        String json = support.invoke("sut_clock_advance", Map.of("by", "P2D", "envId", "9"), ctx);
        assertTrue(JSON.parseObject(json).getString("error").contains("GATE_DENIED"));
    }

    @Test
    @DisplayName("定义带 toolKind=plugin")
    void definition_stampsPluginKind() {
        Map<String, Object> def = PluginAiToolDefinitions.buildFunctionTool(contributed());
        assertEquals(FlowDesignToolKind.PLUGIN.wire(), def.get(FlowDesignToolKind.JSON_FIELD));
        assertEquals("sut_clock_advance",
                ((Map<?, ?>) def.get("function")).get("name"));
    }

    private void stubTool() {
        when(catalog.findContributedTool(eq(1L), eq("sut_clock_advance"))).thenReturn(contributed());
    }

    private static PluginAiToolCatalog.ContributedTool contributed() {
        PluginAiTool tool = PluginAiTool.builder()
                .name("sut_clock_advance")
                .description("拨快")
                .capability("clock.advance")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of("by", Map.of("type", "string")),
                        "required", List.of("by")))
                .build();
        return new PluginAiToolCatalog.ContributedTool(
                "qualitest.sut-sys-clock-dial",
                "拨钟",
                tool);
    }
}
