package com.qualitest.flow.inbound;

import com.qualitest.flow.model.GraphInputSpec;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphMeta;
import com.qualitest.project.domain.TestFlow;
import com.qualitest.project.mapper.TestFlowMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 开放入站 path → 测试流 id 的内存索引。
 * <p>
 * 进程启动时扫描未删除测试流，把已开启开放入站且配置了 path 的流登记进来。
 * 之后单条流保存成功时调用 {@link #upsertFlow} 增量更新。
 * 查询未命中时不扫库，避免无效 path 触发全表解析。
 * path 比较前规范化为以 {@code /} 开头的小写完整相对路径。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenInboundPathIndex {

    private final TestFlowMapper testFlowMapper;

    /** 规范化后的 path → 测试流主键 */
    private final ConcurrentHashMap<String, Long> pathToFlowId = new ConcurrentHashMap<>();

    /** 启动时全量建索引 */
    @PostConstruct
    public void init() {
        refresh();
    }

    /** 全量重建：读出全部未删除测试流，重新解析图里的开放 path */
    public void refresh() {
        TestFlow query = new TestFlow();
        query.setDelStatus(0);
        List<TestFlow> flows = testFlowMapper.selectTestFlowList(query);
        ConcurrentHashMap<String, Long> next = new ConcurrentHashMap<>();
        if (flows != null) {
            for (TestFlow flow : flows) {
                indexFlow(flow, next);
            }
        }
        pathToFlowId.clear();
        pathToFlowId.putAll(next);
        log.info("开放入站 path 索引已重建，条目数={}", pathToFlowId.size());
    }

    /**
     * 单条流变更后更新索引。
     * 先去掉该流原先占用的 path，再按当前 graph_json 重新登记；已删除则只移除。
     */
    public void upsertFlow(TestFlow flow) {
        if (flow == null || flow.getTestFlowId() == null) {
            return;
        }
        Long id = flow.getTestFlowId();
        pathToFlowId.entrySet().removeIf(e -> id.equals(e.getValue()));
        if (flow.getDelStatus() != null && flow.getDelStatus() != 0) {
            return;
        }
        indexFlow(flow, pathToFlowId);
    }

    /**
     * 按入站 path 查测试流 id。
     * 未登记则返回 null，不触发库表扫描。
     */
    public Long findFlowId(String path) {
        String key = normalize(path);
        if (key.isEmpty()) {
            return null;
        }
        return pathToFlowId.get(key);
    }

    /** 当前索引快照（只读副本） */
    public Map<String, Long> snapshot() {
        return Map.copyOf(pathToFlowId);
    }

    /** 规范化完整 path，供登记与查询共用 */
    static String normalize(String path) {
        return OpenInboundPaths.normalize(path);
    }

    /**
     * 若该流开启了开放入站且配置了合法完整 path，则登记到目标 Map。
     * 同一 path 已被别的流占用时保留先到者并打告警日志；保留前缀不登记。
     */
    private static void indexFlow(TestFlow flow, ConcurrentHashMap<String, Long> target) {
        if (flow == null || flow.getTestFlowId() == null || flow.getGraphJson() == null
                || flow.getGraphJson().isBlank()) {
            return;
        }
        try {
            GraphJson graph = GraphJson.parse(flow.getGraphJson());
            GraphMeta meta = graph != null ? graph.getMeta() : null;
            GraphInputSpec spec = meta != null ? meta.getInputSpec() : null;
            if (spec == null || !Boolean.TRUE.equals(spec.getOpen())) {
                return;
            }
            String path = spec.getPath();
            if (path == null || path.isBlank()) {
                return;
            }
            String key = normalize(path);
            if (key.isEmpty() || OpenInboundPaths.isReserved(key)) {
                if (OpenInboundPaths.isReserved(key)) {
                    log.warn("开放入站 path 落在保留前缀，跳过登记: {} flowId={}", key, flow.getTestFlowId());
                }
                return;
            }
            Long prev = target.putIfAbsent(key, flow.getTestFlowId());
            if (prev != null && !prev.equals(flow.getTestFlowId())) {
                log.warn("开放入站 path 冲突: {} 已绑定 flowId={}，忽略 flowId={}",
                        key, prev, flow.getTestFlowId());
            }
        } catch (Exception e) {
            log.debug("索引开放 path 失败 flowId={}: {}", flow.getTestFlowId(), e.getMessage());
        }
    }
}
