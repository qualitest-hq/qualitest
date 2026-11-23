package com.qualitest.flow.context;

import java.util.HashMap;
import java.util.Map;

/**
 * FlowRunContext 暂停/续跑时的序列化与反序列化。
 * <p>
 * 写入：env / flow / in / out / asset / session、
 * entryKind / entryNodeId / trigger / returnCount / delivered / parentSubflowNodeId / nodeOuts、
 * 外联权限、项目 ID、子流深度、projectAuthConfig。
 * 不写入：lastResponse、nodeResponses、deliverySink、parentContext
 * （HTTP 快照须续跑后再请求；写出端与父指针须由调用方重新挂上）。
 */
public final class FlowRunContextPersistence {

    private FlowRunContextPersistence() {
    }

    /** 将可恢复字段拷贝为普通 Map（浅拷贝各子 Map）。 */
    public static Map<String, Object> toMap(FlowRunContext ctx) {
        Map<String, Object> map = new HashMap<>();
        if (ctx == null) {
            return map;
        }
        map.put("env", ctx.getEnv() != null ? new HashMap<>(ctx.getEnv()) : new HashMap<>());
        map.put("flow", ctx.getFlow() != null ? new HashMap<>(ctx.getFlow()) : new HashMap<>());
        map.put("in", ctx.getIn() != null ? new HashMap<>(ctx.getIn()) : new HashMap<>());
        map.put("out", ctx.getOut() != null ? new HashMap<>(ctx.getOut()) : new HashMap<>());
        map.put("asset", ctx.getAsset() != null ? new HashMap<>(ctx.getAsset()) : new HashMap<>());
        map.put("session", ctx.getSession() != null ? new HashMap<>(ctx.getSession()) : new HashMap<>());
        map.put("externalHttpPermitted", ctx.isExternalHttpPermitted());
        map.put("testProjectId", ctx.getTestProjectId());
        map.put("projectAuthConfig", ctx.getProjectAuthConfig());
        map.put("subflowDepth", ctx.getSubflowDepth());
        map.put("entryKind", ctx.getEntryKind());
        map.put("entryNodeId", ctx.getEntryNodeId());
        map.put("trigger", ctx.getTrigger());
        map.put("returnCount", ctx.getReturnCount());
        map.put("delivered", ctx.isDelivered());
        map.put("parentSubflowNodeId", ctx.getParentSubflowNodeId());
        if (ctx.getNodeOuts() != null && !ctx.getNodeOuts().isEmpty()) {
            map.put("nodeOuts", new HashMap<>(ctx.getNodeOuts()));
        }
        return map;
    }

    /** 从 Map 还原上下文；缺键用 builder 默认值；不恢复 sink / parentContext。 */
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
        Object in = map.get("in");
        if (in instanceof Map) {
            builder.in(new HashMap<>((Map<String, Object>) in));
        }
        Object out = map.get("out");
        if (out instanceof Map) {
            builder.out(new HashMap<>((Map<String, Object>) out));
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
        Object entryKind = map.get("entryKind");
        if (entryKind != null) {
            builder.entryKind(String.valueOf(entryKind));
        }
        Object entryNodeId = map.get("entryNodeId");
        if (entryNodeId != null) {
            builder.entryNodeId(String.valueOf(entryNodeId));
        }
        Object trigger = map.get("trigger");
        if (trigger != null) {
            builder.trigger(String.valueOf(trigger));
        }
        Object returnCount = map.get("returnCount");
        if (returnCount instanceof Number n) {
            builder.returnCount(n.intValue());
        }
        Object delivered = map.get("delivered");
        if (delivered instanceof Boolean b) {
            builder.delivered(b);
        }
        Object parentSubflowNodeId = map.get("parentSubflowNodeId");
        if (parentSubflowNodeId != null) {
            builder.parentSubflowNodeId(String.valueOf(parentSubflowNodeId));
        }
        Object nodeOuts = map.get("nodeOuts");
        if (nodeOuts instanceof Map<?, ?> raw) {
            Map<String, Map<String, Object>> copy = new HashMap<>();
            for (Map.Entry<?, ?> e : raw.entrySet()) {
                if (e.getKey() != null && e.getValue() instanceof Map<?, ?> om) {
                    copy.put(String.valueOf(e.getKey()), new HashMap<>((Map<String, Object>) om));
                }
            }
            builder.nodeOuts(copy);
        }
        return builder.build();
    }
}
