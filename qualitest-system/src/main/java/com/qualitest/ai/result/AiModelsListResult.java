package com.qualitest.ai.result;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

/**
 * GET /ai/models 响应：厂商分组模型列表与默认选中项。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiModelsListResult implements Serializable {

    /** 设计面板默认选中：厂商 sort_num 最小组内、模型 sort_num 最小项 */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long defaultModelId;

    /** 按厂商 sort_num 排序的分组列表 */
    private List<AiModelVendorGroupResult> vendors;
}
