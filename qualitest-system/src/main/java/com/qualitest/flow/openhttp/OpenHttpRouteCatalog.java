package com.qualitest.flow.openhttp;

import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.validate.FlowNodeType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 开放 HTTP 路由目录门面。
 * <p>
 * 查找委托 {@link OpenHttpRouteIndex}（进程内 method+path 索引 + 图缓存）；
 * 规范化与单图解析工具仍保留在本类，供冲突门禁与单测使用。
 * method 空则视为 GET；path 统一补前导 /、去掉多余尾 /。
 */
@Component
public class OpenHttpRouteCatalog {

    private final OpenHttpRouteIndex routeIndex;

    /** 通过项目级路由索引查找开放入口 */
    public OpenHttpRouteCatalog(OpenHttpRouteIndex routeIndex) {
        this.routeIndex = routeIndex;
    }

    /**
     * 在指定项目内按 method+path 查找开放路由。
     *
     * @return 命中的路由；无匹配或参数非法时 null
     */
    public OpenHttpRoute find(Long testProjectId, String method, String path) {
        return routeIndex.find(testProjectId, method, path);
    }

    /** HTTP 方法：trim + 大写；空串视为 GET */
    public static String normalizeMethod(String method) {
        if (method == null || method.isBlank()) {
            return "GET";
        }
        return method.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 路径规范化：空→空串；补前导 /；去掉末尾多余 /（根路径保留为 /）。
     */
    public static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String p = path.trim();
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        while (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    /**
     * 将 http-entry 节点转为路由；非 http-entry 或节点空则 null。
     */
    static OpenHttpRoute toRoute(Long testProjectId, Long testFlowId, GraphJson graph, GraphNode node) {
        if (node == null || !FlowNodeType.HTTP_ENTRY.matches(node.getType())) {
            return null;
        }
        Map<String, Object> data = node.getData() != null ? node.getData() : Map.of();
        return new OpenHttpRoute(
                testProjectId,
                testFlowId,
                node.getId(),
                normalizeMethod(stringVal(data.get("method"))),
                normalizePath(stringVal(data.get("path"))),
                parseLong(data.get("envId")),
                graph);
    }

    private static String stringVal(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    private static Long parseLong(Object raw) {
        if (raw instanceof Number n) {
            return n.longValue();
        }
        if (raw == null || String.valueOf(raw).isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 从单张图解析全部 http-entry 路由（供单测构造期望列表，不查库）。
     */
    public static List<OpenHttpRoute> parseRoutes(Long testProjectId, Long testFlowId, GraphJson graph) {
        List<OpenHttpRoute> list = new ArrayList<>();
        if (graph == null || graph.getNodes() == null) {
            return list;
        }
        for (GraphNode node : graph.getNodes()) {
            OpenHttpRoute route = toRoute(testProjectId, testFlowId, graph, node);
            if (route != null) {
                list.add(route);
            }
        }
        return list;
    }
}
