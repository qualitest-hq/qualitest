package com.qualitest.flow.snapshot;

/**
 * 被测系统数据库快照适配器。
 * <p>
 * 质衡通过 HTTP 调用被测方暴露的快照接口，不直连被测库。
 * 实现类负责组请求、解析响应、处理超时与错误。
 */
public interface DbSnapshotAdapter {

    /**
     * 在被测系统创建一次数据快照（同步，阻塞直到完成或超时）。
     *
     * @param request 端点地址、表范围、关联标签、超时等
     * @return 快照标识与状态
     */
    SnapshotRef snapshot(SnapshotRequest request);

    /**
     * 将被测库还原到指定快照（同步，同一 snapshotId 多次调用应幂等）。
     *
     * @param resetEndpointBase 快照服务根地址
     * @param snapshotId        快照标识
     * @param timeoutMs         超时毫秒
     */
    void restore(String resetEndpointBase, String snapshotId, long timeoutMs);
}
