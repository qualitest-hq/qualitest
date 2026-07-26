package com.qualitest.project.params;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.qualitest.common.core.domain.BaseEntity;
import lombok.*;
import org.apache.ibatis.type.Alias;

import java.io.Serializable;

/**
 * 测试项目API Params 对象
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
@Alias("TestProjectApiParams")
public class TestProjectApiParams extends BaseEntity implements Serializable {

    /**
     * 测试项目ID
     */
    private Long testProjectId;

    /**
     * API分组ID
     */
    private Long apiGroupId;

    /**
     * API状态
     */
    private String apiStatus;

    /**
     * API名称
     */
    private String apiName;

    /**
     * API路径
     */
    private String apiPath;

    /**
     * 协议类型
     */
    private String protocolType;

}
