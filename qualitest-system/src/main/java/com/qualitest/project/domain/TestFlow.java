package com.qualitest.project.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.ibatis.type.Alias;
import com.qualitest.common.core.domain.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serial;
import java.util.Date;

/**
 * 测试流对象 test_flow
 * 
 * @author qualitest
 * @date 2026-06-05
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("TestFlow")
public class TestFlow extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 测试流ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testFlowId;

    /**
     * 测试项目ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectId;

    /**
     * 测试流名称
     */
    private String flowName;

    /**
     * 测试流说明
     */
    private String flowDescription;

    /**
     * 流程图JSON
     */
    private String graphJson;

    /**
     * API 语义健康告警条数。
     * 静态对照图上 HTTP 节点与当前 API 定义后得到；0 表示当前无此类告警。
     */
    private Integer apiHealthWarningCount;

    /**
     * 最近一次把 API 语义健康结果写入本行的时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date apiHealthCheckedAt;

    /**
     * 告警类型摘要：去重后的 code 逗号拼接，例如 API_MISSING,ORPHAN_PARAM。
     * 过长时截断存储。
     */
    private String apiHealthWarningCodes;

    /**
     * 删除状态（0正常 1删除）
     */
    private Integer delStatus;

}
