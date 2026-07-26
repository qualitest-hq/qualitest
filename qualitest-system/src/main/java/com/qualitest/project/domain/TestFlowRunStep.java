package com.qualitest.project.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.ibatis.type.Alias;
import com.qualitest.common.core.domain.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serial;

/**
 * 测试流运行步骤对象 test_flow_run_step
 * 
 * @author qualitest
 * @date 2026-06-05
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("TestFlowRunStep")
public class TestFlowRunStep extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

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
    private String nodeId;

    /**
     * 节点类型
     */
    private String nodeType;

    /**
     * 节点名称
     */
    private String nodeName;

    /**
     * 步骤状态（passed成功 failed失败 skipped未执行）
     */
    private String status;

    /**
     * 步骤耗时（毫秒）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long durationMs;

    /**
     * 入边ID
     */
    private String edgeId;

    /**
     * 步骤详情JSON
     */
    private String stepDetails;

    /**
     * 删除状态（0正常 1删除）
     */
    private Integer delStatus;

}
