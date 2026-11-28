package com.qualitest.flow.http;

import com.qualitest.project.domain.TestProject;
import com.qualitest.project.enums.TestProjectMemberPerm;
import com.qualitest.project.mapper.TestProjectMapper;
import com.qualitest.project.service.ITestProjectMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 测试流外联 HTTP 权限解析器。
 * <p>
 * 开跑时把解析结果写入 {@code FlowRunContext.externalHttpPermitted}，
 * 后续 HTTP 节点按该标志决定能否访问外部地址。
 * <ul>
 *   <li>已登录触发（场景 / 套件 / MCP 等）：看操作者是否具备项目能力 {@code flow:external}</li>
 *   <li>开放 HTTP 入站：不看成员能力，只看项目开关 {@code openHttpAllowExternal}；未开或 null 视为禁止</li>
 * </ul>
 * 暂停许可不在本类处理，由项目 {@code allowPause} / 入站固定 false 在开跑处写入 pausePermitted。
 */
@Component
@RequiredArgsConstructor
public class FlowExternalPermission {

    private final ITestProjectMemberService testProjectMemberService;
    private final TestProjectMapper testProjectMapper;

    /**
     * 已登录入口：操作者是否具备项目外联能力。
     *
     * @param testProjectId  项目 id
     * @param operatorUserId 操作者用户 id；任一为空则返回 false
     * @return 允许外联时 true
     */
    public boolean resolve(Long testProjectId, Long operatorUserId) {
        if (testProjectId == null || operatorUserId == null) {
            return false;
        }
        return testProjectMemberService.hasCapability(
                testProjectId, operatorUserId, TestProjectMemberPerm.FLOW_EXTERNAL);
    }

    /**
     * 开放 HTTP 入站：读项目「开放入站允许外联」开关。
     * 项目不存在、开关为 null 或 false 时返回 false。
     *
     * @param testProjectId 项目 id
     * @return 允许外联时 true
     */
    public boolean resolveForOpenIngress(Long testProjectId) {
        if (testProjectId == null) {
            return false;
        }
        TestProject project = testProjectMapper.selectTestProjectById(testProjectId);
        return project != null && project.isOpenHttpExternalAllowed();
    }
}
