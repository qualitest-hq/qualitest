package com.qualitest.project.result;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.apache.ibatis.type.Alias;
import java.util.Date;
import java.math.BigDecimal;
import com.qualitest.common.annotation.Excel;

import java.io.Serializable;

/**
 * 测试流运行步骤 Result 对象
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
@Alias("TestFlowRunStepResult")
public class TestFlowRunStepResult implements Serializable {

    /**
     * 步骤ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testFlowRunStepId;

    /**
     * 运行ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testFlowRunId;

    /**
     * 步骤序号
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long stepIndex;

    /**
     * 节点ID
     */
    @Excel(name = "节点ID")
    private String nodeId;

    /**
     * 节点类型
     */
    @Excel(name = "节点类型")
    private String nodeType;

    /**
     * 节点名称
     */
    @Excel(name = "节点名称")
    private String nodeName;

    /**
     * 步骤状态（passed成功 failed失败 skipped未执行）
     */
    @Excel(name = "步骤状态", readConverterExp = "passed=成功,failed=失败,skipped=未执行")
    private String status;

    /**
     * 步骤耗时（毫秒）
     */
    @Excel(name = "步骤耗时", readConverterExp = "")
    private Long durationMs;

    /**
     * 入边ID
     */
    @Excel(name = "入边ID")
    private String edgeId;

    /**
     * 步骤详情JSON
     */
    @Excel(name = "步骤详情JSON")
    private String stepDetails;


}
