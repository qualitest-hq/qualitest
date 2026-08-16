package com.qualitest.ai.scenario.flow;

import cn.hutool.core.util.IdUtil;
import com.qualitest.ai.scenario.flow.model.FlowDesignScenarioPatch;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
import com.qualitest.ai.scenario.flow.model.DesignValidationResult;
import com.qualitest.ai.tools.FlowDesignIds;
import com.qualitest.api.util.ManagedAuthHeaderApplier;
import com.qualitest.flow.graph.GraphLookupUtils;
import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.model.GraphNodePosition;
import com.qualitest.flow.model.GraphRunScenario;
import com.qualitest.flow.http.FlowHttpCallMode;
import com.qualitest.flow.http.FlowHttpRequestBuilder;
import com.qualitest.flow.validate.AssertPathDesignGate;
import com.qualitest.flow.validate.AuthTokenPresenceGate;
import com.qualitest.flow.validate.LoginExtractPresenceGate;
import com.qualitest.flow.validate.GraphJsonValidator;
import com.qualitest.flow.validate.GraphValidationResult;
import com.qualitest.project.domain.TestProject;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.mapper.TestProjectApiMapper;
import com.qualitest.project.mapper.TestProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * AI 产出流程补丁的服务端规范化器。
 * <p>
 * 在 AI submit 与 Web Diff 合并前依次执行：
 * <ol>
 *   <li>为 addNodes/addEdges 补雪花 id 与默认 position</li>
 *   <li>校验 HTTP(project) 节点 testProjectApiId 属于当前项目；external 跳过 API 归属校验</li>
 *   <li>按接口鉴权标签与项目鉴权配置补 Authorization 等托管头（profileManaged）</li>
 *   <li>补 data.summary（project / external / subflow 各自格式）</li>
 *   <li>规范化 scenarioPatch（场景 id、flowSeed 键名等）</li>
 *   <li>预合并到基准图副本，跑图结构校验，得到 errors/warnings</li>
 *   <li>用上游接口响应示例试算 assert/condition 的 http.body 左值；未命中记入 errors 回传模型</li>
 *   <li>检查需登录节点所需的 flow.token / flow.adminToken 等是否已有来源；缺则记入 errors，本次造流不可进入 Staging</li>
 * </ol>
 * 不写库；用户在前端 Diff 确认后才持久化 graph_json。
 */
@Component
@RequiredArgsConstructor
public class FlowDesignPatchNormalizer {

    private static final double GRID_X = 380.0;
    private static final double DEFAULT_X = 40.0;
    private static final double DEFAULT_Y = 80.0;
    /** 与前端 flowConfig NODE_W / NODE_MIN_H 对齐，用于 AABB 避让 */
    private static final double NODE_W = 300.0;
    private static final double NODE_MIN_H = 108.0;
    /** 单次右移 / 下移行尝试上限；用尽后兜底落点，避免死循环 */
    private static final int MAX_SHIFT = 40;
    private static final double ROW_STEP = NODE_MIN_H + 40.0;

    private final TestProjectApiMapper testProjectApiMapper;
    private final TestProjectMapper testProjectMapper;
    private final GraphJsonValidator graphJsonValidator;
    private final FlowDesignPatchMerger patchMerger;

    /**
     * 全量规范化并预合并校验：合并 patch 后跑图结构校验与断言路径门禁，错误回传模型以便自我修正。
     */
    public NormalizeResult normalize(FlowDesignPatch patch, GraphJson baseGraph, Long testProjectId) {
        List<String> normWarnings = new ArrayList<>();
        patch = initAndNormalizePatch(patch, baseGraph, testProjectId, normWarnings);

        GraphJson merged = patchMerger.mergeAll(baseGraph, patch, normWarnings);
        GraphValidationResult validation = graphJsonValidator.validate(merged);
        List<String> warnings = new ArrayList<>(normWarnings);
        warnings.addAll(validation.getWarnings());

        List<String> errors = new ArrayList<>(validation.getErrors());
        // 断言路径：结构错误进 errors；schema 缺字段进 warnings（不阻断 Staging）
        AssertPathDesignGate.AssertPathGateResult assertPath =
                AssertPathDesignGate.validate(merged,
                        testProjectApiMapper == null ? id -> null : testProjectApiMapper::selectTestProjectApiById);
        errors.addAll(assertPath.errors());
        warnings.addAll(assertPath.warnings());
        String projectAuthJson = loadProjectAuthConfig(testProjectId);
        Function<Long, TestProjectApi> apiResolver = testProjectApiMapper == null
                ? id -> null
                : testProjectApiMapper::selectTestProjectApiById;
        errors.addAll(AuthTokenPresenceGate.validate(merged, projectAuthJson, apiResolver));
        errors.addAll(LoginExtractPresenceGate.validate(merged, projectAuthJson, apiResolver));

        DesignValidationResult planValidation = DesignValidationResult.builder()
                .ok(errors.isEmpty())
                .errors(errors)
                .warnings(warnings)
                .build();

        return new NormalizeResult(patch, planValidation);
    }

