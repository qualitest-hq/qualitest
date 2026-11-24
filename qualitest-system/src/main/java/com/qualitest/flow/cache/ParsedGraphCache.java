package com.qualitest.flow.cache;

import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.sync.FlowExternalChangeEvent;
import com.qualitest.flow.sync.FlowExternalChangeHub;
import com.qualitest.project.domain.TestFlow;
import com.qualitest.project.mapper.TestFlowMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 进程内已解析 {@link GraphJson} 缓存。
 * <p>
 * 键为 testFlowId；命中且 revision 与库一致时跳过 parse。
 * 失效只订阅 {@link FlowExternalChangeHub}（本机 publish + 他机经 Redis 注入），不直接碰 Redis。
 */
@Component
public class ParsedGraphCache {

    private final TestFlowMapper testFlowMapper;
    private final FlowExternalChangeHub hub;

    private final ConcurrentHashMap<Long, Entry> byFlowId = new ConcurrentHashMap<>();

    /** 实际执行 GraphJson.parse 的次数（单测断言用） */
    private final AtomicInteger parseCount = new AtomicInteger();
    /** 实际查库次数（单测断言用） */
    private final AtomicInteger loadCount = new AtomicInteger();

    private Runnable unsubscribe;

    public ParsedGraphCache(TestFlowMapper testFlowMapper, FlowExternalChangeHub hub) {
        this.testFlowMapper = testFlowMapper;
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
     * 按 id 查库后取已解析图；流不存在、已删或图为空时返回 null。
     * 缓存命中且 revision 一致时不 parse。
     */
    public GraphJson get(Long testFlowId) {
        if (testFlowId == null) {
            return null;
        }
        loadCount.incrementAndGet();
        return resolve(testFlowMapper.selectTestFlowById(testFlowId));
    }

    /**
     * 已持有 {@link TestFlow} 行时取图（不再查库）；用于子流等刚加载过定义的路径。
     */
    public GraphJson getFrom(TestFlow flow) {
        return resolve(flow);
    }

    /**
     * 直接写入已解析图（索引重建等温热路径，避免紧接着再 parse）。
     */
    public void put(Long testFlowId, Long testProjectId, Long graphRevision, GraphJson graph) {
        if (testFlowId == null || graph == null) {
            return;
        }
        Long rev = graphRevision != null ? graphRevision : 0L;
        byFlowId.put(testFlowId, new Entry(testProjectId, rev, graph));
    }

    /** 使单流图缓存失效 */
    public void invalidate(Long testFlowId) {
        if (testFlowId != null) {
            byFlowId.remove(testFlowId);
        }
    }

    /** 使某项目下已缓存的全部流图失效 */
    public void invalidateProject(Long testProjectId) {
        if (testProjectId == null) {
            return;
        }
        Iterator<Map.Entry<Long, Entry>> it = byFlowId.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Entry> e = it.next();
            if (testProjectId.equals(e.getValue().testProjectId)) {
                it.remove();
            }
        }
    }

    /** 清空全部（单测用） */
    public void clear() {
        byFlowId.clear();
    }

    public int getParseCount() {
        return parseCount.get();
    }

    public int getLoadCount() {
        return loadCount.get();
    }

    public void resetCounters() {
        parseCount.set(0);
        loadCount.set(0);
    }

    /** 当前缓存条数（单测用） */
    public int size() {
        return byFlowId.size();
    }

    private GraphJson resolve(TestFlow flow) {
        if (flow == null || flow.getTestFlowId() == null
                || (flow.getDelStatus() != null && flow.getDelStatus() != 0)) {
            if (flow != null && flow.getTestFlowId() != null) {
                byFlowId.remove(flow.getTestFlowId());
            }
            return null;
        }
        Long testFlowId = flow.getTestFlowId();
        if (flow.getGraphJson() == null || flow.getGraphJson().isBlank()) {
            byFlowId.remove(testFlowId);
            return null;
        }
        Long revision = flow.getGraphRevision() != null ? flow.getGraphRevision() : 0L;
        Entry hit = byFlowId.get(testFlowId);
        if (hit != null && Objects.equals(hit.graphRevision, revision) && hit.graph != null) {
            return hit.graph;
        }
        GraphJson graph;
        try {
            parseCount.incrementAndGet();
            graph = GraphJson.parse(flow.getGraphJson());
        } catch (Exception e) {
            byFlowId.remove(testFlowId);
            return null;
        }
        if (graph == null) {
            byFlowId.remove(testFlowId);
            return null;
        }
        byFlowId.put(testFlowId, new Entry(flow.getTestProjectId(), revision, graph));
        return graph;
    }

    private void onExternalChange(FlowExternalChangeEvent event) {
        if (event == null || event.getType() == null) {
            return;
        }
        String type = event.getType();
        if (FlowExternalChangeEvent.TYPE_GRAPH_COMMITTED.equals(type)
                || FlowExternalChangeEvent.TYPE_FLOW_CREATED.equals(type)
                || FlowExternalChangeEvent.TYPE_FLOW_DELETED.equals(type)
                || FlowExternalChangeEvent.TYPE_FLOW_META_CHANGED.equals(type)) {
            invalidate(event.getTestFlowId());
            return;
        }
        if (FlowExternalChangeEvent.TYPE_PROJECT_DELETED.equals(type)) {
            invalidateProject(event.getTestProjectId());
        }
    }

    private record Entry(Long testProjectId, Long graphRevision, GraphJson graph) {}
}
