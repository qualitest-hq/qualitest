package com.qualitest.project.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.qualitest.common.core.domain.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.ibatis.type.Alias;

import java.io.Serial;
import java.util.Date;

/**
 * 测试套件运行项对象 test_suite_run_item
 *
 * @author qualitest
 * @date 2026-10-28
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("TestSuiteRunItem")
public class TestSuiteRunItem extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 套件运行项ID */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testSuiteRunItemId;

    /** 套件运行ID */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testSuiteRunId;

    /** 排序 */
    private Integer sortNum;

    /** 测试流ID */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testFlowId;

    /** 流名称快照 */
    private String flowNameSnapshot;

    /** 场景ID */
    private String runScenarioId;

    /** 场景名称快照 */
    private String scenarioNameSnapshot;

    /** 关联正式 Run ID */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testFlowRunId;

    /** 项状态 */
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