    /**
     * 规范化 patch 字段（id、position、API 绑定、summary 等），不执行预合并校验。
     * 供部分勾选预览等在合并前单独调用规范化步骤的场景使用。
     */
    public FlowDesignPatch preparePatch(FlowDesignPatch patch, GraphJson baseGraph, Long testProjectId, List<String> warnings) {
        return initAndNormalizePatch(patch, baseGraph, testProjectId, warnings);
    }

    /**
     * 检查图中需登录的 project HTTP 是否已有对应端 flow 变量来源（如 token、adminToken）。
     * 缺来源时返回错误文案列表；项目未配鉴权或无需登录时返回空列表。
     */
    public List<String> collectAuthTokenPresenceErrors(GraphJson graph, Long testProjectId) {
        String projectAuthJson = loadProjectAuthConfig(testProjectId);
        return AuthTokenPresenceGate.validate(
                graph,
                projectAuthJson,
                testProjectApiMapper == null ? id -> null : testProjectApiMapper::selectTestProjectApiById);
    }

    /**
     * 检查图中登录/注册类 project HTTP 是否已配置期望的 flow 变量 extract。
     * 缺 extract 时返回错误文案；非登录口或已配置时返回空列表。
     */
    public List<String> collectLoginExtractPresenceErrors(GraphJson graph, Long testProjectId) {
        String projectAuthJson = loadProjectAuthConfig(testProjectId);
        return LoginExtractPresenceGate.validate(
                graph,
                projectAuthJson,
                testProjectApiMapper == null ? id -> null : testProjectApiMapper::selectTestProjectApiById);
    }

    /**
     * normalize 与 preparePatch 共用的 patch 初始化与规范化步骤。
     * 依次：suggestedDeletes、雪花 id / position、scenarioPatch、
     * HTTP API 绑定校验、按 type 规范化节点 data、节点 summary。
     */
    private FlowDesignPatch initAndNormalizePatch(
            FlowDesignPatch patch,
            GraphJson baseGraph,
            Long testProjectId,
            List<String> warnings) {
        if (patch == null) {
            patch = new FlowDesignPatch();
        }
        if (patch.getSuggestedDeletes() == null) {
            patch.setSuggestedDeletes(new FlowDesignPatch.SuggestedDeletes());
        }
        normalizeIds(patch, baseGraph);
        normalizeScenarioPatch(patch);
        validateApiBindings(patch, baseGraph, testProjectId, warnings);
        normalizeTypedNodeData(patch, baseGraph);
        fillSummaries(patch);
        return patch;
    }

    /**
     * 按节点 type 规范化 data（无 API 上下文）。
     * HTTP 已在 {@link #validateApiBindings} 中走 API-aware 规范化，此处跳过。
     * updateNodes 缺 type 时从基准图解析，避免 containsKey 启发式误伤。
     */
    private static void normalizeTypedNodeData(FlowDesignPatch patch, GraphJson baseGraph) {
        if (patch.getAddNodes() != null) {
            for (GraphNode node : patch.getAddNodes()) {
                if (node == null || node.getData() == null) {
                    continue;
                }
                String type = trimType(node.getType());
                if (type.isEmpty() || "http".equalsIgnoreCase(type)) {
                    continue;
                }
                FlowDesignNodeDataNormalizer.normalize(type, node.getData());
            }
        }
        if (patch.getUpdateNodes() != null) {
            for (GraphNode update : patch.getUpdateNodes()) {
                if (update == null || update.getData() == null) {
                    continue;
                }
                String type = resolveEffectiveNodeType(baseGraph, update);
                if (type.isEmpty() || "http".equalsIgnoreCase(type)) {
                    continue;
                }
                FlowDesignNodeDataNormalizer.normalize(type, update.getData());
            }
        }
    }

