package com.qualitest.flow.openhttp;

import com.qualitest.flow.cache.ParsedGraphCache;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.sync.FlowExternalChangeEvent;
import com.qualitest.flow.sync.FlowExternalChangeHub;
import com.qualitest.flow.validate.OpenHttpRouteCollisionGate;
import com.qualitest.project.domain.TestFlow;
import com.qualitest.project.mapper.TestFlowMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 项目级开放 HTTP 路由索引（method+path → 流/入口引用）。
 * <p>
 * 索引正文只存 {@link RouteRef}，整图走 {@link ParsedGraphCache}。
 * 失效订阅 {@link FlowExternalChangeHub}；miss 时扫项目未删流重建。
 * 同 key 被多流占用时：入站查找不命中（拒绝双活），peers 列表仍暴露全部占用供保存门禁。
 */
@Component
public class OpenHttpRouteIndex {

    private static final Logger log = LoggerFactory.getLogger(OpenHttpRouteIndex.class);

    private final TestFlowMapper testFlowMapper;
    private final ParsedGraphCache parsedGraphCache;
    private final FlowExternalChangeHub hub;

    /** projectId → 重建后的查找索引与 peers 快照 */
    private final ConcurrentHashMap<Long, ProjectRoutes> byProject = new ConcurrentHashMap<>();

    /** 项目索引重建次数（单测断言用） */
    private final AtomicInteger rebuildCount = new AtomicInteger();

    private Runnable unsubscribe;

    /** 单项目路由快照：查找用去歧义索引 + 门禁用全量 peers */
    record ProjectRoutes(Map<String, RouteRef> byKey, List<OpenHttpRouteCollisionGate.PeerRoute> peers) {}

    public OpenHttpRouteIndex(TestFlowMapper testFlowMapper,
                              ParsedGraphCache parsedGraphCache,
                              FlowExternalChangeHub hub) {
        this.testFlowMapper = testFlowMapper;
        this.parsedGraphCache = parsedGraphCache;
        this.hub = hub;
    }

    @PostConstruct
    public void subscribe() {
        unsubscribe = hub.subscribeAll(this::onExternalChange);
    }

    @PreDestroy
    void teardown() {
        if (unsubscribe != null) {
            unsubscribe.run();
            unsubscribe = null;
        }
    }

    /**
     * 按 method+path 查找开放路由。
     *
     * @return 命中的路由；无匹配或参数非法时 null
     */
    public OpenHttpRoute find(Long testProjectId, String method, String path) {
        String m = OpenHttpRouteCatalog.normalizeMethod(method);
        String p = OpenHttpRouteCatalog.normalizePath(path);
        if (testProjectId == null || m.isEmpty() || p.isEmpty()) {
            return null;
        }
        String key = routeKey(m, p);
        ProjectRoutes project = byProject.get(testProjectId);
        if (project == null) {
            project = rebuild(testProjectId);
        }
        RouteRef ref = project.byKey().get(key);
        if (ref == null) {
            return null;
        }
        GraphJson graph = parsedGraphCache.get(ref.testFlowId());
        if (graph == null) {
            // 流已删或图不可用：索引陈旧，失效后返回未命中
            invalidateProject(testProjectId);
            return null;
        }
        return new OpenHttpRoute(
                testProjectId,
                ref.testFlowId(),
                ref.entryNodeId(),
                m,
                p,
                ref.envId(),
                graph);
    }

    /**
     * 列出项目内已占用的开放路由（供冲突门禁）。
     * 含脏数据双活占用；索引未建时先重建；不附带整图。
     */
    public List<OpenHttpRouteCollisionGate.PeerRoute> listPeerRoutes(Long testProjectId) {
        if (testProjectId == null) {
            return List.of();
        }
        ProjectRoutes project = byProject.get(testProjectId);
        if (project == null) {
            project = rebuild(testProjectId);
        }
        return List.copyOf(project.peers());
    }

    /** 使某项目路由索引失效 */
    public void invalidateProject(Long testProjectId) {
        if (testProjectId != null) {
            byProject.remove(testProjectId);
        }
    }

    /** 清空全部索引（单测用） */
    public void clear() {
        byProject.clear();
    }

    public int getRebuildCount() {
        return rebuildCount.get();
    }

    public void resetCounters() {
        rebuildCount.set(0);
    }

