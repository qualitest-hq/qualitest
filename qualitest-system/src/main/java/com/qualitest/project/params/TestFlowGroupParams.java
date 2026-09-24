package com.qualitest.project.params;

import com.qualitest.common.core.domain.BaseEntity;
import lombok.*;
import org.apache.ibatis.type.Alias;

import java.io.Serializable;

/**
 * 测试流分组 Params 对象
 *
 * @author qualitest
 * @since 2026-09-24
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("TestFlowGroupParams")
public class TestFlowGroupParams extends BaseEntity implements Serializable {

    /**
     * 测试项目ID
     */
    private Long testProjectId;

    /**
     * 父分组ID
     */
    private Long parentId;

    /**
     * 祖级列表
     */
    private String ancestors;

    /**
     * 分组名称
     */
    private String groupName;

}