    /** update 缺 type 时回落基准图节点 type。 */
    private static String resolveEffectiveNodeType(GraphJson baseGraph, GraphNode update) {
        if (update.getType() != null && !update.getType().isBlank()) {
            return update.getType().trim();
        }
        if (update.getId() == null || baseGraph == null || baseGraph.getNodes() == null) {
            return "";
        }
        GraphNode existing = GraphLookupUtils.findNode(baseGraph.getNodes(), update.getId());
        return existing != null && existing.getType() != null ? existing.getType().trim() : "";
    }

    private static String trimType(String type) {
        return type != null ? type.trim() : "";
    }

    /**
     * 规范化新增节点与连线的 id。
     * <p>
     * 缺 id 或非数字 id 时生成雪花 id；缺 position 或与底图/同批已放节点 AABB 重叠时按网格错开。
     * 节点 id 被替换时，同步将 addEdges 的 source/target 映射到新 id，避免连线端点悬空。
     */
    private void normalizeIds(FlowDesignPatch patch, GraphJson baseGraph) {
        Map<String, String> idRemap = new HashMap<>();
        List<GraphNodePosition> obstacles = collectBaseObstacles(baseGraph);
        if (patch.getAddNodes() != null) {
            int index = 0;
            for (GraphNode node : patch.getAddNodes()) {
                String oldId = node.getId();
                if (node.getId() == null || node.getId().isBlank() || !isValidId(node.getId())) {
                    String newId = String.valueOf(IdUtil.getSnowflakeNextId());
                    if (oldId != null && !oldId.isBlank()) {
                        idRemap.put(oldId, newId);
                    }
                    node.setId(newId);
                }
                GraphNodePosition preferred = node.getPosition() != null
                        ? node.getPosition()
                        : defaultAddPosition(baseGraph, index);
                GraphNodePosition resolved = resolveAddPosition(obstacles, preferred);
                node.setPosition(resolved);
                obstacles.add(resolved);
                index++;
            }
        }
        if (patch.getAddEdges() != null) {
            for (GraphEdge edge : patch.getAddEdges()) {
                if (edge.getId() == null || edge.getId().isBlank() || !isValidId(edge.getId())) {
                    edge.setId(String.valueOf(IdUtil.getSnowflakeNextId()));
                }
                // 节点 id 已替换时，将连线端点同步到新 id
                if (edge.getSource() != null && idRemap.containsKey(edge.getSource())) {
                    edge.setSource(idRemap.get(edge.getSource()));
                }
                if (edge.getTarget() != null && idRemap.containsKey(edge.getTarget())) {
                    edge.setTarget(idRemap.get(edge.getTarget()));
                }
            }
        }
    }

    /** 收集基准图全部节点 position，作为避让障碍起点。 */
    private static List<GraphNodePosition> collectBaseObstacles(GraphJson baseGraph) {
        List<GraphNodePosition> obstacles = new ArrayList<>();
        if (baseGraph == null || baseGraph.getNodes() == null) {
            return obstacles;
        }
        for (GraphNode n : baseGraph.getNodes()) {
            if (n != null && n.getPosition() != null) {
                obstacles.add(n.getPosition());
            }
        }
        return obstacles;
    }

    /**
     * 相对障碍物为候选点找空位：缺坐标已在调用方补 preferred；有坐标但重叠时同样错开。
     * 优先沿 x 网格右移，用尽后 y 下移再继续。
     */
    static GraphNodePosition resolveAddPosition(List<GraphNodePosition> obstacles, GraphNodePosition preferred) {
        double baseX = preferred != null ? preferred.getX() : DEFAULT_X;
        double baseY = preferred != null ? preferred.getY() : DEFAULT_Y;
        for (int row = 0; row < MAX_SHIFT; row++) {
            double y = baseY + row * ROW_STEP;
            for (int i = 0; i < MAX_SHIFT; i++) {
                double x = baseX + i * GRID_X;
                if (!overlapsAny(x, y, obstacles)) {
                    return GraphNodePosition.builder().x(x).y(y).build();
                }
            }
        }
        // 与前端一致：兜底落在扫过范围的右下角外侧
        return GraphNodePosition.builder()
                .x(baseX + MAX_SHIFT * GRID_X)
                .y(baseY + MAX_SHIFT * ROW_STEP)
                .build();
    }

    private static boolean overlapsAny(double x, double y, List<GraphNodePosition> obstacles) {
        if (obstacles == null || obstacles.isEmpty()) {
            return false;
        }
        for (GraphNodePosition o : obstacles) {
            if (o == null) {
                continue;
            }
            if (boxesOverlap(x, y, o.getX(), o.getY())) {
                return true;
            }
        }
        return false;
    }

