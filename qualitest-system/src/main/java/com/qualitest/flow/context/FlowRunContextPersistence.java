package com.qualitest.flow.context;

import java.util.HashMap;
import java.util.Map;

/**
 * FlowRunContext 暂停/续跑时的序列化与反序列化。
 * <p>
 * 快照包含 env / flow / asset / session、外联权限、项目 ID、子流深度，
 * 以及项目多端配置（projectAuthConfig），保证续跑后 HTTP 仍能按 Profile 约定校验业务码并补鉴权头。
 */
public final class FlowRunContextPersistence {

    private FlowRunContextPersistence() {
    }

    public static Map<String, Object> toMap(FlowRunContext ctx) {
        Map<String, Object> map = new HashMap<>();
        if (ctx == null) {
            return map;
        }
        map.put("env", ctx.getEnv() != null ? new HashMap<>(ctx.getEnv()) : new HashMap<>());
        map.put("flow", ctx.getFlow() != null ? new HashMap<>(ctx.getFlow()) : new HashMap<>());
        map.put("asset", ctx.getAsset() != null ? new HashMap<>(ctx.getAsset()) : new HashMap<>());
        map.put("session", ctx.getSession() != null ? new HashMap<>(ctx.getSession()) : new HashMap<>());
        map.put("externalHttpPermitted", ctx.isExternalHttpPermitted());
        map.put("testProjectId", ctx.getTestProjectId());
        map.put("projectAuthConfig", ctx.getProjectAuthConfig());
        map.put("subflowDepth", ctx.getSubflowDepth());
        return map;
    }

    @SuppressWarnings("unchecked")
    public static FlowRunContext fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return FlowRunContext.builder().build();
        }
        FlowRunContext.FlowRunContextBuilder builder = FlowRunContext.builder();
        Object env = map.get("env");
        if (env instanceof Map) {
            builder.env(new HashMap<>((Map<String, Object>) env));
        }
        Object flow = map.get("flow");
        if (flow instanceof Map) {
            builder.flow(new HashMap<>((Map<String, Object>) flow));
        }
        Object asset = map.get("asset");
        if (asset instanceof Map) {
            builder.asset(new HashMap<>((Map<String, Object>) asset));
        }
        Object session = map.get("session");
        if (session instanceof Map) {
            builder.session(new HashMap<>((Map<String, Object>) session));
        }
        Object permitted = map.get("externalHttpPermitted");
        if (permitted instanceof Boolean b) {
            builder.externalHttpPermitted(b);
        }
        Object projectId = map.get("testProjectId");
        if (projectId instanceof Number n) {
            builder.testProjectId(n.longValue());
        }
        Object projectAuthConfig = map.get("projectAuthConfig");
        if (projectAuthConfig != null) {
            builder.projectAuthConfig(String.valueOf(projectAuthConfig));
        }
        Object depth = map.get("subflowDepth");
        if (depth instanceof Number n) {
            builder.subflowDepth(n.intValue());
        }
        return builder.build();
    }
}
