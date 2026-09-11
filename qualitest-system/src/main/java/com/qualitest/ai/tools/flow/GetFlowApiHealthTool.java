package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.ai.tools.ToolResultByteFit;
import com.qualitest.flow.diagnose.HttpNodeApiHealthChecker;
import com.qualitest.flow.diagnose.HttpNodeApiHealthWarning;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.mapper.TestProjectApiMapper;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 检查画布上「项目接口」HTTP 节点的 API 语义健康。
 * <p>
 * 解析当前画布（优先草稿 graphJson，否则按 testFlowId 加载），检查绑定接口是否仍在、
 * 测值参数是否孤儿、抽取路径是否还能对上响应结构。不写库。
 * 返回 healthy、warningCount、warningCodes、warnings（可带残留 apiName/path/method）。
 * 返回前按体检结果形状做字节上限裁剪。
 */
public class GetFlowApiHealthTool implements QualitestTool {

    private final FlowGraphContextResolver graphResolver;
    private final HttpNodeApiHealthChecker healthChecker;
    private final TestProjectApiMapper testProjectApiMapper;

    public GetFlowApiHealthTool(FlowGraphContextResolver graphResolver,
                                HttpNodeApiHealthChecker healthChecker,
                                TestProjectApiMapper testProjectApiMapper) {
        this.graphResolver = graphResolver;
        this.healthChecker = healthChecker;
        this.testProjectApiMapper = testProjectApiMapper;
    }

    @Override
    public String getName() {
        return FlowDesignToolNames.GET_FLOW_API_HEALTH.getId();
    }

    @Override
    public String execute(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        // 解析要体检的画布图
        FlowGraphContextResolver.ResolvedGraph resolved = graphResolver.resolve(arguments, ctx);
        if (!resolved.isOk()) {
            return resolved.errorJson();
        }
        GraphJson graph = resolved.graph();

        // 按 API id 缓存加载结果，同一接口只查一次库
        Map<Long, TestProjectApi> apiCache = new HashMap<>();
        List<HttpNodeApiHealthWarning> warnings = healthChecker.checkGraph(
                graph.toJsonString(),
                id -> apiCache.computeIfAbsent(id, this::loadActiveApi));

        // 节点上残留的接口名/路径，用于 API 已删时仍能提示换绑目标
        Map<String, Map<String, Object>> nodeHints = collectHttpNodeHints(graph);
        JSONArray warningArr = new JSONArray();
        TreeSet<String> codes = new TreeSet<>();
        for (HttpNodeApiHealthWarning w : warnings) {
            if (w == null) {
                continue;
            }
            if (w.code != null && !w.code.isBlank()) {
                codes.add(w.code);
            }
            JSONObject item = new JSONObject();
            item.put("code", w.code);
            item.put("nodeId", w.nodeId);
            item.put("nodeName", w.nodeName);
            if (w.testProjectApiId != null) {
                item.put("testProjectApiId", String.valueOf(w.testProjectApiId));
            }
            item.put("message", w.message);
            if (w.detail != null && !w.detail.isBlank()) {
                item.put("detail", w.detail);
            }
            Map<String, Object> hint = w.nodeId != null ? nodeHints.get(w.nodeId) : null;
            if (hint != null) {
                putIfPresent(item, "apiName", hint.get("apiName"));
                putIfPresent(item, "apiPath", hint.get("apiPath"));
                putIfPresent(item, "httpMethod", hint.get("httpMethod"));
            }
            warningArr.add(item);
        }

        JSONObject result = new JSONObject();
        if (ctx.getTestFlowId() != null) {
            result.put("testFlowId", String.valueOf(ctx.getTestFlowId()));
        }
        result.put("healthy", warnings.isEmpty());
        result.put("warningCount", warnings.size());
        result.put("warningCodes", codes);
        result.put("warnings", warningArr);
        if (warnings.isEmpty()) {
            result.put("message", "当前画布无 API 语义告警");
        } else {
            // 给模型的修复指引：缺接口则换绑，孤儿测值/抽取失效则对照接口详情调整
            result.put("hint", "API_MISSING 时用 search_apis 按 apiName/apiPath 找现行接口，"
                    + "再 submit_update_http_node 换绑 testProjectApiId；"
                    + "ORPHAN_PARAM / EXTRACT_PATH_MISSING 时对照 get_api_details 调整测值或抽取");
        }
        return ToolResultByteFit.fitApiHealth(result, ctx.getMaxToolResultBytes());
    }

    /**
     * 按 id 加载未删除的项目 API；查无或已删除返回 null（体检侧记为 API_MISSING）。
     */
    private TestProjectApi loadActiveApi(Long id) {
        if (id == null) {
            return null;
        }
        TestProjectApi api = testProjectApiMapper.selectTestProjectApiById(id);
        if (api == null || (api.getDelStatus() != null && api.getDelStatus() != 0)) {
            return null;
        }
        return api;
    }

    /**
     * 遍历图中 HTTP 节点，收集 data 里残留的 apiName / apiPath / httpMethod。
     * 接口资产已删时节点上仍可能留有这些字段，用于定位应换绑到哪个接口。
     */
    private static Map<String, Map<String, Object>> collectHttpNodeHints(GraphJson graph) {
        Map<String, Map<String, Object>> hints = new LinkedHashMap<>();
        if (graph == null || graph.getNodes() == null) {
            return hints;
        }
        for (GraphNode node : graph.getNodes()) {
            if (node == null || node.getId() == null || node.getData() == null) {
                continue;
            }
            String type = node.getType();
            if (type != null && !type.isBlank() && !"http".equals(type)) {
                continue;
            }
            Map<String, Object> data = node.getData();
            Map<String, Object> hint = new LinkedHashMap<>();
            Object apiName = data.get("apiName");
            Object apiPath = data.get("apiPath");
            Object httpMethod = data.get("httpMethod");
            if (apiName != null) {
                hint.put("apiName", apiName);
            }
            if (apiPath != null) {
                hint.put("apiPath", apiPath);
            }
            if (httpMethod != null) {
                hint.put("httpMethod", httpMethod);
            }
            if (!hint.isEmpty()) {
                hints.put(node.getId(), hint);
            }
        }
        return hints;
    }

    /** 非空字符串才写入 JSON 字段 */
    private static void putIfPresent(JSONObject item, String key, Object value) {
        if (value == null) {
            return;
        }
        String text = String.valueOf(value);
        if (!text.isBlank() && !"null".equals(text)) {
            item.put(key, text);
        }
    }
}
