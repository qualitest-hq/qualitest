package com.qualitest.flow.diagnose;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 测试流 HTTP 节点相对所绑 API 的一条语义告警。
 * <p>
 * 仅用于提示，不拦截保存或执行。
 */
public class HttpNodeApiHealthWarning {

    /**
     * 告警类型：
     * <ul>
     *   <li>API_MISSING — 未绑定或绑定的 API 已不存在</li>
     *   <li>ORPHAN_PARAM — 测值覆盖里的参数名在 API 请求参数中已不存在</li>
     *   <li>EXTRACT_PATH_MISSING — 响应抽取路径在 API 响应结构摘要里找不到</li>
     * </ul>
     */
    public String code;
    /** 所属测试流 id（批量诊断或单流检查时填入） */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public Long testFlowId;
    /** 测试流名称 */
    public String flowName;
    /** 画布节点 id */
    public String nodeId;
    /** 节点展示名 */
    public String nodeName;
    /** 节点绑定的 API id */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public Long testProjectApiId;
    /** 给人看的完整告警文案 */
    public String message;
    /** 辅助定位：孤儿参数名，或抽取表达式等 */
    public String detail;

    /**
     * 组装一条告警对象。
     */
    public static HttpNodeApiHealthWarning of(
            String code,
            String nodeId,
            String nodeName,
            Long testProjectApiId,
            String message,
            String detail) {
        HttpNodeApiHealthWarning w = new HttpNodeApiHealthWarning();
        w.code = code;
        w.nodeId = nodeId;
        w.nodeName = nodeName;
        w.testProjectApiId = testProjectApiId;
        w.message = message;
        w.detail = detail;
        return w;
    }
}
