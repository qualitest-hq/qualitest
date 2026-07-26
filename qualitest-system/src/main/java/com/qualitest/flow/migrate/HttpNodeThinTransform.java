package com.qualitest.flow.migrate;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.graph.FlowHttpNodeVisitor;
import com.qualitest.flow.http.FlowHttpNodePathSupport;
import com.qualitest.flow.http.HttpNodeRequestValueOverridesSupport;
import com.qualitest.project.domain.TestProjectApi;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 把旧版「厚」HTTP 节点改成「薄」节点的纯函数变换（不访问数据库）。
 * <p>
 * 对项目接口模式的 http 节点：
 * <ol>
 *   <li>从整份 requestConfig 抽出测值写入 requestValueOverrides</li>
 *   <li>删除 requestConfig</li>
 *   <li>删除 apiPath（发出去的路径只跟所绑 API）</li>
 *   <li>必要时补 httpMethod、apiName</li>
 * </ol>
 * 外联 URL 节点不动。
 */
public final class HttpNodeThinTransform {

    private HttpNodeThinTransform() {
    }

    /**
     * 变换一条测试流的 graph_json。
     *
     * @param graphJson   原始图 JSON 字符串
     * @param apiResolver 按接口 ID 查 API，用于补 method / 名称；查不到可返回 null
     * @return 是否变更、变换后 JSON、逐节点变更摘要
     */
    public static TransformResult transformGraphJson(
            String graphJson,
            Function<Long, TestProjectApi> apiResolver) {
        TransformResult result = new TransformResult();
        result.originalJson = graphJson;
        if (graphJson == null || graphJson.isBlank()) {
            result.transformedJson = graphJson;
            return result;
        }
        JSONObject root;
        try {
            root = JSON.parseObject(graphJson);
        } catch (Exception e) {
            result.transformedJson = graphJson;
            result.parseError = e.getMessage();
            return result;
        }
        if (root == null) {
            result.transformedJson = graphJson;
            return result;
        }

        boolean[] changed = {false};
        FlowHttpNodeVisitor.visit(root, (nodeId, node, data) -> {
            NodeChange change = transformProjectHttpNode(data, apiResolver);
            if (change != null) {
                changed[0] = true;
                result.nodeChanges.add(change);
            }
        });
        result.changed = changed[0];
        result.transformedJson = changed[0] ? root.toJSONString() : graphJson;
        return result;
    }

    /**
     * 变换单个 project HTTP 节点 data（就地修改）。external 跳过。
     *
     * @return 有变更时返回摘要，否则 null
     */
    public static NodeChange transformProjectHttpNode(
            JSONObject data,
            Function<Long, TestProjectApi> apiResolver) {
        if (data == null) {
            return null;
        }
        if (!FlowHttpNodeVisitor.isProjectBoundHttp(data)) {
            return null;
        }

        boolean changed = false;
        NodeChange change = new NodeChange();
        change.nodeName = data.getString("name");
        change.testProjectApiId = data.get("testProjectApiId") != null
                ? String.valueOf(data.get("testProjectApiId"))
                : null;

        // 从整份 requestConfig 抽测值；已有 overrides 优先保留
        Object requestConfig = data.get("requestConfig");
        if (requestConfig != null) {
            JSONObject extracted = HttpNodeRequestValueOverridesSupport.extractFromRequestConfig(requestConfig);
            JSONObject existing = data.getJSONObject("requestValueOverrides");
            JSONObject merged = new JSONObject();
            if (existing != null) {
                merged.putAll(existing);
            }
            if (extracted != null && !extracted.isEmpty()) {
                JSONObject existingParams = merged.getJSONObject("paramDefaults");
                JSONObject extractedParams = extracted.getJSONObject("paramDefaults");
                if (extractedParams != null) {
                    if (existingParams == null) {
                        existingParams = new JSONObject();
                        merged.put("paramDefaults", existingParams);
                    }
                    for (String key : extractedParams.keySet()) {
                        if (!existingParams.containsKey(key)) {
                            existingParams.put(key, extractedParams.get(key));
                        }
                    }
                }
                if (extracted.containsKey("bodyExample") && !merged.containsKey("bodyExample")) {
                    merged.put("bodyExample", extracted.get("bodyExample"));
                }
            }
            if (!merged.isEmpty()) {
                data.put("requestValueOverrides", merged);
                change.hasOverrides = true;
            }
            data.remove("requestConfig");
            changed = true;
            change.removedRequestConfig = true;
        }

        // 删除节点路径；必要时补 method / 名称
        TestProjectApi api = resolveApi(data.get("testProjectApiId"), apiResolver);
        if (data.containsKey("apiPath")) {
            FlowHttpNodePathSupport.stripNodeApiPath(data);
            changed = true;
            change.clearedApiPath = true;
        }
        if (api != null) {
            if (data.getString("httpMethod") == null || data.getString("httpMethod").isBlank()) {
                try {
                    if (api.getRequestConfig() != null) {
                        JSONObject rc = JSON.parseObject(api.getRequestConfig());
                        if (rc != null && rc.getString("method") != null) {
                            data.put("httpMethod", rc.getString("method").trim().toUpperCase());
                            changed = true;
                        }
                    }
                } catch (Exception ignored) {
                    // 解析失败则不补 method
                }
            }
            if (data.getString("apiName") == null || data.getString("apiName").isBlank()) {
                if (api.getApiName() != null) {
                    data.put("apiName", api.getApiName());
                    changed = true;
                }
            }
        }

        return changed ? change : null;
    }

    /** 按节点上的 testProjectApiId 查 API；无效 ID 返回 null。 */
    private static TestProjectApi resolveApi(Object rawId, Function<Long, TestProjectApi> apiResolver) {
        Long id = FlowHttpNodeVisitor.parseTestProjectApiId(rawId);
        if (id == null || apiResolver == null) {
            return null;
        }
        try {
            return apiResolver.apply(id);
        } catch (Exception e) {
            return null;
        }
    }

    /** 单条 graph_json 的变换结果。 */
    public static class TransformResult {
        /** 是否有任意节点被改过 */
        public boolean changed;
        public String originalJson;
        public String transformedJson;
        /** JSON 解析失败时的错误信息 */
        public String parseError;
        public final List<NodeChange> nodeChanges = new ArrayList<>();
    }

    /** 单个 HTTP 节点的变更摘要（用于 dry-run 报告）。 */
    public static class NodeChange {
        public String nodeName;
        public String testProjectApiId;
        /** 是否删除了整份 requestConfig */
        public boolean removedRequestConfig;
        /** 是否删除了节点 apiPath */
        public boolean clearedApiPath;
        /** 变换后是否带有 requestValueOverrides */
        public boolean hasOverrides;
    }
}
