package com.qualitest.ai.result;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * 模型下拉选项，用于会话创建等场景选择可用模型。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiModelOptionResult implements Serializable {

    /**
     * 模型主键
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long aiLlmModelId;

    /**
     * 上游模型 ID，调用 LLM API 时作为 model 参数
     */
    private String modelName;

    /**
     * 界面展示名称；优先取 display_name，为空时回退 modelName
     */
    private String displayName;

    /**
     * 排序权重，值越小越靠前
     */
    private Integer sortNum;

    /**
     * 是否支持思考模式
     */
    private Boolean thinkingCapable;

    /**
     * 选中该模型时默认是否开启思考
     */
    private Boolean thinkingDefault;
}