    private static boolean boxesOverlap(double ax, double ay, double bx, double by) {
        return !(ax + NODE_W <= bx
                || bx + NODE_W <= ax
                || ay + NODE_MIN_H <= by
                || by + NODE_MIN_H <= ay);
    }

    /**
     * 规范化 scenarioPatch：为新增场景补雪花 id、空 flowSeed 与占位名称。
     */
    private void normalizeScenarioPatch(FlowDesignPatch patch) {
        FlowDesignScenarioPatch scenarioPatch = patch.getScenarioPatch();
        if (scenarioPatch == null || !FlowDesignScenarioPatch.hasChanges(scenarioPatch)) {
            return;
        }
        if (scenarioPatch.getAddScenarios() != null) {
            int index = 1;
            for (GraphRunScenario scenario : scenarioPatch.getAddScenarios()) {
                if (scenario == null) {
                    continue;
                }
                if (scenario.getId() == null || scenario.getId().isBlank() || !isValidId(scenario.getId())) {
                    scenario.setId(String.valueOf(IdUtil.getSnowflakeNextId()));
                }
                if (scenario.getFlowSeed() == null) {
                    scenario.setFlowSeed(new HashMap<>());
                }
                if (scenario.getName() == null || scenario.getName().isBlank()) {
                    scenario.setName("新场景 " + index);
                }
                if (scenario.getTestProjectEnvId() == null) {
                    scenario.setTestProjectEnvId("");
                }
                if (scenario.getRemark() == null) {
                    scenario.setRemark("");
                }
                index++;
            }
        }
    }

    /**
     * 为缺省 position 的新增节点推算坐标。
     * 空图首节点 (40,80)；否则取基准图末节点 x+380，同批后续节点 y 递增 40。
     */
    private static GraphNodePosition defaultAddPosition(GraphJson baseGraph, int indexInPatch) {
        double baseX = DEFAULT_X;
        double baseY = DEFAULT_Y;
        if (baseGraph != null && baseGraph.getNodes() != null && !baseGraph.getNodes().isEmpty()) {
            GraphNode last = baseGraph.getNodes().get(baseGraph.getNodes().size() - 1);
            if (last.getPosition() != null) {
                baseX = last.getPosition().getX() + GRID_X;
                baseY = last.getPosition().getY();
            }
        }
        return GraphNodePosition.builder()
                .x(baseX)
                .y(baseY + indexInPatch * 40.0)
                .build();
    }

    /** 画布节点/边 id 须为可解析的数字雪花 id */
    private static boolean isValidId(String id) {
        return FlowDesignIds.parseLongId(id) != null;
    }

    /** 校验 patch 中 HTTP 节点（新增或更新）绑定的 API 是否属于当前项目，并按项目鉴权补托管头 */
    private void validateApiBindings(FlowDesignPatch patch, GraphJson baseGraph, Long testProjectId, List<String> warnings) {
        String projectAuthJson = loadProjectAuthConfig(testProjectId);
        if (patch.getAddNodes() != null) {
            for (GraphNode node : patch.getAddNodes()) {
                validateHttpNodeApiBinding(node, testProjectId, projectAuthJson, warnings);
            }
        }
        if (patch.getUpdateNodes() != null) {
            for (GraphNode update : patch.getUpdateNodes()) {
                GraphNode effective = resolveUpdateNodeForApiValidation(baseGraph, update);
                validateHttpNodeApiBinding(effective, testProjectId, projectAuthJson, warnings);
                syncApiBindingFieldsToUpdate(update, effective);
            }
        }
    }

    /**
     * 读取当前测试项目的鉴权配置 JSON。
     * 无项目 id、无 mapper 或项目不存在时返回 null。
     */
    private String loadProjectAuthConfig(Long testProjectId) {
        if (testProjectId == null || testProjectMapper == null) {
            return null;
        }
        TestProject project = testProjectMapper.selectTestProjectById(testProjectId);
        return project != null ? project.getAuthConfig() : null;
    }

