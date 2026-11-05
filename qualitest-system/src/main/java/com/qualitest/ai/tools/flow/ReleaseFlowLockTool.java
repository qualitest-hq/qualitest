package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.flow.sync.FlowEditLeaseService;
import lombok.RequiredArgsConstructor;

import java.util.Map;

/**
 * 释放本端持有的测试流写锁。
 * <p>
 * 只释放本端锁；锁不在本端名下时返回成功并说明当前状态，重复调用无副作用。
 */
@RequiredArgsConstructor
public class ReleaseFlowLockTool implements QualitestTool {

    /** 写锁服务 */
    private final FlowEditLeaseService flowEditLeaseService;

    /** MCP 注入的本端稳定端 ID；空时从上下文 {@link FlowDesignToolContext#getEditLeaseEndpoint()} 读取 */
    private final String endpointId;

    @Override
    public String getName() {
        return FlowDesignToolNames.RELEASE_FLOW_LOCK.getId();
    }

    /**
     * 释放指定测试流上本端持有的写锁。
     * 无锁或锁在他端名下时仍返回 ok=true，并说明未释放原因。
     *
     * @param arguments 可选 testFlowId；缺省用上下文中的测试流 id
     * @param ctx       请求上下文，提供 testFlowId 与端标识回退
     * @return JSON：ok、released、message；必要时含 lockHeldBy
     */
    @Override
    public String execute(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        Long testFlowId = FlowDesignToolSupport.longArg(
                arguments != null ? arguments.get("testFlowId") : null);
        if (testFlowId == null && ctx != null) {
            testFlowId = ctx.getTestFlowId();
        }
        if (testFlowId == null) {
            return FlowDesignToolSupport.errorJson("缺少 testFlowId");
        }
        String endpoint = endpointId;
        if ((endpoint == null || endpoint.isBlank()) && ctx != null) {
            endpoint = ctx.getEditLeaseEndpoint();
        }
        if (endpoint == null || endpoint.isBlank()) {
            return FlowDesignToolSupport.errorJson("缺少端标识，无法释放写锁");
        }

        String held = flowEditLeaseService.peekHolder(testFlowId);
        JSONObject result = new JSONObject();
        result.put("testFlowId", String.valueOf(testFlowId));
        // 无锁：幂等成功，未做删除
        if (held == null || held.isBlank()) {
            result.put("ok", true);
            result.put("released", false);
            result.put("message", "当前无写锁");
            return result.toJSONString();
        }
        // 锁在他端：不强制释放，仅告知持锁方
        if (!endpoint.equals(held)) {
            result.put("ok", true);
            result.put("released", false);
            result.put("lockHeldBy", held);
            result.put("message", "写锁不在本端名下，未改动");
            return result.toJSONString();
        }
        // 本端持锁：删除 Redis 键
        flowEditLeaseService.release(testFlowId, endpoint);
        result.put("ok", true);
        result.put("released", true);
        result.put("message", "已释放本端写锁");
        return result.toJSONString();
    }
}
