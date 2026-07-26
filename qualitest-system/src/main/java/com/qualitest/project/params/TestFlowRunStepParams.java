package com.qualitest.project.params;

import com.qualitest.common.core.domain.BaseEntity;
import lombok.*;
import org.apache.ibatis.type.Alias;

import java.io.Serializable;

/**
 * 测试流运行步骤 Params 对象
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
@Alias("TestFlowRunStepParams")
public class TestFlowRunStepParams extends BaseEntity implements Serializable {

    /**
     * 运行ID
     */
    private Long testFlowRunId;

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
     * 入边ID
     */
    private String edgeId;

}
