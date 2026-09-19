package com.qualitest.project.result;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.apache.ibatis.type.Alias;
import java.util.Date;
import com.qualitest.common.annotation.Excel;

import java.io.Serializable;

/**
 * 测试项目 Result 对象
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
@Alias("TestProjectResult")
public class TestProjectResult implements Serializable {

    /**
     * 测试项目ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectId;

    /**
     * 项目名
     */
    @Excel(name = "项目名")
    private String projectName;

    /**
     * 素材变量
     */
    private String assetVariables;

    /**
     * 项目多端配置 JSON（鉴权头 + 响应约定 + 预制接口）；详情可读。
     */
    private String authConfig;

    /**
     * 是否允许 MCP 全自动写流：开启后持 Token 方可经 MCP 改图画布并跑流。
     */
    private Boolean mcpAutopilotEnabled;

    /**
     * 是否允许 MCP 导入接口。
     * 开启后，持项目 Token 可调用 import_apis 写入本项目接口库。
     * 默认关闭。本字段只控制导入接口，不控制改图画布与跑流。
     */
    private Boolean mcpImportApisEnabled;

    /**
     * 有 Profile 但预制 apis 全空时为 true，前端提示从项目模板添加。
     */
    private Boolean needsAuthTemplateHint;

    /**
     * 最新API同步时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "最新API同步时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date lastApiSyncTime;

    /**
     * 项目下未删除 API 条数，来源 test_project.api_count 冗余列。
     */
    @Excel(name = "API数量")
    private Integer apiCount;

    /**
     * 所有者ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long ownerId;

    /**
     * 所有者名称
     */
    @Excel(name = "所有者")
    private String ownerName;


}
