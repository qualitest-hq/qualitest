package com.qualitest.ai.params;

import com.qualitest.common.core.domain.BaseEntity;
import lombok.*;
import org.apache.ibatis.type.Alias;

import java.io.Serializable;

/**
 * AI提示词模板 Params 对象
 *
 * @author qualitest
 * @since 2026-07-04
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("AiPromptTemplateParams")
public class AiPromptTemplateParams extends BaseEntity implements Serializable {

    /**
     * 模板范围（platform平台 project项目）
     */
    private String templateScope;

    /**
     * 测试项目ID
     */
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
     * 内置状态（0自定义 1内置）
     */
    private Integer builtinStatus;

    /**
     * 启用状态（0禁用 1启用）
     */
    private Integer enableStatus;

}
