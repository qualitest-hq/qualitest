package com.qualitest.ai.result;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 厂商模型发现结果，对比远端可用模型与本地已入库记录。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiLlmDiscoverModelsResult implements Serializable {

    /**
     * 厂商主键
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long vendorId;

    /**
     * 远端模型列表拉取时间（UTC）
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Date fetchedAt;

    /**
     * 是否来自短期缓存，避免频繁请求上游
     */
    private Boolean fromCache;

    /**
     * 发现到的模型条目列表
     */
    private List<AiLlmDiscoveredModelItem> models;
}
