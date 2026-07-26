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
 * AI提示词模板对象 ai_prompt_template
 * 
 * @author qualitest
 * @date 2026-07-04
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("AiPromptTemplate")
public class AiPromptTemplate extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 提示词模板ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long aiPromptTemplateId;

    /**
     * 模板范围（platform平台 project项目）
     */
    private String templateScope;

    /**
     * 测试项目ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectId;

    /**
     * 会话场景
     */
    private String sessionScene;

    /**
     * 模板标题
     */
    private String templateTitle;

    /**
     * 模板说明
     */
    private String templateDescription;

    /**
     * 模板正文
     */
    private String templateContent;

    /**
     * 内置状态（0自定义 1内置）
     */
    private Integer builtinStatus;

    /**
     * 启用状态（0禁用 1启用）
     */
    private Integer enableStatus;

    /**
     * 排序
     */
    private Integer sortNum;

    /**
     * 删除状态（0正常 1删除）
     */
    private Integer delStatus;

}
