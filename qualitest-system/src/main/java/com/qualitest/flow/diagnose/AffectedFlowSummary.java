package com.qualitest.flow.diagnose;

import java.util.ArrayList;
import java.util.List;

/**
 * 导入影响汇总里的一条测试流记录。
 * 表示该流中有 HTTP 节点绑定了本次变更过的 API，可能需要人工或 AI 复查。
 */
public class AffectedFlowSummary {

    /** 测试流 id */
    public Long testFlowId;

    /** 测试流名称 */
    public String flowName;

    /** 本流中绑定到本次变更 API 的 HTTP 节点个数 */
    public int referenceNodeCount;

    /** 上述节点上的语义告警总条数（如孤儿测值、抽取路径失效等） */
    public int warningCount;

    /** 告警文案样例，条数有上限，便于快速扫一眼问题类型 */
    public List<String> sampleWarnings = new ArrayList<>();
}
