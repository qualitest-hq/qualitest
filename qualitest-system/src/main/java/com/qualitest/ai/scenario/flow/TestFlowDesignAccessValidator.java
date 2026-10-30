package com.qualitest.ai.scenario.flow;

import com.qualitest.ai.scenario.flow.model.TestFlowDesignRequest;
import com.qualitest.ai.tools.flow.TestFlowAccessSupport;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.project.enums.TestProjectMemberPerm;
import com.qualitest.project.service.ITestFlowService;
import com.qualitest.project.service.ITestProjectMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 测试流 AI 设计入口：校验项目成员 AI 权限，并确认测试流属于指定项目。
 * 模板设计模式不校验成员与流归属。
 */
@Component
@RequiredArgsConstructor
public class TestFlowDesignAccessValidator {

    /** 项目成员权限校验 */
    private final ITestProjectMemberService testProjectMemberService;

    /** 按流 id 读取测试流 */
    private final ITestFlowService testFlowService;

    /**
     * 非模板模式：要求 ai:flow:design；开启全自动时再要求 ai:flow:write；并确认流归属项目。
     *
     * @param request 造流设计请求
     */
    public void validateMemberAndFlow(TestFlowDesignRequest request) {
        if (request == null) {
            throw new ServiceException("请求不能为空");
        }
        if (request.isTemplateDesignMode()) {
            return;
        }
        if (request.getTestProjectId() != null) {
            testProjectMemberService.requireCapability(
                    request.getTestProjectId(), TestProjectMemberPerm.AI_FLOW_DESIGN);
            if (request.isAutopilotEnabledEffective()) {
                testProjectMemberService.requireCapability(
                        request.getTestProjectId(), TestProjectMemberPerm.AI_FLOW_WRITE);
            }
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
