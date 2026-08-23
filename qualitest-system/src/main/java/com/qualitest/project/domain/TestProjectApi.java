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
     * 测值配置 JSON：调试默认参数、请求体示例、按响应 id 存的示例等。
     * 与 request_config / response_config 分列；保存时从结构里拆出测值写入本列，读详情再叠回去展示。
     */
    private String testValueConfig;

    /**
     * 业务响应码白名单 JSON。
     * 典型字段：successValues、source、updatedAt。
     * 由 Run 暂停确认或设置页维护；插件/API 批量导入不读写本列。
     */
    private String bizCodeConfig;

    /**
     * 造流设计提示 JSON。
     * 典型字段：hints（短文本列表）、source、updatedAt。
     * 人机均可维护；插件/API 批量导入不读写本列。
     */
    private String designHints;

    /**
     * 接口鉴权标签 JSON。
     * 典型内容：mode（none 可不登录 / inherit 需登录 / override 自定义头），
     * 以及可选的 authProfileId（指定用哪套项目鉴权配置）。
     * 由接口导入写入；列表与详情原样返回给前端。
     */
    private String authConfig;

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
