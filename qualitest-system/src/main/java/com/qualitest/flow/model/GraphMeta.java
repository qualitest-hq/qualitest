package com.qualitest.flow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 图元数据：画布视口/排版等。
 * <p>
 * 可运行场景列表保存在 scenario-entry 节点的 data.scenarios，本类不承载场景配置。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphMeta {

    /** 图 JSON 结构版本号，供解析与迁移识别 */
    private Integer schemaVersion;

    /**
     * 画布视口；默认 {@code { x:40, y:40, zoom:1 }}
     */
    private GraphViewport viewport;

    /**
     * 这条流的排版方式，只记方式，不记连线拐点。
     * default：从左往右分层并折行。
     * horizontal：横向分层，不折行。
     * horizontal-wrap：横向分层并折行。
     * vertical：自上而下分层。
     */
    private String layout;

    /**
     * 唯一开始节点 id 缓存；执行仍以拓扑入度为准，此字段仅辅助画布
     */
    private String startNodeId;
}