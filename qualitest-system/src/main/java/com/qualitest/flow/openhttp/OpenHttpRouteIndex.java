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
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 项目级开放 HTTP 路由索引（method+path → 流/入口引用）。
 * <p>
 * 索引正文只存 {@link RouteRef}，整图走 {@link ParsedGraphCache}。
 * 失效订阅 {@link FlowExternalChangeHub}；miss 时扫项目未删流重建。
 * 冲突策略：先写入者胜（与历史 Catalog 扫描顺序一致）。
 */
@Component
public class OpenHttpRouteIndex {

    private final TestFlowMapper testFlowMapper;
    private final ParsedGraphCache parsedGraphCache;
    private final FlowExternalChangeHub hub;

    /** projectId → ( "METHOD path" → RouteRef ) */
    private final ConcurrentHashMap<Long, Map<String, RouteRef>> byProject = new ConcurrentHashMap<>();

    /** 项目索引重建次数（单测断言用） */
    private final AtomicInteger rebuildCount = new AtomicInteger();

    private Runnable unsubscribe;

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
        Map<String, RouteRef> index = byProject.get(testProjectId);
        if (index == null) {
            index = rebuild(testProjectId);
        }
        RouteRef ref = index.get(key);
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
     * 索引未建时先重建；不附带整图。
     */
    public List<OpenHttpRouteCollisionGate.PeerRoute> listPeerRoutes(Long testProjectId) {
        if (testProjectId == null) {
            return List.of();
        }
        Map<String, RouteRef> index = byProject.get(testProjectId);
        if (index == null) {
            index = rebuild(testProjectId);
        }
        List<OpenHttpRouteCollisionGate.PeerRoute> peers = new ArrayList<>(index.size());
        for (RouteRef ref : index.values()) {
            peers.add(new OpenHttpRouteCollisionGate.PeerRoute(
                    ref.testFlowId(),
                    ref.flowName(),
                    ref.entryNodeId(),
                    ref.nodeName(),
                    ref.method(),
                    ref.path()));
        }
        return peers;
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
     * 扫项目未删流重建索引；同 key 先写入者胜。
     * 入口解析复用 {@link OpenHttpRouteCatalog#parseRoutes}，避免与 Catalog 双份解析逻辑。
     */
    Map<String, RouteRef> rebuild(Long testProjectId) {
        rebuildCount.incrementAndGet();
        Map<String, RouteRef> next = new LinkedHashMap<>();
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
                    next.putIfAbsent(
                            routeKey(route.method(), route.path()),
                            toRef(flow, graph, route));
                }
                // 重建时顺带温热图缓存，避免随后 find 再 parse
                parsedGraphCache.put(flow.getTestFlowId(), flow.getTestProjectId(), rev, graph);
            }
        }
        byProject.put(testProjectId, next);
        return next;
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
