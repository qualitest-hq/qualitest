package com.qualitest.ai.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.ibatis.type.Alias;
import com.qualitest.common.core.domain.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serial;

/**
 * AI 模型对象 ai_llm_model
 * 
 * @author qualitest
 * @date 2026-06-15
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("AiLlmModel")
public class AiLlmModel extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 模型ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long aiLlmModelId;

    /**
     * 厂商ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long aiLlmVendorId;

    /**
     * 模型名（上游模型 ID）
     */
    private String modelName;

    /**
     * 展示名，为空时回退使用 model_name
     */
    private String displayName;

    /**
     * 内置状态（0自定义 1内置）；内置记录由系统预置，不可删除
     */
    private Integer builtinStatus;

    /**
     * 启用状态（0禁用 1启用）
     */
    private Integer enableStatus;

    /**
     * 是否支持思考（0否 1是）
     */
    private Integer thinkingCapable;

    /**
     * 默认是否开启思考（0关 1开）
     */
    private Integer thinkingDefault;

    /**
     * 思考模式 token 预算上限；为空时使用全局默认值
     */
    private Integer thinkingBudgetTokens;

    /**
     * 排序
     */
    private Integer sortNum;

    /**
     * 删除状态（0正常 1删除）
     */
    private Integer delStatus;

}
