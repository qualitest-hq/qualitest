package com.qualitest.project.params;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.ibatis.type.Alias;

import java.io.Serializable;

/**
 * 从平台子流模板复制为项目内子流定义的请求参数。
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("CreateSubflowFromTemplateParams")
public class CreateSubflowFromTemplateParams implements Serializable {

    /** 目标测试项目 id */
    private Long testProjectId;

    /** 平台模板 id，如 tpl_oauth_client_credentials */
    private String templateId;

    /** 新建子流名称；空时取模板名称 */
    private String flowName;
}
