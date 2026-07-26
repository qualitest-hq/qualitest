package com.qualitest.project.result;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.apache.ibatis.type.Alias;
import java.util.Date;
import java.math.BigDecimal;
import com.qualitest.common.annotation.Excel;

import java.io.Serializable;

/**
 * 测试项目API分组 Result 对象
 *
 * @author qualitest
 * @since 2026-02-05
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("TestProjectApiGroupResult")
public class TestProjectApiGroupResult implements Serializable {

    /**
     * API分组ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long apiGroupId;

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


}
