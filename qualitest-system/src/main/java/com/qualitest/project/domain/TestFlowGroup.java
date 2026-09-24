package com.qualitest.project.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.qualitest.common.core.domain.BaseEntity;
import lombok.*;
import org.apache.ibatis.type.Alias;

import java.io.Serial;
import java.util.Date;

/**
 * 测试流分组对象 test_flow_group
 *
 * @author qualitest
 * @date 2026-09-24
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("TestFlowGroup")
public class TestFlowGroup extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 测试流分组ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long flowGroupId;

    /**
     * 测试项目ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectId;

    /**
     * 父分组ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long parentId;

    /**
     * 祖级列表
     */
    private String ancestors;

    /**
     * 分组名称
     */
    private String groupName;

    /**
     * 显示顺序
     */
    private Integer sortNum;

    /**
     * 删除状态（0正常 1删除）
     */
    private Integer delStatus;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 修改时间
     */
    private Date updateTime;

}
