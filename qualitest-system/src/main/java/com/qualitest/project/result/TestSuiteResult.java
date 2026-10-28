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
 * 测试套件 Result 对象
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
@Alias("TestSuiteResult")
public class TestSuiteResult implements Serializable {

    /** 套件ID */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testSuiteId;

    /** 测试项目ID */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectId;

    /** 套件名称 */
    @Excel(name = "套件名称")
    private String suiteName;

    /** 套件说明 */
    @Excel(name = "套件说明")
    private String suiteDescription;

    /** 非通过即停（1是 0否） */
    private Integer stopOnNonPass;

    /** 删除状态（0正常 1删除） */
    private Integer delStatus;

    /** 项数量（列表聚合） */
    private Integer itemCount;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    /** 套件项列表（详情） */
    @Builder.Default
    private List<TestSuiteItemResult> items = new ArrayList<>();
}
