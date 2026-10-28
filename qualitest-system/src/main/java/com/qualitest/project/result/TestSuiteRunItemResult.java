package com.qualitest.project.result;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.qualitest.common.annotation.Excel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.ibatis.type.Alias;

import java.io.Serializable;
import java.util.Date;

/**
 * 测试套件运行项 Result 对象
 *
 * @author qualitest
 * @since 2026-10-28
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("TestSuiteRunItemResult")
public class TestSuiteRunItemResult implements Serializable {

    /** 套件运行项ID */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testSuiteRunItemId;

    /** 套件运行ID */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testSuiteRunId;

    /** 排序 */
    @Excel(name = "排序")
    private Integer sortNum;

    /** 测试流ID */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testFlowId;

    /** 流名称快照 */
    @Excel(name = "流名称快照")
    private String flowNameSnapshot;

    /** 场景ID */
    private String runScenarioId;

    /** 场景名称快照 */
    private String scenarioNameSnapshot;

    /** 关联正式 Run ID */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testFlowRunId;

    /** 项状态 */
    @Excel(name = "项状态")
    private String status;

    /** 错误信息 */
    private String errorMessage;

    /** 耗时毫秒 */
    private Long durationMs;

    /** 删除状态（0正常 1删除） */
    private Integer delStatus;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}
