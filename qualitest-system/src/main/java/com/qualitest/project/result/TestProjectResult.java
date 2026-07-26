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
     * 响应约定 / 业务 Code 库（JSON 字符串）。
     * 项目详情与设置页读写；含 codePath、successValues、messagePath、dataPath。
     */
    private String responseConvention;

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
