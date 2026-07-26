package com.qualitest.flow.diagnose;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * API 批量导入后，对本批「更新成功」的 API 做项目内影响扫描的结果。
 * <p>
 * 放入导入接口返回体的 syncImpact 字段，供调用方展示受影响流与告警概况。
 */
public class ApiSyncImpactSummary {

    /** 参与本次扫描的变更 API 个数 */
    public int changedApiCount;

    /** 至少引用了一条变更 API 的测试流个数 */
    public int affectedFlowCount;

    /** 上述流上、落在变更 API 绑定节点上的语义告警总条数 */
    public int warningCount;

    /**
     * 受影响流明细（告警数降序，条数有上限）。
     * 每条含流名、引用节点数、告警数及少量告警样例文案。
     */
    public List<AffectedFlowSummary> flows = new ArrayList<>();

    /**
     * 全部受影响测试流 id（完整集合，不做条数截断）。
     * 仅服务端用来回写 api_health_*，不输出到 JSON 响应。
     */
    @JsonIgnore
    public Set<Long> allAffectedFlowIds = new LinkedHashSet<>();
}
