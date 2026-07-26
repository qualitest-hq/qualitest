package com.qualitest.ai.result;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * 模型发现列表中的单条记录，标记远端模型与本地记录的对应关系。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiLlmDiscoveredModelItem implements Serializable {

    /**
     * 远端模型 ID
     */
    private String modelId;

    /**
     * 展示名称
     */
    private String displayName;

    /**
     * 同步状态：NEW=远端有本地无，EXISTING=已入库，ORPHAN=本地有远端已不存在
     */
    private String status;

    /**
     * 是否支持思考模式（0否 1是）
     */
    private Integer thinkingCapable;

    /**
     * 默认是否开启思考（0关 1开）
     */
    private Integer thinkingDefault;

    /**
     * 是否为模板推荐模型，同步界面可优先展示
     */
    private Boolean recommended;

    /**
     * 本地已入库时的模型主键；status 为 NEW 时为 null
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long aiLlmModelId;

    /**
     * 本地记录的启用状态（0禁用 1启用）；仅 EXISTING / ORPHAN 有值
     */
    private Integer enableStatus;

    /**
     * 本地记录的内置状态（0自定义 1内置）；仅 EXISTING / ORPHAN 有值
     */
    private Integer builtinStatus;
}
