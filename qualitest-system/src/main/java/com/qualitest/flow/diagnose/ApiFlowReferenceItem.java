package com.qualitest.flow.diagnose;

/**
 * 某个测试流里，绑定了指定 API 的一个 HTTP 节点摘要。
 * <p>
 * 用于 API 详情「引用」列表：展示哪条流、哪个节点在用该接口。
 */
public class ApiFlowReferenceItem {

    /** 测试流 id */
    public Long testFlowId;
    /** 测试流名称 */
    public String flowName;
    /** 画布节点 id */
    public String nodeId;
    /** 节点展示名 */
    public String nodeName;
    /** 节点 callMode，一般为 project */
    public String callMode;
    /** 所查的 API id */
    public Long testProjectApiId;
}
