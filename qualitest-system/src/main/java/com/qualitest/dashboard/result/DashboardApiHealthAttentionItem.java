package com.qualitest.dashboard.result;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.ibatis.type.Alias;

import java.io.Serializable;
import java.util.Date;

/**
 * 接口变更待关注列表中的一条测试流。
 * <p>
 * 数据来自 test_flow 已落库的 api_health_* 字段，查询时不再解析 graph_json。
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("DashboardApiHealthAttentionItem")
public class DashboardApiHealthAttentionItem implements Serializable {

    /** 所属项目 id */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectId;

    /** 项目名称 */
    private String projectName;

    /** 测试流 id */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testFlowId;

    /** 测试流名称 */
    private String flowName;

    /** API 语义健康告警条数 */
    private Integer warningCount;

    /** 告警类型摘要，如 API_MISSING,ORPHAN_PARAM */
    private String warningCodes;

    /** 最近一次健康检查落库时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date checkedAt;
}
