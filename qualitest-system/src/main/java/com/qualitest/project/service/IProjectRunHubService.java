package com.qualitest.project.service;

import com.qualitest.project.result.ProjectRunHubResult;

/**
 * 项目运行中心。
 * <p>
 * 聚合「活动缓存」（排队中 / 执行中）与「最近正式 Run 记录」（查库），
 * 并提供取消排队、移除活动行、等待排队开跑等能力。
 * 正式报告仍在 {@code test_flow_run} 等表，不因移除活动行而删除。
 */
public interface IProjectRunHubService {

    /**
     * 查询运行中心总览：当前并发模式、活动列表、最近正式记录。
     *
     * @param testProjectId 项目 id
     * @param recentLimit   最近记录条数上限；空则用默认值
     */
    ProjectRunHubResult getProjectRunHub(Long testProjectId, Integer recentLimit);

    /**
     * 取消仍在排队的项（尚未开跑）。
     * 会从环境队列移除，并标记 queueItemId 为已取消，打断等待方轮询。
     *
     * @return 是否找到并取消成功
     */
    boolean cancelQueuedRun(Long testProjectId, String queueItemId);

    /**
     * 从活动列表移除一行。
     * 若是排队行则等价于取消排队；若是执行中行只清缓存展示，不影响后台 Run 与库表报告。
     */
    void dismissHubActiveItem(Long testProjectId, String itemId);

    /**
     * 阻塞等待排队项开跑，直到拿到 runId、被取消或超时。
     *
     * @param queueItemId 触发回执里的排队 id
     * @param timeoutMs   最长等待毫秒
     * @return 开跑后的 runId；超时或取消返回 null
     */
    Long awaitQueuedRunStart(String queueItemId, long timeoutMs);

    /**
     * 非阻塞查询排队项是否已绑定 runId。
     * 已取消或尚未开跑返回 null。
     */
    Long peekQueuedRunId(String queueItemId);
}
