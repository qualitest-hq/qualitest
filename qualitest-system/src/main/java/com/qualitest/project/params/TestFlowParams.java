package com.qualitest.project.params;

import com.qualitest.common.core.domain.BaseEntity;
import lombok.*;
import org.apache.ibatis.type.Alias;

import java.io.Serializable;
import java.util.List;

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

    /**
     * 选中的测试流分组ID（服务端会展开为含子孙的 flowGroupIdList）
     */
    private Long flowGroupId;

    /**
     * 仅查询未分组的测试流
     */
    private Boolean ungroupedOnly;

    /**
     * 分组 id 列表（含选中组及其子孙；由 Service 填充）
     */
    private List<Long> flowGroupIdList;

}
