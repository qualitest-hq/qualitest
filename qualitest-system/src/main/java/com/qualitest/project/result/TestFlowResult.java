package com.qualitest.project.result;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.apache.ibatis.type.Alias;
import com.qualitest.common.annotation.Excel;

import java.io.Serializable;
import java.util.Date;

/**
 * 测试流 Result 对象
 *
 * @author qualitest
 * @since 2026-06-05
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("TestFlowResult")
public class TestFlowResult implements Serializable {

    /**
     * 测试流ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testFlowId;

    /**
     * 测试项目ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectId;

    /**
     * 测试流名称
     */
    @Excel(name = "测试流名称")
    private String flowName;

    /**
     * 测试流说明
     */
    @Excel(name = "测试流说明")
    private String flowDescription;

    /**
     * 流程图JSON
     */
    @Excel(name = "流程图JSON")
    private String graphJson;

    /**
     * API 语义健康告警条数
     */
    private Integer apiHealthWarningCount;

    /**
     * 最近一次写入 API 语义健康结果的时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date apiHealthCheckedAt;

    /**
     * 告警类型摘要
     */
    private String apiHealthWarningCodes;

    /**
     * 删除状态（0正常 1删除）
     */
    private Integer delStatus;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

}
