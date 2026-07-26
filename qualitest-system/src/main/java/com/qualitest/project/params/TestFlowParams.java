package com.qualitest.project.params;

import com.qualitest.common.core.domain.BaseEntity;
import lombok.*;
import org.apache.ibatis.type.Alias;

import java.io.Serializable;

/**
 * 测试流 Params 对象
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
@Alias("TestFlowParams")
public class TestFlowParams extends BaseEntity implements Serializable {

    /**
     * 测试项目ID
     */
    private Long testProjectId;

    /**
     * 测试流名称
     */
    private String flowName;

}
