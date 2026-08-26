package com.qualitest.ai.scenario.flow;

import com.qualitest.ai.scenario.flow.model.TestFlowDesignRequest;
import com.qualitest.ai.tools.flow.TestFlowAccessSupport;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.project.service.ITestFlowService;
import com.qualitest.project.service.ITestProjectMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 测试流 AI 设计入口权限与资源归属校验（成员 + testFlow 属于 testProject）。
 * 模板模式跳过项目成员与流归属校验（由控制器权限字控制）。
 */
@Component
@RequiredArgsConstructor
public class TestFlowDesignAccessValidator {

    private final ITestProjectMemberService testProjectMemberService;
    private final ITestFlowService testFlowService;

    public void validateMemberAndFlow(TestFlowDesignRequest request) {
        if (request == null) {
            throw new ServiceException("请求不能为空");
        }
        if (request.isTemplateDesignMode()) {
            return;
        }
        if (request.getTestProjectId() != null) {
            testProjectMemberService.getCheckProjectMemberRole(request.getTestProjectId());
        }
        if (request.getTestFlowId() != null && request.getTestProjectId() != null) {
            var access = TestFlowAccessSupport.resolveFlowInProject(
                    request.getTestFlowId(), request.getTestProjectId(), testFlowService);
            if (!access.isOk()) {
                throw new ServiceException(access.errorMessage());
            }
        }
    }
}
