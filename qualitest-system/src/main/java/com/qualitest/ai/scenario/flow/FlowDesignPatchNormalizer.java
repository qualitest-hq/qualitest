package com.qualitest.ai.scenario.flow;

import cn.hutool.core.util.IdUtil;
import com.qualitest.ai.scenario.flow.model.FlowDesignScenarioPatch;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
import com.qualitest.ai.scenario.flow.model.DesignValidationResult;
import com.qualitest.ai.tools.FlowDesignIds;
import com.qualitest.flow.graph.GraphLookupUtils;
import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.model.GraphNodePosition;
import com.qualitest.flow.model.GraphRunScenario;
import com.qualitest.flow.http.FlowHttpCallMode;
import com.qualitest.flow.http.FlowHttpRequestBuilder;
import com.qualitest.flow.validate.AssertPathDesignGate;
import com.qualitest.flow.validate.GraphJsonValidator;
import com.qualitest.flow.validate.GraphValidationResult;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.mapper.TestProjectApiMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AI 产出流程补丁的服务端规范化器。
 * <p>
 * 在 AI submit 与 Web Diff 合并前依次执行：
 * <ol>
 *   <li>为 addNodes/addEdges 补雪花 id 与默认 position</li>
 *   <li>校验 HTTP(project) 节点 testProjectApiId 属于当前项目；external 跳过 API 归属校验</li>
 *   <li>补 data.summary（project / external / subflow 各自格式）</li>
 *   <li>规范化 scenarioPatch（场景 id、flowSeed 键名等）</li>
 *   <li>预合并到基准图副本，跑图结构校验，得到 errors/warnings</li>
 *   <li>用上游接口响应示例试算 assert/condition 的 http.body 左值；未命中记入 errors 回传模型</li>
 * </ol>
 * 不写库；用户在前端 Diff 确认后才持久化 graph_json。
 */
@Component
@RequiredArgsConstructor
public class FlowDesignPatchNormalizer {

    private static final double GRID_X = 380.0;
    private static final double DEFAULT_X = 40.0;
    private static final double DEFAULT_Y = 80.0;

    private final TestProjectApiMapper testProjectApiMapper;
    private final GraphJsonValidator graphJsonValidator;
    private final FlowDesignPatchMerger patchMerger;

    /**
     * 全量规范化并预合并校验：用于 AI submit 工具回调。
     * 合并全部 patch 后跑图结构校验，再用响应示例试算断言路径；错误回传模型以便自我修正。
     */
    public NormalizeResult normalize(FlowDesignPatch patch, GraphJson baseGraph, Long testProjectId) {
        List<String> normWarnings = new ArrayList<>();
        patch = initAndNormalizePatch(patch, baseGraph, testProjectId, normWarnings);

        GraphJson merged = patchMerger.mergeAll(baseGraph, patch, normWarnings);
        GraphValidationResult validation = graphJsonValidator.validate(merged);
        List<String> warnings = new ArrayList<>(normWarnings);
        warnings.addAll(validation.getWarnings());

        List<String> errors = new ArrayList<>(validation.getErrors());
        // 设计期门禁：AI 提交的坏断言路径（试算空/[]）直接进 errors，进不了 Staging
        errors.addAll(AssertPathDesignGate.validate(merged,
                testProjectApiMapper == null ? id -> null : testProjectApiMapper::selectTestProjectApiById));

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
     * normalize 与 preparePatch 共用的 patch 初始化与规范化步骤。
     * 依次：suggestedDeletes、雪花 id / position、scenarioPatch、
     * HTTP API 绑定校验、assert 规则规范化、节点 summary。
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
        normalizeAssertNodes(patch);
        normalizeConditionNodes(patch);
        fillSummaries(patch);
        return patch;
    }

