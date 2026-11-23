package com.qualitest.flow.openhttp;

import com.qualitest.flow.model.GraphJson;

/**
 * 一条开放 HTTP 入站路由。
 * <p>
 * 同项目内以 method + path 唯一定位到某张流图及其 http-entry 节点；
 * envId 用于开跑时装载环境变量，可为 null（无环境则 env 为空）。
 *
 * @param testProjectId 所属测试项目
 * @param testFlowId    命中的测试流主键
 * @param entryNodeId   图上 http-entry 节点 id，作为开跑起点
 * @param method        已规范化的 HTTP 方法（大写）
 * @param path          已规范化的路径（以 / 开头、无尾斜杠，根为 /）
 * @param envId         入口配置的环境 id，可空
 * @param graph         解析后的整图快照
 */
public record OpenHttpRoute(
        Long testProjectId,
        Long testFlowId,
        String entryNodeId,
        String method,
        String path,
        Long envId,
        GraphJson graph
) {
}
