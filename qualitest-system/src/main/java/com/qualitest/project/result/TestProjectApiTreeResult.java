package com.qualitest.project.result;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API 树节点结果对象（扁平分组 + 接口列表通过 {@link #buildTree} 组装为根节点列表）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TestProjectApiTreeResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final ObjectMapper OM = new ObjectMapper();

    /**
     * 节点类型：{@code group} 分组，{@code api} 接口
     */
    private String nodeType;

    /**
     * 树节点展示文案（分组为分组名，接口为 API 名称）
     */
    private String label;

    /**
     * el-tree 的 node-key，全局唯一，例如 {@code g-分组ID}、{@code a-接口ID}、未分组为 {@code g-ungrouped}
     */
    private String treeNodeKey;

    /**
     * 子节点列表；仅分组节点使用。接口节点为叶子，序列化时省略
     */
    private List<TestProjectApiTreeResult> children;

    /**
     * 分组有效：其下（含嵌套子分组）所挂接口的总数，用于侧栏显示数量
     */
    private Integer apiCount;

    /**
     * 分组有效：当前分组的业务主键
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long apiGroupId;

    /**
     * 分组有效：所属测试项目 ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectId;

    /**
     * 分组有效：父分组 ID，根分组通常为空
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long parentId;

    /**
     * 分组有效：祖级链（与数据库分组表一致，便于扩展）
     */
    private String ancestors;

    /**
     * 分组有效：分组名称（与 label 在根分组上相同）
     */
    private String groupName;

    /**
     * 分组有效：同层排序号
     */
    private Integer sortNum;

    /**
     * 接口有效：API 主键
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectApiId;

    /**
     * 接口有效：API 名称
     */
    private String apiName;

    /**
     * 接口有效：请求路径
     */
    private String apiPath;

    /**
     * 接口有效：从 requestConfig 中解析的 HTTP 方法大写，如 GET、POST
     */
    private String httpMethod;

    /**
     * 由分组列表与接口列表拼装侧边栏树（含嵌套分组、未分组、apiCount 汇总）。
     */
    public static List<TestProjectApiTreeResult> buildTree(
            List<TestProjectApiGroupResult> groups,
            List<TestProjectApiResult> apis) {
        if (groups == null) {
            groups = List.of();
        }
        if (apis == null) {
            apis = List.of();
        }

        Map<Long, TestProjectApiTreeResult> map = new HashMap<>();
        for (TestProjectApiGroupResult g : groups) {
            if (g == null || g.getApiGroupId() == null) {
                continue;
            }
            map.put(g.getApiGroupId(), groupResultToNode(g));
        }

        List<TestProjectApiTreeResult> roots = new ArrayList<>();
        for (TestProjectApiGroupResult g : groups) {
            if (g == null || g.getApiGroupId() == null) {
                continue;
            }
            TestProjectApiTreeResult node = map.get(g.getApiGroupId());
            Long pid = g.getParentId();
            if (pid != null && map.containsKey(pid)) {
                map.get(pid).getChildren().add(node);
            } else {
                roots.add(node);
            }
        }

        List<TestProjectApiTreeResult> orphanApis = new ArrayList<>();
        for (TestProjectApiResult api : apis) {
            if (api == null || api.getTestProjectApiId() == null) {
                continue;
            }
            TestProjectApiTreeResult apiNode = apiResultToNode(api);
            Long gid = api.getApiGroupId();
            if (gid != null && map.containsKey(gid)) {
                map.get(gid).getChildren().add(apiNode);
            } else {
                orphanApis.add(apiNode);
            }
        }

        if (!orphanApis.isEmpty()) {
            roots.add(TestProjectApiTreeResult.builder()
                    .nodeType("group")
                    .groupName("未分组")
                    .label("未分组")
                    .treeNodeKey("g-ungrouped")
                    .apiGroupId(null)
                    .parentId(null)
                    .children(new ArrayList<>(orphanApis))
                    .apiCount(0)
                    .build());
        }

        recountGroupApiCounts(roots);
        return roots;
    }

    private static TestProjectApiTreeResult groupResultToNode(TestProjectApiGroupResult g) {
        return TestProjectApiTreeResult.builder()
                .nodeType("group")
                .label(g.getGroupName())
                .treeNodeKey("g-" + g.getApiGroupId())
                .children(new ArrayList<>())
                .apiCount(0)
                .apiGroupId(g.getApiGroupId())
                .testProjectId(g.getTestProjectId())
                .parentId(g.getParentId())
                .ancestors(g.getAncestors())
                .groupName(g.getGroupName())
                .sortNum(g.getSortNum())
                .build();
    }

    private static TestProjectApiTreeResult apiResultToNode(TestProjectApiResult api) {
        String name = api.getApiName();
        return TestProjectApiTreeResult.builder()
                .nodeType("api")
                .label(name != null && !name.isEmpty() ? name : "(未命名)")
                .treeNodeKey("a-" + api.getTestProjectApiId())
                .testProjectApiId(api.getTestProjectApiId())
                .apiName(api.getApiName())
                .apiPath(api.getApiPath() != null ? api.getApiPath() : "")
                .httpMethod(extractHttpMethod(api.getRequestConfig()))
                .build();
    }

    private static String extractHttpMethod(String requestConfig) {
        if (requestConfig == null || requestConfig.isBlank()) {
            return "";
        }
        String t = requestConfig.trim();
        if (!t.startsWith("{")) {
            return "";
        }
        try {
            JsonNode root = OM.readTree(t);
            JsonNode m = root.get("method");
            if (m == null || m.isNull()) {
                m = root.get("httpMethod");
            }
            if (m != null && !m.isNull()) {
                return m.asText().toUpperCase().trim();
            }
        } catch (Exception ignored) {
            return "";
        }
        return "";
    }

    private static void recountGroupApiCounts(List<TestProjectApiTreeResult> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        for (TestProjectApiTreeResult node : nodes) {
            if (!"group".equals(node.getNodeType())) {
                continue;
            }
            List<TestProjectApiTreeResult> ch = node.getChildren();
            if (ch != null && !ch.isEmpty()) {
                recountGroupApiCounts(ch);
                int sum = 0;
                for (TestProjectApiTreeResult child : ch) {
                    if ("api".equals(child.getNodeType())) {
                        sum++;
                    } else {
                        sum += child.getApiCount() != null ? child.getApiCount() : 0;
                    }
                }
                node.setApiCount(sum);
            } else {
                node.setApiCount(0);
            }
        }
    }
}