    /**
     * 规范化 patch 中 assert 节点 rules：运算符别名、去 {{}}、{@code $…} 左值改成 {@code http.body…}。
     * addNodes 看 type=assert；updateNodes 在 type=assert 或 data 含 rules 时处理。
     */
    private static void normalizeAssertNodes(FlowDesignPatch patch) {
        if (patch.getAddNodes() != null) {
            for (GraphNode node : patch.getAddNodes()) {
                if (node != null && "assert".equals(node.getType()) && node.getData() != null) {
                    FlowDesignAssertNodeNormalizer.normalize(node.getData());
                }
            }
        }
        if (patch.getUpdateNodes() != null) {
            for (GraphNode node : patch.getUpdateNodes()) {
                if (node == null || node.getData() == null) {
                    continue;
                }
                boolean isAssert = "assert".equals(node.getType());
                if (!isAssert && node.getData().containsKey("rules")) {
                    isAssert = true;
                }
                if (isAssert) {
                    FlowDesignAssertNodeNormalizer.normalize(node.getData());
                }
            }
        }
    }

    /**
     * 规范化 condition 节点 {@code branches[].conditions[]}（运算符、{{}}、{@code $} 左值）。
     */
    private static void normalizeConditionNodes(FlowDesignPatch patch) {
        if (patch.getAddNodes() != null) {
            for (GraphNode node : patch.getAddNodes()) {
                if (node != null && "condition".equals(node.getType()) && node.getData() != null) {
                    FlowDesignAssertNodeNormalizer.normalizeConditionBranches(node.getData());
                }
            }
        }
        if (patch.getUpdateNodes() != null) {
            for (GraphNode node : patch.getUpdateNodes()) {
                if (node == null || node.getData() == null) {
                    continue;
                }
                boolean isCondition = "condition".equals(node.getType());
                if (!isCondition && node.getData().containsKey("branches")) {
                    isCondition = true;
                }
                if (isCondition) {
                    FlowDesignAssertNodeNormalizer.normalizeConditionBranches(node.getData());
                }
            }
        }
    }

    /**
     * 规范化新增节点与连线的 id。
     * <p>
     * 缺 id 或非数字 id 时生成雪花 id；缺 position 时按基准图末节点网格推算坐标。
     * 节点 id 被替换时，同步将 addEdges 的 source/target 映射到新 id，避免连线端点悬空。
     */
    private void normalizeIds(FlowDesignPatch patch, GraphJson baseGraph) {
        Map<String, String> idRemap = new HashMap<>();
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
                if (node.getPosition() == null) {
                    node.setPosition(defaultAddPosition(baseGraph, index));
                }
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

    /** 画布节点/边 id 须为纯数字雪花 id */
    private static boolean isValidId(String id) {
        return id != null && id.matches("\\d+");
    }

    /** 校验 patch 中 HTTP 节点（新增或更新）绑定的 API 是否属于当前项目 */
    private void validateApiBindings(FlowDesignPatch patch, GraphJson baseGraph, Long testProjectId, List<String> warnings) {
        if (patch.getAddNodes() != null) {
            for (GraphNode node : patch.getAddNodes()) {
                validateHttpNodeApiBinding(node, testProjectId, warnings);
            }
        }
        if (patch.getUpdateNodes() != null) {
            for (GraphNode update : patch.getUpdateNodes()) {
                GraphNode effective = resolveUpdateNodeForApiValidation(baseGraph, update);
                validateHttpNodeApiBinding(effective, testProjectId, warnings);
                syncApiBindingFieldsToUpdate(update, effective);
            }
        }
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

    /** 将 API 校验结果（置空/补全）写回 updateNodes 的增量 data */
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
        update.setData(updateData);
    }

    /** 节点 data 含 testProjectApiId 时校验归属；external 模式校验 externalUrl 并跳过 API 绑定 */
    private void validateHttpNodeApiBinding(GraphNode node, Long testProjectId, List<String> warnings) {
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
            FlowDesignHttpNodeNormalizer.normalize(data, null);
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
                    TestProjectApi api = testProjectApiMapper.selectTestProjectApiById(apiId);
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
                    }
                }
            }
        }

        FlowDesignHttpNodeNormalizer.normalize(data, boundApi);
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
