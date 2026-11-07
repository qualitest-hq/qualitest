package com.qualitest.project.service.impl;

import cn.hutool.core.util.StrUtil;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.common.utils.SecurityUtils;
import com.qualitest.project.domain.TestProject;
import com.qualitest.project.enums.RunConcurrencyMode;
import com.qualitest.project.enums.TestProjectMemberPerm;
import com.qualitest.project.mapper.TestProjectMapper;
import com.qualitest.project.result.ProjectRunHubItemResult;
import com.qualitest.project.result.ProjectRunHubResult;
import com.qualitest.project.runhub.EnvRunConcurrencyService;
import com.qualitest.project.runhub.EnvRunQueueItem;
import com.qualitest.project.service.IProjectRunHubService;
import com.qualitest.project.service.ITestFlowRunService;
import com.qualitest.project.service.ITestProjectMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 项目运行中心实现。
 * <p>
 * 活动区读 Redis；最近记录查 {@code test_flow_run}；
 * 取消排队 / 等待开跑走环境并发服务上的 queueItemId 绑定。
 */
@Service
@RequiredArgsConstructor
public class ProjectRunHubServiceImpl implements IProjectRunHubService {

    private final EnvRunConcurrencyService envRunConcurrencyService;
    private final ITestFlowRunService testFlowRunService;
    private final ITestProjectMemberService testProjectMemberService;
    private final TestProjectMapper testProjectMapper;

    /**
     * 组装运行中心：并发模式 + 活动缓存 + 最近正式 Run。
     * 需要项目流查看权限。
     */
    @Override
    public ProjectRunHubResult getProjectRunHub(Long testProjectId, Integer recentLimit) {
        if (testProjectId == null) {
            throw new ServiceException("testProjectId 不能为空");
        }
        testProjectMemberService.requireCapability(
                testProjectId, SecurityUtils.getUserId(), TestProjectMemberPerm.FLOW_VIEW);
        TestProject project = testProjectMapper.selectTestProjectById(testProjectId);
        int limit = recentLimit == null ? 30 : Math.max(1, Math.min(recentLimit, 100));
        List<ProjectRunHubItemResult> active = envRunConcurrencyService.listActive(testProjectId);
        List<ProjectRunHubItemResult> recent = listRecentHubItems(testProjectId, limit);
        String mode = project != null ? project.getRunConcurrencyMode() : null;
        return ProjectRunHubResult.builder()
                .runConcurrencyMode(RunConcurrencyMode.fromCode(mode).getCode())
                .active(active)
                .recent(recent)
                .build();
    }

    /**
     * 取消排队：定位环境 → 从队列摘掉 → 标记 queueItemId 为 cancelled。
     * 需要项目跑流权限。
     */
    @Override
    public boolean cancelQueuedRun(Long testProjectId, String queueItemId) {
        if (testProjectId == null || StrUtil.isBlank(queueItemId)) {
            return false;
        }
        testProjectMemberService.requireCapability(
                testProjectId, SecurityUtils.getUserId(), TestProjectMemberPerm.FLOW_RUN);
        String bareId = stripQueuePrefix(queueItemId);
        Long envId = findQueuedEnvId(testProjectId, bareId, queueItemId);
        if (envId == null) {
            return false;
        }
        EnvRunQueueItem removed = envRunConcurrencyService.cancelQueued(testProjectId, envId, bareId);
        if (removed != null) {
            envRunConcurrencyService.bindQueueItemCancelled(bareId);
            return true;
        }
        return false;
    }

    /**
     * 活动区移除：排队行走取消排队；执行中行只删缓存展示。
     */
    @Override
    public void dismissHubActiveItem(Long testProjectId, String itemId) {
        if (testProjectId == null || StrUtil.isBlank(itemId)) {
            return;
        }
        testProjectMemberService.requireCapability(
                testProjectId, SecurityUtils.getUserId(), TestProjectMemberPerm.FLOW_VIEW);
        if (itemId.startsWith("queue:")) {
            cancelQueuedRun(testProjectId, itemId);
            return;
        }
        envRunConcurrencyService.dismissActive(testProjectId, itemId);
    }

    /**
     * 轮询 queueItemId 绑定，直到拿到 runId、发现已取消、或超时。
     */
    @Override
    public Long awaitQueuedRunStart(String queueItemId, long timeoutMs) {
        if (StrUtil.isBlank(queueItemId)) {
            return null;
        }
        long deadline = System.currentTimeMillis() + Math.max(1000L, timeoutMs);
        while (System.currentTimeMillis() < deadline) {
            Long peeked = peekQueuedRunId(queueItemId);
            if (peeked != null) {
                return peeked;
            }
            if (envRunConcurrencyService.isQueueItemCancelled(queueItemId)) {
                return null;
            }
            try {
                Thread.sleep(400L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return peekQueuedRunId(queueItemId);
    }

    /**
     * 单次查询排队项绑定的 runId；已取消视为尚未开跑（返回 null）。
     */
    @Override
    public Long peekQueuedRunId(String queueItemId) {
        if (StrUtil.isBlank(queueItemId) || envRunConcurrencyService.isQueueItemCancelled(queueItemId)) {
            return null;
        }
        return envRunConcurrencyService.findRunIdForQueueItem(queueItemId);
    }

    /** 在活动列表里按 queueItemId 找到对应环境 id，供取消排队用 */
    private Long findQueuedEnvId(Long testProjectId, String bareId, String rawId) {
        for (ProjectRunHubItemResult item : envRunConcurrencyService.listActive(testProjectId)) {
            if (("queue:" + bareId).equals(item.getItemId()) || rawId.equals(item.getItemId())) {
                return item.getTestProjectEnvId();
            }
        }
        return null;
    }

    /** 去掉前端可能带来的 {@code queue:} 前缀，得到裸 queueItemId */
    private static String stripQueuePrefix(String queueItemId) {
        return queueItemId.startsWith("queue:") ? queueItemId.substring("queue:".length()) : queueItemId;
    }

    /** 查库：项目下最近正式单流 Run，映射为运行中心行 */
    private List<ProjectRunHubItemResult> listRecentHubItems(Long testProjectId, int limit) {
        List<ProjectRunHubItemResult> out = new ArrayList<>();
        List<ProjectRunHubItemResult> flowRuns =
                testFlowRunService.selectRecentHubItemsByProjectId(testProjectId, limit);
        if (flowRuns != null) {
            out.addAll(flowRuns);
        }
        return out;
    }
}
