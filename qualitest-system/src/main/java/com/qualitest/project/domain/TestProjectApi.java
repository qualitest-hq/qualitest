package com.qualitest.project.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.qualitest.common.core.domain.BaseEntity;
import lombok.*;
import org.apache.ibatis.type.Alias;

import java.io.Serial;
import java.util.Date;

/**
 * 测试项目API对象 test_project_api
 *
 * @author qualitest
 * @date 2026-02-05
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("TestProjectApi")
public class TestProjectApi extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 测试项目API ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectApiId;

    /**
     * 测试项目ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectId;

    /**
     * API分组ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long apiGroupId;

    /**
     * API状态
     */
    private String apiStatus;

    /**
     * API分组
     */
    private String apiGroup;

    /**
     * API名称
     */
    private String apiName;

    /**
     * API详细描述
     */
    private String apiDescription;

    /**
     * API路径
     */
    private String apiPath;

    /**
     * 协议类型
     */
    private String protocolType;

    /**
     * 请求配置
     */
    private String requestConfig;

    /**
     * 请求头配置
     */
    private String headers;

    /**
     * Cookie配置
     */
    private String cookies;

    /**
     * 响应配置
     */
    private String responseConfig;

    /**
     * 测试值层 JSON：与 request/response 结构分列存储。
     * 典型内容：paramDefaults（参数默认值）、bodyExample（请求体示例）、
     * response.examplesById（按响应 id 存的 example）、removedParams（结构已删参数名归档）。
     * 导入更新时按字段合并写入，不整段清空用户已填内容。
     */
    private String testValueConfig;

    /**
     * 业务响应码白名单 JSON。
     * 典型字段：successValues、source、updatedAt。
     * 由 Run 暂停确认或设置页维护；插件/API 批量导入不读写本列。
     */
    private String bizCodeConfig;

    /**
     * 前置操作脚本
     */
    private String preRequestScript;

    /**
     * 后置操作脚本
     */
    private String postRequestScript;

    /**
     * 最新同步时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastSyncTime;

    /**
     * 删除状态（0正常 1删除）
     */
    private Integer delStatus;

}
