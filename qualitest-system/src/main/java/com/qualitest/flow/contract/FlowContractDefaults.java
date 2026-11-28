package com.qualitest.flow.contract;

import com.qualitest.flow.model.FlowNodeType;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphMeta;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.model.GraphSchemaVersions;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 流图落库与运行时共用的跨节点类型默认值。
 * <p>
 * 负责：返回节点缺省 from、入口缺省 maxSteps、图 meta.schemaVersion 补写，
 * 以及零次返回时的通用业务对象。HTTP 入口完整 fallbackOut、子流入口业务兜底
 * 由各自专属类提供，不放在本类。
 */
public final class FlowContractDefaults {

    /** 返回节点 data.from 为空时写入的路径 */
    public static final String DEFAULT_FROM = "flow.out";

    /** 入口节点未配置 data.maxSteps 时的默认单次 Run 步数上限 */
    public static final int DEFAULT_MAX_STEPS = 200;

    /** maxSteps 允许的最大值（裁剪上界） */
    public static final int MAX_STEPS_CEILING = 1000;

    /** 跑完未命中返回节点时，兜底业务对象里的 code */
    public static final String NO_RETURN_CODE = "NO_RETURN";

    /** 跑完未命中返回节点时，兜底业务对象里的 message */
    public static final String NO_RETURN_MESSAGE = "未执行到返回节点";

    private FlowContractDefaults() {
    }

    /**
     * 构造零次返回用的业务对象 {@code {code, message}}。
     * HTTP 入口把它放进 fallbackOut.body；子流入口可直接当作业务 fallbackOut。
     */
    public static Map<String, Object> defaultNoReturnBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", NO_RETURN_CODE);
        body.put("message", NO_RETURN_MESSAGE);
        return body;
    }

    /**
     * 落库前一次性补齐跨类型缺省字段。
     *
     * @param graph 待落库图，null 时直接返回 false
     * @return 是否改写了图内容
     */
    public static boolean applySaveDefaults(GraphJson graph) {
        if (graph == null) {
            return false;
        }
        boolean changed = false;
        changed |= fillMissingReturnFrom(graph);
        changed |= fillMissingEntryMaxSteps(graph);
        changed |= stampSchemaVersion(graph);
        return changed;
    }

    /**
     * 遍历返回节点：空白 from 写成 {@link #DEFAULT_FROM}；仅有首尾空白时改写成 trim 后的值。
     *
     * @return 是否改写了任一节点
     */
    public static boolean fillMissingReturnFrom(GraphJson graph) {
        if (graph == null || graph.getNodes() == null) {
            return false;
        }
        boolean changed = false;
        for (GraphNode node : graph.getNodes()) {
            if (node == null || !FlowNodeType.isReturn(node.getType())) {
                continue;
            }
            Map<String, Object> data = node.getData();
            if (data == null) {
                data = new LinkedHashMap<>();
                node.setData(data);
            }
            Object raw = data.get("from");
            String from = raw == null ? "" : String.valueOf(raw).trim();
            if (from.isEmpty()) {
                data.put("from", DEFAULT_FROM);
                changed = true;
                continue;
            }
            if (!from.equals(String.valueOf(raw))) {
                data.put("from", from);
                changed = true;
            }
        }
        return changed;
    }

    /**
     * 遍历入口节点：缺少 data.maxSteps 时写入 {@link #DEFAULT_MAX_STEPS}。
     *
     * @return 是否改写了任一节点
     */
    public static boolean fillMissingEntryMaxSteps(GraphJson graph) {
        if (graph == null || graph.getNodes() == null) {
            return false;
        }
        boolean changed = false;
        for (GraphNode node : graph.getNodes()) {
            if (node == null || !FlowNodeType.isEntry(node.getType())) {
                continue;
            }
            Map<String, Object> data = node.getData();
            if (data == null) {
                data = new LinkedHashMap<>();
                node.setData(data);
            }
            if (data.get("maxSteps") == null) {
                data.put("maxSteps", DEFAULT_MAX_STEPS);
                changed = true;
            }
        }
        return changed;
    }

    /**
     * 图 meta 缺少 schemaVersion 时写入当前平台版本号；已有值不覆盖。
     *
     * @return 是否新写入了 schemaVersion
     */
    public static boolean stampSchemaVersion(GraphJson graph) {
        if (graph == null) {
            return false;
        }
        GraphMeta meta = graph.getMeta();
        if (meta == null) {
            meta = new GraphMeta();
            graph.setMeta(meta);
        }
        if (meta.getSchemaVersion() == null) {
            meta.setSchemaVersion(GraphSchemaVersions.CURRENT);
            return true;
        }
        return false;
    }
}
