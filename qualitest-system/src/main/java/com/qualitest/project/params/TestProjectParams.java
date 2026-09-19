package com.qualitest.project.params;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.qualitest.common.core.domain.BaseEntity;
import lombok.*;
import org.apache.ibatis.type.Alias;

import java.io.Serializable;

/**
 * 测试项目 Params 对象
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
@Alias("TestProjectParams")
public class TestProjectParams extends BaseEntity implements Serializable {

    /**
     * 项目名
     */
    private String projectName;

    /**
     * 素材变量
     */
    private String assetVariables;

    /**
     * 所有者名称
     */
    private String ownerName;

    /**
     * 成员用户ID
     */
    private Long memberUserId;

}
