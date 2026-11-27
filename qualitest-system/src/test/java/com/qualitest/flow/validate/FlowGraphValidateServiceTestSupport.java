package com.qualitest.flow.validate;

import com.qualitest.flow.inspect.ApiHealthIssueCollector;
import com.qualitest.flow.inspect.FlowIssueFinalizeService;
import com.qualitest.flow.inspect.PlatformPluginInspectionEvaluator;
import com.qualitest.flow.inspect.PluginInvokeInspectionCollector;
import com.qualitest.flow.openhttp.OpenHttpRouteRegistry;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 单测构造 {@link FlowGraphValidateService}：附加收集器默认空列表，finalize 原样返回。
 */
public final class FlowGraphValidateServiceTestSupport {

    private FlowGraphValidateServiceTestSupport() {}

    public static FlowGraphValidateService create(
            GraphJsonValidator validator,
            FlowRunReadinessGate readiness,
            OpenHttpRouteRegistry routeRegistry) {
        ApiHealthIssueCollector apiHealth = mock(ApiHealthIssueCollector.class);
        when(apiHealth.collect(any(), any())).thenReturn(List.of());
        PlatformPluginInspectionEvaluator platform = mock(PlatformPluginInspectionEvaluator.class);
        when(platform.evaluate(any(), any(), any())).thenReturn(List.of());
        PluginInvokeInspectionCollector invoke = mock(PluginInvokeInspectionCollector.class);
        when(invoke.collect(any(), any(), any(), any(), any())).thenReturn(List.of());
        FlowIssueFinalizeService finalize = mock(FlowIssueFinalizeService.class);
        when(finalize.finalizeIssues(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        return new FlowGraphValidateService(
                validator,
                readiness,
                routeRegistry != null ? routeRegistry : mock(OpenHttpRouteRegistry.class),
                apiHealth,
                platform,
                invoke,
                finalize);
    }

    public static FlowGraphValidateService create(
            GraphJsonValidator validator, FlowRunReadinessGate readiness) {
        return create(validator, readiness, null);
    }
}
