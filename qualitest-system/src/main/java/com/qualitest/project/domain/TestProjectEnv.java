package com.qualitest.project.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.qualitest.common.core.domain.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.ibatis.type.Alias;

import java.io.Serial;

/**
 * 测试项目环境对象 test_project_env
 * 
 * @author qualitest
 * @date 2026-02-05
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("TestProjectEnv")
public class TestProjectEnv extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 测试项目环境ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectEnvId;

    /**
     * 测试项目ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectId;

    /**
     * 用户ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long userId;

    /**
     * 共享状态（share 共享，private 私有）
     */
    private String shareStatus;

    /**
     * 环境标识颜色
     */
    private String envColor;

    /**
     * 环境名称
     */
    private String envName;

    /**
     * 环境URL
     */
    private String envUrl;

    /**
     * 环境变量
     */
    private String envVariables;

    /**
     * 是否允许在正式 Run 中还原被测数据。
     * 0：禁止打 checkpoint 与 restore；1：允许（须被测环境已暴露快照接口）。
     * 生产环境应置 0。
     */
    private Integer allowDestructiveReset;

    /**
     * 排序号
     */
    private Integer sortNum;

    /**
     * 删除状态（0正常 1删除）
     */
    private Integer delStatus;

}
