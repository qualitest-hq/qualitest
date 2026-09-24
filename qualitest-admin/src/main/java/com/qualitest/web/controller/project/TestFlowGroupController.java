package com.qualitest.web.controller.project;

import com.qualitest.common.annotation.Log;
import com.qualitest.common.core.controller.BaseController;
import com.qualitest.common.core.domain.R;
import com.qualitest.common.core.page.TableDataInfo;
import com.qualitest.common.core.text.Convert;
import com.qualitest.common.enums.BusinessType;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.project.domain.TestFlowGroup;
import com.qualitest.project.params.TestFlowGroupParams;
import com.qualitest.project.result.TestFlowGroupResult;
import com.qualitest.project.service.ITestFlowGroupService;
import com.qualitest.project.service.ITestProjectMemberService;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 测试流分组Controller
 *
 * @author qualitest
 * @date 2026-09-24
 */
@RestController
@RequestMapping("/project/testFlowGroup")
@AllArgsConstructor
public class TestFlowGroupController extends BaseController {

    private final ITestFlowGroupService testFlowGroupService;
    private final ITestProjectMemberService testProjectMemberService;

    /**
     * 查询测试流分组列表
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:list')")
    @GetMapping("/list")
    public TableDataInfo list(TestFlowGroupParams params) {
        if (params.getTestProjectId() == null) {
            throw new ServiceException("testProjectId 不能为空");
        }
        testProjectMemberService.getCheckProjectMemberRole(params.getTestProjectId());
        List<TestFlowGroupResult> list = testFlowGroupService.selectTestFlowGroupResultList(params);
        return getDataTable(list);
    }

    /**
     * 查询测试流分组树
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:list')")
    @GetMapping("/tree")
    public R<List<TestFlowGroupResult>> tree(@RequestParam("testProjectId") Long testProjectId) {
        if (testProjectId == null) {
            throw new ServiceException("testProjectId 不能为空");
        }
        testProjectMemberService.getCheckProjectMemberRole(testProjectId);
        return ok(testFlowGroupService.selectTestFlowGroupTree(testProjectId));
    }

    /**
     * 获取测试流分组详细信息
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @GetMapping("/{flowGroupId}")
    public R<TestFlowGroupResult> getInfo(@PathVariable("flowGroupId") Long flowGroupId) {
        TestFlowGroupResult result = testFlowGroupService.selectTestFlowGroupResult(flowGroupId);
        if (result != null && result.getTestProjectId() != null) {
            testProjectMemberService.getCheckProjectMemberRole(result.getTestProjectId());
        }
        return ok(result);
    }

    /**
     * 新增测试流分组
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:add')")
    @Log(title = "测试流分组", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@RequestBody TestFlowGroup testFlowGroup) {
        if (testFlowGroup.getTestProjectId() == null) {
            throw new ServiceException("testProjectId 不能为空");
        }
        testProjectMemberService.getCheckProjectMemberRole(testFlowGroup.getTestProjectId());
        return toR(testFlowGroupService.insertTestFlowGroup(testFlowGroup));
    }

    /**
     * 修改测试流分组
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:edit')")
    @Log(title = "测试流分组", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@RequestBody TestFlowGroup testFlowGroup) {
        if (testFlowGroup.getFlowGroupId() == null) {
            throw new ServiceException("flowGroupId 不能为空");
        }
        TestFlowGroup existing = testFlowGroupService.selectTestFlowGroupById(testFlowGroup.getFlowGroupId());
        if (existing == null || (existing.getDelStatus() != null && existing.getDelStatus() != 0)) {
            throw new ServiceException("分组不存在");
        }
        testProjectMemberService.getCheckProjectMemberRole(existing.getTestProjectId());
        return toR(testFlowGroupService.updateTestFlowGroup(testFlowGroup));
    }

    /**
     * 删除测试流分组
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:remove')")
    @Log(title = "测试流分组", businessType = BusinessType.DELETE)
    @DeleteMapping("/{flowGroupIds}")
    public R<Void> remove(@PathVariable Long[] flowGroupIds) {
        List<Long> idList = Convert.toLongList(flowGroupIds);
        for (Long id : idList) {
            TestFlowGroupResult existing = testFlowGroupService.selectTestFlowGroupResult(id);
            if (existing != null && existing.getTestProjectId() != null) {
                testProjectMemberService.getCheckProjectMemberRole(existing.getTestProjectId());
            }
        }
        return toR(testFlowGroupService.logicDeleteTestFlowGroupByIdList(idList));
    }
}
