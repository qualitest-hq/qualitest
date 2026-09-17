package com.qualitest.flow.sync;

import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试流 / 项目资源被外部写入后的通知事件。
 * 推给已打开画布的浏览器；默认不带整图，可选带节点/边增量片段。
 */
@Getter
@Builder
public class FlowExternalChangeEvent {

    /** 事件类型：图提交、素材变更、鉴权变更、环境变更、开跑、流元数据、新建流等 */
    private final String type;

    /** 相关测试流 id（图 / 开跑类） */
    private final Long testFlowId;

    /** 相关项目 id（素材 / 鉴权 / 环境类） */
    private final Long testProjectId;

    /** 写入来源：mcp / web-autopilot / web-save / web-ui */
    private final String source;

    /** 图更新时间，前端用于去重、避免重复灌图 */
    private final String updateTime;

    /** 开跑事件携带的 runId */
    private final Long runId;

    /** 素材变更涉及的变量 key */
    @Builder.Default
    private final List<String> keys = Collections.emptyList();

    /** 本批变更或删除的节点 id，供高亮 */
    @Builder.Default
    private final List<String> changedNodeIds = Collections.emptyList();

    /** 落盘图中对应节点的 JSON 片段 */
    @Builder.Default
    private final List<Object> nodePatches = Collections.emptyList();

    /** 相关边的 JSON 片段 */
    @Builder.Default
    private final List<Object> edgePatches = Collections.emptyList();

    /** 本批删除的节点 id */
    @Builder.Default
    private final List<String> deletedNodeIds = Collections.emptyList();

    /** 本批删除的边 id */
    @Builder.Default
    private final List<String> deletedEdgeIds = Collections.emptyList();

    /** 预留扩展字段 */
    @Builder.Default
    private final Map<String, Object> extras = Collections.emptyMap();

    /** 画布图已写入库 */
    public static final String TYPE_GRAPH_COMMITTED = "graphCommitted";
    /** 项目素材变量已变更 */
    public static final String TYPE_ASSET_VARIABLES_CHANGED = "assetVariablesChanged";
    /** 项目鉴权配置已变更 */
    public static final String TYPE_AUTH_CONFIG_CHANGED = "authConfigChanged";
    /** 项目环境列表已变更 */
    public static final String TYPE_PROJECT_ENVS_CHANGED = "projectEnvsChanged";
    /** 已触发一次 Run */
    public static final String TYPE_RUN_STARTED = "runStarted";
    /** 流名称等元数据变更 */
    public static final String TYPE_FLOW_META_CHANGED = "flowMetaChanged";
    /** 新建测试流 */
    public static final String TYPE_FLOW_CREATED = "flowCreated";

    public static final String SOURCE_MCP = "mcp";
    public static final String SOURCE_WEB_AUTOPILOT = "web-autopilot";
    public static final String SOURCE_WEB_SAVE = "web-save";
    public static final String SOURCE_WEB_UI = "web-ui";

    /**
     * 组装推送给客户端的 JSON Map。
     *
     * @param stringifyIds true：id 转成字符串（浏览器 SSE）；false：保留 Long（Redis 扇出）
     */
    public Map<String, Object> toPayloadMap(boolean stringifyIds) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", type);
        putId(map, "testFlowId", testFlowId, stringifyIds);
        putId(map, "testProjectId", testProjectId, stringifyIds);
        if (source != null) {
            map.put("source", source);
        }
        if (updateTime != null) {
            map.put("updateTime", updateTime);
        }
        putId(map, "runId", runId, stringifyIds);
        putIfNonEmpty(map, "keys", keys);
        putIfNonEmpty(map, "changedNodeIds", changedNodeIds);
        putIfNonEmpty(map, "nodePatches", nodePatches);
        putIfNonEmpty(map, "edgePatches", edgePatches);
        putIfNonEmpty(map, "deletedNodeIds", deletedNodeIds);
        putIfNonEmpty(map, "deletedEdgeIds", deletedEdgeIds);
        return map;
    }

    private static void putId(Map<String, Object> map, String key, Long id, boolean stringify) {
        if (id == null) {
            return;
        }
        map.put(key, stringify ? String.valueOf(id) : id);
    }

    private static void putIfNonEmpty(Map<String, Object> map, String key, List<?> values) {
        if (values != null && !values.isEmpty()) {
            map.put(key, values);
        }
    }
}
