package com.qualitest.flow.context;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * FlowRunContext 在暂停落盘与续跑恢复时的序列化 / 反序列化。
 * <p>
 * 写入可恢复字段：env / flow / in / out / asset / session、
 * entryKind / entryNodeId / trigger / returnCount / delivered / parentSubflowNodeId / nodeOuts、
 * externalHttpPermitted、pausePermitted、
 * testProjectId / testProjectEnvId / testFlowRunId、subflowDepth、projectAuthConfig、
 * lastResponse / nodeResponses（HTTP 响应快照；续跑后仍要能读上次响应）。
 * 不写入：deliverySink、parentContext（写出端与父指针由调用方在恢复后重新挂上）。
 */
public final class FlowRunContextPersistence {

    private FlowRunContextPersistence() {
    }

    /**
     * 将可恢复字段拷贝为普通 Map（各子 Map 浅拷贝），供写入 run_execution_state。
     */
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
        map.put("pausePermitted", ctx.isPausePermitted());
        map.put("testProjectId", ctx.getTestProjectId());
        map.put("testProjectEnvId", ctx.getTestProjectEnvId());
        map.put("testFlowRunId", ctx.getTestFlowRunId());
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
        if (ctx.getLastResponse() != null) {
            map.put("lastResponse", snapshotToMap(ctx.getLastResponse()));
        }
        if (ctx.getNodeResponses() != null && !ctx.getNodeResponses().isEmpty()) {
            Map<String, Object> responses = new LinkedHashMap<>();
            for (Map.Entry<String, FlowRunContext.HttpResponseSnapshot> e : ctx.getNodeResponses().entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    responses.put(e.getKey(), snapshotToMap(e.getValue()));
                }
            }
            map.put("nodeResponses", responses);
        }
        return map;
    }

    /**
     * 从 Map 还原上下文；缺键用 builder 默认值。
     * 不恢复 deliverySink / parentContext。
     */
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
        // 暂停许可：缺省由 builder 默认 true；有布尔值则按落盘值恢复
        Object pausePermitted = map.get("pausePermitted");
        if (pausePermitted instanceof Boolean b) {
            builder.pausePermitted(b);
        }
        Object projectId = map.get("testProjectId");
        if (projectId instanceof Number n) {
            builder.testProjectId(n.longValue());
        }
        Object envId = map.get("testProjectEnvId");
        if (envId instanceof Number n) {
            builder.testProjectEnvId(n.longValue());
        }
        Object runId = map.get("testFlowRunId");
        if (runId instanceof Number n) {
            builder.testFlowRunId(n.longValue());
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
        Object lastResponse = map.get("lastResponse");
        if (lastResponse instanceof Map<?, ?> lr) {
            builder.lastResponse(mapToSnapshot((Map<String, Object>) lr));
        }
        Object nodeResponses = map.get("nodeResponses");
        if (nodeResponses instanceof Map<?, ?> raw) {
            Map<String, FlowRunContext.HttpResponseSnapshot> copy = new HashMap<>();
            for (Map.Entry<?, ?> e : raw.entrySet()) {
                if (e.getKey() != null && e.getValue() instanceof Map<?, ?> sm) {
                    copy.put(String.valueOf(e.getKey()), mapToSnapshot((Map<String, Object>) sm));
                }
            }
            builder.nodeResponses(copy);
        }
        return builder.build();
    }

    /** 将单条 HTTP 响应快照写成 Map，供 toMap 嵌套。 */
    private static Map<String, Object> snapshotToMap(FlowRunContext.HttpResponseSnapshot snapshot) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", snapshot.getStatus());
        if (snapshot.getHeaders() != null) {
            m.put("headers", new HashMap<>(snapshot.getHeaders()));
        }
        m.put("body", snapshot.getBody());
        m.put("bodyText", snapshot.getBodyText());
        m.put("durationMs", snapshot.getDurationMs());
        m.put("responseKind", snapshot.getResponseKind());
        m.put("expectedMatch", snapshot.getExpectedMatch());
        return m;
    }

    /** 从 Map 还原单条 HTTP 响应快照。 */
    @SuppressWarnings("unchecked")
    private static FlowRunContext.HttpResponseSnapshot mapToSnapshot(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        Object headers = map.get("headers");
        Map<String, Object> headerCopy = null;
        if (headers instanceof Map) {
            headerCopy = new HashMap<>((Map<String, Object>) headers);
        }
        Object status = map.get("status");
        int statusCode = status instanceof Number n ? n.intValue() : 0;
        Object duration = map.get("durationMs");
        Long durationMs = duration instanceof Number n ? n.longValue() : null;
        Object expected = map.get("expectedMatch");
        Boolean expectedMatch = expected instanceof Boolean b ? b : null;
        Object bodyText = map.get("bodyText");
        Object responseKind = map.get("responseKind");
        return FlowRunContext.HttpResponseSnapshot.builder()
                .status(statusCode)
                .headers(headerCopy)
                .body(map.get("body"))
                .bodyText(bodyText != null ? String.valueOf(bodyText) : null)
                .durationMs(durationMs)
                .responseKind(responseKind != null ? String.valueOf(responseKind) : null)
                .expectedMatch(expectedMatch)
                .build();
    }
}
