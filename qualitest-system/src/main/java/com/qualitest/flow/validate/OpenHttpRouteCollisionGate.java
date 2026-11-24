package com.qualitest.flow.validate;

import cn.hutool.core.util.StrUtil;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.openhttp.OpenHttpRoute;
import com.qualitest.flow.openhttp.OpenHttpRouteCatalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 设计期门禁：同一测试项目内 {@code http-entry} 的 method+path 不得重复。
 * <p>
 * 同时检查同一流内多个入口之间的冲突，以及不同流之间的冲突；保存时直接拦截，
 * 错误文案会带上已占用路由的流名与节点名。
 */
public final class OpenHttpRouteCollisionGate {

    private OpenHttpRouteCollisionGate() {}

    /**
     * 其它流上的开放路由占用信息（method / path 已规范化）。
     *
     * @param testFlowId 所属测试流 id
     * @param flowName   所属测试流名称
     * @param nodeId     入口节点 id
     * @param nodeName   入口节点名称
     * @param method     HTTP 方法
     * @param path       路由路径
     */
    public record PeerRoute(
            Long testFlowId,
            String flowName,
            String nodeId,
            String nodeName,
            String method,
            String path) {}

    /**
     * 一条测试流的路由扫描输入。
     *
     * @param testProjectId 所属测试项目 id
     * @param testFlowId    测试流 id
     * @param flowName      测试流名称
     * @param graphJson     测试流图 JSON 文本
     */
    public record FlowRouteSource(
            Long testProjectId,
            Long testFlowId,
            String flowName,
            String graphJson) {}

    /**
     * 校验当前图的开放路由是否与项目内其它流冲突，并检查本图内部是否自冲突。
     *
     * @param graph         当前待保存的图
     * @param currentFlowId 当前流 id，用于排除其自身的旧路由；可空
     * @param peers         项目内其它流已占用的路由
     * @return 冲突说明列表；无冲突时为空列表
     */
    public static List<String> validate(GraphJson graph, Long currentFlowId, List<PeerRoute> peers) {
        List<String> errors = new ArrayList<>();
        if (graph == null || graph.getNodes() == null) {
            return errors;
        }

        // 先扫描本图内的路由，记录每个 method+path 的占用节点，重复即报自冲突
        Map<String, String> localOwners = new LinkedHashMap<>();
        for (OpenHttpRoute route : OpenHttpRouteCatalog.parseRoutes(null, currentFlowId, graph)) {
            if (route == null || route.path() == null || route.path().isBlank()) {
                continue;
            }
            String key = route.method() + " " + route.path();
            GraphNode node = findNode(graph, route.entryNodeId());
            String nodeLabel = nodeLabel(node, route.entryNodeId());
            String previous = localOwners.putIfAbsent(key, nodeLabel);
            if (previous != null) {
                errors.add("开放 HTTP 入口冲突：" + key + " 在本流被「"
                        + previous + "」与「" + nodeLabel + "」同时占用");
            }
        }

        if (peers == null || peers.isEmpty()) {
            return errors;
        }
        // 再把本图路由与其它流的路由逐一比对
        for (Map.Entry<String, String> local : localOwners.entrySet()) {
            String key = local.getKey();
            for (PeerRoute peer : peers) {
                if (peer == null || peer.path() == null || peer.path().isBlank()) {
                    continue;
                }
                if (currentFlowId != null && currentFlowId.equals(peer.testFlowId())) {
                    continue;
                }
                String peerKey = peer.method() + " " + peer.path();
                if (!key.equals(peerKey)) {
                    continue;
                }
                errors.add("开放 HTTP 入口冲突：" + key + " 已被流「"
                        + StrUtil.blankToDefault(peer.flowName(), "#" + peer.testFlowId())
                        + "」的入口「"
                        + peerNodeLabel(peer)
                        + "」占用（本流节点「" + local.getValue() + "」）");
            }
        }
        return errors;
    }

    /**
     * 把项目内其它流的图解析为已占用路由列表；图为空或解析失败的流被跳过。
     *
     * @param flows 其它流的路由扫描输入
     * @return 已占用路由列表
     */
    public static List<PeerRoute> peersFromFlows(List<FlowRouteSource> flows) {
        List<PeerRoute> peers = new ArrayList<>();
        if (flows == null) {
            return peers;
        }
        for (FlowRouteSource flow : flows) {
            if (flow == null || StrUtil.isBlank(flow.graphJson())) {
                continue;
            }
            GraphJson g;
            try {
                g = GraphJson.parse(flow.graphJson());
            } catch (Exception e) {
                continue;
            }
            for (OpenHttpRoute route : OpenHttpRouteCatalog.parseRoutes(
                    flow.testProjectId(), flow.testFlowId(), g)) {
                if (route == null || route.path() == null || route.path().isBlank()) {
                    continue;
                }
                GraphNode node = findNode(g, route.entryNodeId());
                peers.add(new PeerRoute(
                        flow.testFlowId(),
                        flow.flowName(),
                        route.entryNodeId(),
                        node != null ? node.getName() : null,
                        route.method(),
                        route.path()));
            }
        }
        return peers;
    }

    /** 按节点 id 在图中查找节点，找不到返回 null */
    private static GraphNode findNode(GraphJson graph, String nodeId) {
        if (graph == null || graph.getNodes() == null || nodeId == null) {
            return null;
        }
        for (GraphNode n : graph.getNodes()) {
            if (n != null && nodeId.equals(n.getId())) {
                return n;
            }
        }
        return null;
    }

    /** 取本图节点的展示标签：优先节点名，其次节点 id，最后回退传入 id */
    private static String nodeLabel(GraphNode node, String fallbackId) {
        if (node != null && StrUtil.isNotBlank(node.getName())) {
            return node.getName().trim();
        }
        if (node != null && StrUtil.isNotBlank(node.getId())) {
            return node.getId().trim();
        }
        return StrUtil.blankToDefault(fallbackId, "?");
    }

    /** 取其它流入口节点的展示标签：优先节点名，其次节点 id */
    private static String peerNodeLabel(PeerRoute peer) {
        if (peer == null) {
            return "?";
        }
        if (StrUtil.isNotBlank(peer.nodeName())) {
            return peer.nodeName().trim();
        }
        return StrUtil.blankToDefault(peer.nodeId(), "?");
    }
}
