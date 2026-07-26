package com.qualitest.ai.llm.discovery;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 从上游 API 拉取到的模型原始信息，尚未与本地记录合并。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscoveredModel {

    /**
     * 上游模型 ID
     */
    private String modelId;

    /**
     * 展示名称
     */
    private String displayName;

    /**
     * 上游返回的归属方标识（如 OpenAI 的 owned_by）
     */
    private String ownedBy;

    /**
     * 上游记录的模型创建时间
     */
    private Instant remoteCreatedAt;

    /**
     * 是否支持思考模式（0否 1是）
     */
    private Integer thinkingCapable;

    /**
     * 默认是否开启思考（0关 1开）
     */
    private Integer thinkingDefault;
}
