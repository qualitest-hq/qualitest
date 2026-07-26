package com.qualitest.project.params;

import com.qualitest.common.core.domain.BaseEntity;
import lombok.*;
import org.apache.ibatis.type.Alias;

import java.io.Serializable;

/**
 * 测试项目环境 Params 对象
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
@Alias("TestProjectEnvParams")
public class TestProjectEnvParams extends BaseEntity implements Serializable {

    /**
     * 测试项目ID
     */
    private Long testProjectId;

    /**
     * 用户ID
     */
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
     * 是否允许在正式 Run 中还原被测数据（0 否，1 是）
     */
    private Integer allowDestructiveReset;

    /**
     * 排序号
     */
    private Integer sortNum;

}