    /**
     * updateNodes 的 patch 通常只含 data 增量；校验 API 绑定前与基准图节点合并 type/callMode 等上下文。
     */
    private static GraphNode resolveUpdateNodeForApiValidation(GraphJson baseGraph, GraphNode update) {
        if (update == null || update.getId() == null) {
            return update;
        }
        GraphNode existing = baseGraph != null && baseGraph.getNodes() != null
                ? GraphLookupUtils.findNode(baseGraph.getNodes(), update.getId())
                : null;
        if (existing == null) {
            return update;
        }
        String type = update.getType() != null && !update.getType().isBlank()
                ? update.getType()
                : existing.getType();
        Map<String, Object> mergedData = new HashMap<>();
        if (existing.getData() != null) {
            mergedData.putAll(existing.getData());
        }
        if (update.getData() != null) {
            mergedData.putAll(update.getData());
        }
        return GraphNode.builder()
                .id(update.getId())
                .type(type)
                .data(mergedData)
                .build();
    }

    /** 将 API 校验结果（置空/补全/鉴权头）写回 updateNodes 的增量 data */
    private static void syncApiBindingFieldsToUpdate(GraphNode update, GraphNode effective) {
        if (update == null || effective == null || effective.getData() == null) {
            return;
        }
        Map<String, Object> updateData = update.getData() != null
                ? new HashMap<>(update.getData())
                : new HashMap<>();
        Map<String, Object> effectiveData = effective.getData();
        if (updateData.containsKey("testProjectApiId") || effectiveData.containsKey("testProjectApiId")) {
            updateData.put("testProjectApiId", effectiveData.get("testProjectApiId"));
        }
        if (effectiveData.get("apiName") != null) {
            updateData.put("apiName", effectiveData.get("apiName"));
        }
        if (effectiveData.get("apiPath") != null) {
            updateData.put("apiPath", effectiveData.get("apiPath"));
        }
        if (effectiveData.containsKey("headers")) {
            updateData.put("headers", effectiveData.get("headers"));
        }
        update.setData(updateData);
    }

    /** 节点 data 含 testProjectApiId 时校验归属；external 模式校验 externalUrl 并跳过 API 绑定 */
    private void validateHttpNodeApiBinding(
            GraphNode node, Long testProjectId, String projectAuthJson, List<String> warnings) {
        if (node == null) {
            return;
        }
        Map<String, Object> data = node.getData();
        if (data == null) {
            return;
        }
        if (node.getType() != null && !node.getType().isBlank() && !"http".equals(node.getType())) {
            return;
        }
        String callMode = data.get("callMode") != null ? String.valueOf(data.get("callMode")).trim() : "";
        if (FlowHttpCallMode.isExternal(callMode)) {
            Object externalUrl = data.get("externalUrl");
            if (externalUrl == null || String.valueOf(externalUrl).isBlank()) {
                String nodeLabel = String.valueOf(data.getOrDefault("name", node.getId() != null ? node.getId() : "HTTP 节点"));
                warnings.add("HTTP 节点「" + nodeLabel + "」外联模式缺少 externalUrl");
            }
            FlowDesignHttpNodeNormalizer.normalize(data, null, projectAuthJson);
            return;
        }

        TestProjectApi boundApi = null;
        if (FlowHttpCallMode.isProject(callMode) || data.get("testProjectApiId") != null) {
            Object rawId = data.get("testProjectApiId");
            String nodeLabel = String.valueOf(data.getOrDefault("name", node.getId() != null ? node.getId() : "HTTP 节点"));
            if (rawId != null && !String.valueOf(rawId).isBlank()) {
                Long apiId = FlowDesignIds.parseLong(rawId);
                if (apiId == null) {
                    data.put("testProjectApiId", null);
                    warnings.add("HTTP 节点「" + nodeLabel + "」testProjectApiId 无效，已置空");
                } else {
                    TestProjectApi api = testProjectApiMapper != null
                            ? testProjectApiMapper.selectTestProjectApiById(apiId)
                            : null;
                    if (api == null || api.getTestProjectId() == null || !api.getTestProjectId().equals(testProjectId)) {
                        data.put("testProjectApiId", null);
                        warnings.add("HTTP 节点「" + nodeLabel + "」API 不属于当前项目，已置空");
                    } else {
                        data.put("testProjectApiId", String.valueOf(apiId));
                        if (data.get("apiName") == null) {
                            data.put("apiName", api.getApiName());
                        }
                        // 不自动写入 apiPath：路径只跟资产，节点不存路径
                        boundApi = api;
                        ManagedAuthHeaderApplier.applyToNodeData(
                                data,
                                api.getAuthConfig(),
                                projectAuthJson,
                                api.getApiPath(),
                                nodeLabel,
                                warnings);
                    }
                }
            }
        }

        FlowDesignHttpNodeNormalizer.normalize(data, boundApi, projectAuthJson);
    }

