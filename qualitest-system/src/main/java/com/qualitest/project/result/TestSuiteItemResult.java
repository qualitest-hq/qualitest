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
 * 测试套件项 Result 对象
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
@Alias("TestSuiteItemResult")
public class TestSuiteItemResult implements Serializable {

    /** 套件项ID */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testSuiteItemId;

    /** 套件ID */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testSuiteId;

    /** 测试流ID */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testFlowId;

    /** 测试流名称（关联） */
    @Excel(name = "测试流名称")
    private String flowName;

    /** 场景ID（空表示默认场景） */
    private String runScenarioId;

    /** 排序 */
    @Excel(name = "排序")
    private Integer sortNum;

    /** 删除状态（0正常 1删除） */
    private Integer delStatus;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}
