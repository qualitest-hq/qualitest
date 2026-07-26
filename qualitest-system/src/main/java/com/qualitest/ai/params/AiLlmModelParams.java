package com.qualitest.ai.params;

import com.qualitest.common.core.domain.BaseEntity;
import lombok.*;
import org.apache.ibatis.type.Alias;

import java.io.Serializable;

/**
 * AI 模型 Params 对象
 *
 * @author qualitest
 * @since 2026-06-15
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("AiLlmModelParams")
public class AiLlmModelParams extends BaseEntity implements Serializable {

    /**
     * 厂商ID
     */
    private Long aiLlmVendorId;

    /**
     * 模型名
     */
    private String modelName;

    /**
     * 启用状态（0禁用 1启用）
     */
    private Integer enableStatus;

}