    /**
     * 扫项目未删流重建索引。
     * 同 key 多流占用时从查找索引剔除（入站 404），peers 仍保留全部占用；
     * 入口解析复用 {@link OpenHttpRouteCatalog#parseRoutes}。
     */
    ProjectRoutes rebuild(Long testProjectId) {
        rebuildCount.incrementAndGet();
        Map<String, RouteRef> byKey = new LinkedHashMap<>();
        List<OpenHttpRouteCollisionGate.PeerRoute> peers = new ArrayList<>();
        Set<String> ambiguousKeys = new HashSet<>();
        TestFlow query = new TestFlow();
        query.setTestProjectId(testProjectId);
        query.setDelStatus(0);
        List<TestFlow> flows = testFlowMapper.selectTestFlowList(query);
        if (flows != null) {
            for (TestFlow flow : flows) {
                if (flow == null || flow.getGraphJson() == null || flow.getGraphJson().isBlank()) {
                    continue;
                }
                GraphJson graph;
                try {
                    graph = GraphJson.parse(flow.getGraphJson());
                } catch (Exception e) {
                    continue;
                }
                if (graph == null) {
                    continue;
                }
                Long rev = flow.getGraphRevision() != null ? flow.getGraphRevision() : 0L;
                for (OpenHttpRoute route : OpenHttpRouteCatalog.parseRoutes(
                        flow.getTestProjectId(), flow.getTestFlowId(), graph)) {
                    if (route == null || route.path() == null || route.path().isEmpty()) {
                        continue;
                    }
                    RouteRef ref = toRef(flow, graph, route);
                    peers.add(new OpenHttpRouteCollisionGate.PeerRoute(
                            ref.testFlowId(),
                            ref.flowName(),
                            ref.entryNodeId(),
                            ref.nodeName(),
                            ref.method(),
                            ref.path()));
                    String key = routeKey(route.method(), route.path());
                    if (ambiguousKeys.contains(key)) {
                        continue;
                    }
                    RouteRef previous = byKey.putIfAbsent(key, ref);
                    if (previous != null && !Objects.equals(previous.testFlowId(), ref.testFlowId())) {
                        byKey.remove(key);
                        ambiguousKeys.add(key);
                        log.warn(
                                "开放 HTTP 路由双活：项目 {} 的 {} 同时被流 {} 与 {} 占用，入站暂不命中",
                                testProjectId,
                                key,
                                previous.testFlowId(),
                                ref.testFlowId());
                    }
                }
                // 重建时顺带温热图缓存，避免随后 find 再 parse
                parsedGraphCache.put(flow.getTestFlowId(), flow.getTestProjectId(), rev, graph);
            }
        }
        ProjectRoutes snapshot = new ProjectRoutes(Map.copyOf(byKey), List.copyOf(peers));
        byProject.put(testProjectId, snapshot);
        return snapshot;
    }

    private void onExternalChange(FlowExternalChangeEvent event) {
        if (event == null || event.getType() == null) {
            return;
        }
        String type = event.getType();
        if (FlowExternalChangeEvent.TYPE_GRAPH_COMMITTED.equals(type)
                || FlowExternalChangeEvent.TYPE_FLOW_CREATED.equals(type)
                || FlowExternalChangeEvent.TYPE_FLOW_DELETED.equals(type)
                || FlowExternalChangeEvent.TYPE_FLOW_META_CHANGED.equals(type)
                || FlowExternalChangeEvent.TYPE_PROJECT_DELETED.equals(type)) {
            invalidateProject(event.getTestProjectId());
        }
    }

    static String routeKey(String method, String path) {
        return method + " " + path;
    }

    private static RouteRef toRef(TestFlow flow, GraphJson graph, OpenHttpRoute route) {
        return new RouteRef(
                route.testFlowId(),
                route.entryNodeId(),
                route.method(),
                route.path(),
                route.envId(),
                flow.getFlowName(),
                nodeName(graph, route.entryNodeId()));
    }

    private static String nodeName(GraphJson graph, String entryNodeId) {
        if (graph == null || graph.getNodes() == null || entryNodeId == null) {
            return null;
        }
        for (GraphNode node : graph.getNodes()) {
            if (node != null && entryNodeId.equals(node.getId())) {
                return node.getName();
            }
        }
        return null;
    }

    /**
     * 索引中的路由引用（不含整图）。
     */
    public record RouteRef(
            Long testFlowId,
            String entryNodeId,
            String method,
            String path,
            Long envId,
            String flowName,
            String nodeName) {}
}
