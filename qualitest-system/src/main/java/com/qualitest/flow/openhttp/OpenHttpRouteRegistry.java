package com.qualitest.flow.openhttp;

import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.validate.FlowIssue;
import com.qualitest.flow.validate.FlowIssueCodes;
import com.qualitest.flow.validate.OpenHttpRouteCollisionGate;
import com.qualitest.project.result.TestFlowResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 开放 HTTP 路由唯一端口：规范化、解析、peers、冲突、列表标注。
 * <p>
 * 保存只落盘，不在此硬拦；开跑 / 入站 / 画布 / 列表均经本类询问冲突。
 */
@Component
@RequiredArgsConstructor
public class OpenHttpRouteRegistry {

    private final OpenHttpRouteIndex routeIndex;
    private final OpenHttpRouteCatalog routeCatalog;

    /** HTTP 方法规范化 */
    public String normalizeMethod(String method) {
        return OpenHttpRouteCatalog.normalizeMethod(method);
    }

    /** 路径规范化 */
    public String normalizePath(String path) {
        return OpenHttpRouteCatalog.normalizePath(path);
    }

    /**
     * 按项目 + method + path 解析入站路由；无匹配或歧义已从查找索引剔除时返回 null（调用方 404）。
     */
    public OpenHttpRoute resolve(Long testProjectId, String method, String path) {
        return routeCatalog.find(testProjectId, method, path);
    }

    /** 项目内已占用路由（供画布 peers 预检） */
    public List<OpenHttpRouteCollisionGate.PeerRoute> listPeers(Long testProjectId) {
        if (testProjectId == null) {
            return List.of();
        }
        return routeIndex.listPeerRoutes(testProjectId);
    }

    /**
     * 当前图相对项目 peers 的路由冲突诊断。
     */
    public List<FlowIssue> conflicts(
            GraphJson graph, Long testProjectId, Long currentFlowId, FlowIssue.Stage stage) {
        List<OpenHttpRouteCollisionGate.PeerRoute> peers =
                testProjectId != null ? routeIndex.listPeerRoutes(testProjectId) : List.of();
        List<String> messages = OpenHttpRouteCollisionGate.validate(graph, currentFlowId, peers);
        List<FlowIssue> issues = new ArrayList<>(messages.size());
        FlowIssue.Stage effective = stage != null ? stage : FlowIssue.Stage.RUN;
        for (String message : messages) {
            issues.add(FlowIssue.error(
                    FlowIssueCodes.ENTRY_ROUTE_CONFLICT, effective, null, message));
        }
        return issues;
    }

    /**
     * 给列表行标注 routeConflict / routeConflictMessage。
     */
    public void annotateFlows(List<TestFlowResult> rows, Long testProjectId) {
        if (rows == null || rows.isEmpty() || testProjectId == null) {
            return;
        }
        List<OpenHttpRouteIndex.AmbiguousRoute> conflicts = routeIndex.findAmbiguousRoutes(testProjectId);
        if (conflicts.isEmpty()) {
            return;
        }
        Map<Long, String> messages = new LinkedHashMap<>();
        for (OpenHttpRouteIndex.AmbiguousRoute conflict : conflicts) {
            for (OpenHttpRouteCollisionGate.PeerRoute entry : conflict.entries()) {
                Long flowId = entry.testFlowId();
                if (flowId == null) {
                    continue;
                }
                List<String> others = new ArrayList<>();
                for (OpenHttpRouteCollisionGate.PeerRoute peer : conflict.entries()) {
                    if (!Objects.equals(peer.testFlowId(), flowId)) {
                        others.add(peerLabel(peer));
                    }
                }
                String who = others.isEmpty() ? "本流其它入口" : String.join("、", others);
                messages.merge(flowId, conflict.key() + " 与 " + who + " 冲突", (a, b) -> a + "；" + b);
            }
        }
        for (TestFlowResult row : rows) {
            if (row == null || row.getTestFlowId() == null) {
                continue;
            }
            String message = messages.get(row.getTestFlowId());
            if (message != null) {
                row.setRouteConflict(Boolean.TRUE);
                row.setRouteConflictMessage("开放 HTTP 入口冲突：" + message);
            }
        }
    }

    /** 使某项目路由索引失效（写库后调用） */
    public void invalidateProject(Long testProjectId) {
        routeIndex.invalidateProject(testProjectId);
    }

    private static String peerLabel(OpenHttpRouteCollisionGate.PeerRoute peer) {
        String flow = peer.flowName() != null && !peer.flowName().isBlank()
                ? peer.flowName()
                : "流#" + peer.testFlowId();
        String node = peer.nodeName() != null && !peer.nodeName().isBlank()
                ? peer.nodeName()
                : peer.nodeId();
        return "「" + flow + "」入口「" + node + "」";
    }
}
