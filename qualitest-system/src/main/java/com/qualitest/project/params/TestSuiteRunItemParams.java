package com.qualitest.project.params;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.qualitest.common.core.domain.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.ibatis.type.Alias;

import java.io.Serializable;

/**
 * 测试套件运行项 Params 对象
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
@Alias("TestSuiteRunItemParams")
public class TestSuiteRunItemParams extends BaseEntity implements Serializable {

    /** 套件运行项ID */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testSuiteRunItemId;

    /** 套件运行ID */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testSuiteRunId;

    /** 测试流ID */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testFlowId;

    /** 状态 */
    private String status;

    /** 删除状态（0正常 1删除） */
    private Integer delStatus;
}
