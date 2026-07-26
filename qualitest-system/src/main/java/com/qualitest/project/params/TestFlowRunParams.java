package com.qualitest.project.params;

import com.qualitest.common.core.domain.BaseEntity;
import lombok.*;
import org.apache.ibatis.type.Alias;

import java.io.Serializable;

/**
 * 测试流运行 Params 对象
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
@Alias("TestFlowRunParams")
public class TestFlowRunParams extends BaseEntity implements Serializable {

    /**
     * 测试流ID
     */
    private Long testFlowId;

    /**
     * 测试项目环境ID
     */
    private Long testProjectEnvId;

    /**
     * 运行场景ID
     */
    private String runScenarioId;

    /**
     * 运行状态（running执行中 passed成功 failed失败 paused暂停 aborted中止 cancelled取消）
     */
    private String status;

    /**
     * 流程图指纹哈希
     */
    private String graphFingerprint;

    /**
     * 触发方式（manual手动 ci持续集成 schedule定时）
     */
    private String triggerType;

}
