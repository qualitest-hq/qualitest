package com.qualitest.project.result;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.apache.ibatis.type.Alias;
import java.util.Date;
import java.util.List;
import java.math.BigDecimal;
import com.qualitest.common.annotation.Excel;

import java.io.Serializable;

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
     * 图 schema 是否可升级（由服务层根据 graphJson 计算，非库表字段）
     */
    private Boolean upgradeAvailable;

    /**
     * 当前图 schema 版本（缺失视为 1）
     */
    private Integer upgradeFromVersion;

    /**
     * 升级目标 schema 版本
     */
    private Integer upgradeToVersion;

    /**
     * 升级变更摘要，供确认弹窗展示
     */
    private List<String> upgradeSummary;


}
