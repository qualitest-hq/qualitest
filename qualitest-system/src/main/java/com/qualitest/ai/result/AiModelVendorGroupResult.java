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
 * 按厂商分组的模型列表（一组 option-group）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiModelVendorGroupResult implements Serializable {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long aiLlmVendorId;

    /** 厂商展示名 */
    private String vendorName;
    private Integer sortNum;
    /** 该厂商下启用的模型列表 */
    private List<AiModelOptionResult> models;
}