    /** 按节点类型生成画布卡片副标题 summary */
    private void fillSummaries(FlowDesignPatch patch) {
        if (patch.getAddNodes() == null) return;
        for (GraphNode node : patch.getAddNodes()) {
            Map<String, Object> data = node.getData();
            if (data == null) continue;
            switch (node.getType()) {
                case "http" -> data.put("summary", buildHttpSummary(data));
                case "assert" -> data.put("summary", buildAssertSummary(data));
                case "subflow" -> data.put("summary", buildSubflowSummary(data));
                default -> {
                    if (data.get("name") != null) {
                        data.put("summary", String.valueOf(data.get("name")));
                    }
                }
            }
        }
    }

    /**
     * 生成 HTTP 节点画布副标题 summary。
     * external：METHOD ↗ host/path；
     * project：METHOD +（节点 apiPath 若有，否则 apiName，再否则 —）。
     */
    private static String buildHttpSummary(Map<String, Object> data) {
        String callMode = data.get("callMode") != null ? String.valueOf(data.get("callMode")).trim() : "";
        if (FlowHttpCallMode.isExternal(callMode)) {
            String method = data.get("httpMethod") != null
                    ? String.valueOf(data.get("httpMethod")).toUpperCase(Locale.ROOT)
                    : "GET";
            String externalUrl = data.get("externalUrl") != null ? String.valueOf(data.get("externalUrl")) : "—";
            return FlowHttpRequestBuilder.formatExternalSummary(method, externalUrl);
        }
        if (data.get("testProjectApiId") == null) {
            return "请选择项目接口";
        }
        String method = data.get("httpMethod") != null
                ? String.valueOf(data.get("httpMethod")).toUpperCase(Locale.ROOT)
                : "GET";
        String path = null;
        if (data.get("apiPath") != null && !String.valueOf(data.get("apiPath")).isBlank()) {
            path = String.valueOf(data.get("apiPath"));
        } else if (data.get("apiName") != null && !String.valueOf(data.get("apiName")).isBlank()) {
            path = String.valueOf(data.get("apiName"));
        } else {
            path = "—";
        }
        return method + " " + path;
    }

    private static String buildAssertSummary(Map<String, Object> data) {
        Object rulesObj = data.get("rules");
        if (!(rulesObj instanceof List<?> rules) || rules.isEmpty()) {
            return "点击配置断言";
        }
        List<String> parts = new ArrayList<>();
        for (Object r : rules) {
            if (r instanceof Map<?, ?> rule) {
                Object left = rule.get("left");
                Object op = rule.get("operator");
                Object right = rule.get("right");
                if (left != null && String.valueOf(left).trim().length() > 0) {
                    parts.add(String.valueOf(left) + " " + String.valueOf(op) + " " + String.valueOf(right));
                }
            }
        }
        return parts.isEmpty() ? "点击配置断言" : String.join(" 且 ", parts);
    }

    /**
     * 生成子流节点画布副标题 {@code summary}。
     * <p>
     * 优先使用 {@code subflowName}，否则为「子流#subflowId」；若配置了 {@code outputs}，
     * 追加「→ flowKey1, flowKey2」表示写回父 flow 的变量键。
     */
    private static String buildSubflowSummary(Map<String, Object> data) {
        String name = data.get("subflowName") != null ? String.valueOf(data.get("subflowName")).trim() : "";
        if (name.isEmpty() && data.get("subflowId") != null && !String.valueOf(data.get("subflowId")).isBlank()) {
            name = "子流#" + data.get("subflowId");
        }
        if (name.isEmpty()) {
            return "请选择子流";
        }
        Object outputs = data.get("outputs");
        if (outputs instanceof List<?> rows && !rows.isEmpty()) {
            List<String> keys = new ArrayList<>();
            for (Object row : rows) {
                if (row instanceof Map<?, ?> map) {
                    Object flowKey = map.get("flowKey");
                    if (flowKey != null && !String.valueOf(flowKey).isBlank()) {
                        keys.add(String.valueOf(flowKey).trim());
                    }
                }
            }
            if (!keys.isEmpty()) {
                return name + " → " + String.join(", ", keys);
            }
        }
        return name;
    }

    /** 规范化后的 patch 与校验结果 */
    public record NormalizeResult(FlowDesignPatch patch, DesignValidationResult validation) {}
}
