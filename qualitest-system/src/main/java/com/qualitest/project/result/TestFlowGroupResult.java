package com.qualitest.project.result;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.qualitest.common.annotation.Excel;
import lombok.*;
import org.apache.ibatis.type.Alias;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 测试流分组 Result 对象
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
@Alias("TestFlowGroupResult")
public class TestFlowGroupResult implements Serializable {

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
    @Excel(name = "祖级列表")
    private String ancestors;

    /**
     * 分组名称
     */
    @Excel(name = "分组名称")
    private String groupName;

    /**
     * 显示顺序
     */
    @Excel(name = "显示顺序")
    private Integer sortNum;

    /**
     * 删除状态（0正常 1删除）
     */
    private Integer delStatus;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    /**
     * 树节点展示名
     */
    private String label;

    /**
     * 子分组
     */
    @Builder.Default
    private List<TestFlowGroupResult> children = new ArrayList<>();

}
