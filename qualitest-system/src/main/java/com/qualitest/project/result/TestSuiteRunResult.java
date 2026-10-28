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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 测试套件运行 Result 对象
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
@Alias("TestSuiteRunResult")
public class TestSuiteRunResult implements Serializable {

    /** 套件运行ID */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testSuiteRunId;

    /** 测试项目ID */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectId;

    /** 套件ID（空表示临时多选） */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testSuiteId;

    /** 套件名称快照 */
    @Excel(name = "套件名称快照")
    private String suiteNameSnapshot;

    /** 状态 */
    @Excel(name = "状态")
    private String status;

    /** 非通过即停（1是 0否） */
    private Integer stopOnNonPass;

    /** 统一环境覆盖 */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectEnvId;

    /** 触发方式 */
    private String triggerType;

    /** 通过数 */
    private Integer passedCount;

    /** 失败数 */
    private Integer failedCount;

    /** 暂停数 */
    private Integer pausedCount;

    /** 跳过数 */
    private Integer skippedCount;

    /** 其他数 */
    private Integer otherCount;

    /** 开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startedAt;

    /** 结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date finishedAt;

    /** 操作者用户ID */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long operatorUserId;

    /** 删除状态（0正常 1删除） */
    private Integer delStatus;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    /** 运行项列表（详情） */
    @Builder.Default
    private List<TestSuiteRunItemResult> items = new ArrayList<>();
}
