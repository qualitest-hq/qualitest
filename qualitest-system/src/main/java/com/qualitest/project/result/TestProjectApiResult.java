package com.qualitest.project.result;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.apache.ibatis.type.Alias;
import java.util.Date;
import java.math.BigDecimal;
import com.qualitest.common.annotation.Excel;

import java.io.Serializable;

/**
 * 测试项目API Result 对象
 *
 * @author qualitest
 * @since 2026-02-05
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("TestProjectApiResult")
public class TestProjectApiResult implements Serializable {

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
    @Excel(name = "API分组ID")
    private Long apiGroupId;

    /**
     * API状态
     */
    @Excel(name = "API状态")
    private String apiStatus;

    /**
     * API分组
     */
    @Excel(name = "API分组")
    private String apiGroup;

    /**
     * API名称
     */
    @Excel(name = "API名称")
    private String apiName;

    /**
     * API详细描述
     */
    @Excel(name = "API详细描述")
    private String apiDescription;

    /**
     * API路径
     */
    @Excel(name = "API路径")
    private String apiPath;

    /**
     * 协议类型
     */
    @Excel(name = "协议类型")
    private String protocolType;

    /**
     * 请求配置
     */
    @Excel(name = "请求配置")
    private String requestConfig;

    /**
     * 请求头配置
     */
    @Excel(name = "请求头配置")
    private String headers;

    /**
     * Cookie配置
     */
    @Excel(name = "Cookie配置")
    private String cookies;

    /**
     * 响应配置
     */
    @Excel(name = "响应配置")
    private String responseConfig;

    /**
     * 测试值层 JSON（参数默认值、body/响应示例等），详情接口按库中原值返回。
     * 同记录的 requestConfig、responseConfig 在详情读路径会叠加上本列内容后再返回前端。
     */
    private String testValueConfig;

    /**
     * 业务响应码白名单 JSON。
     * 典型字段：successValues（成功 code 列表）、source、updatedAt。
     * HTTP 执行时若 successValues 非空，优先于项目响应约定中的成功值。
     */
    private String bizCodeConfig;

    /**
     * 接口鉴权标签 JSON。
     * 表示该接口是否免登录、是否指定鉴权配置；详情与列表接口原样返回。
     */
    private String authConfig;

    /**
     * 前置操作脚本
     */
    @Excel(name = "前置操作脚本")
    private String preRequestScript;

    /**
     * 后置操作脚本
     */
    @Excel(name = "后置操作脚本")
    private String postRequestScript;

    /**
     * 最新同步时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "最新同步时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date lastSyncTime;


}
