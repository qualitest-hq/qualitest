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
 * 测试套件项 Params 对象
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
@Alias("TestSuiteItemParams")
public class TestSuiteItemParams extends BaseEntity implements Serializable {

    /** 套件项ID */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testSuiteItemId;

    /** 套件ID */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testSuiteId;

    /** 测试流ID */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testFlowId;

    /** 场景ID（空表示默认场景） */
    private String runScenarioId;

    /** 删除状态（0正常 1删除） */
    private Integer delStatus;
}
