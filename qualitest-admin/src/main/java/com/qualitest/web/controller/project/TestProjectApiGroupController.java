package com.qualitest.web.controller.project;

import com.qualitest.common.annotation.Log;
import com.qualitest.common.core.controller.BaseController;
import com.qualitest.common.core.domain.R;
import com.qualitest.common.core.page.TableDataInfo;
import com.qualitest.common.core.text.Convert;
import com.qualitest.common.enums.BusinessType;
import com.qualitest.common.utils.poi.ExcelUtil;
import com.qualitest.project.domain.TestProjectApiGroup;
import com.qualitest.project.params.TestProjectApiGroupParams;
import com.qualitest.project.result.TestProjectApiGroupResult;
import com.qualitest.project.service.ITestProjectApiGroupService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 测试项目API分组Controller
 *
 * @author qualitest
 * @date 2026-02-05
 */
@RestController
@RequestMapping("/project/testProjectApiGroup")
@AllArgsConstructor
public class TestProjectApiGroupController extends BaseController {

    private final ITestProjectApiGroupService testProjectApiGroupService;

    /**
     * 查询测试项目API分组列表
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:list')")
    @GetMapping("/list")
    public TableDataInfo list(TestProjectApiGroupParams params) {
        startPage();
        List<TestProjectApiGroupResult> list = testProjectApiGroupService.selectTestProjectApiGroupResultList(params);
        return getDataTable(list);
    }

    /**
     * 导出测试项目API分组列表
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:export')")
    @Log(title = "测试项目API分组", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, TestProjectApiGroupParams params) {
        List<TestProjectApiGroupResult> list = testProjectApiGroupService.selectTestProjectApiGroupResultList(params);
        ExcelUtil<TestProjectApiGroupResult> util = new ExcelUtil<>(TestProjectApiGroupResult.class);
        util.exportExcel(response, list, "测试项目API分组数据");
    }

    /**
     * 获取测试项目API分组详细信息
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @GetMapping(value = "/{apiGroupId}")
    public R<TestProjectApiGroupResult> getInfo(@PathVariable("apiGroupId") Long apiGroupId) {
        return ok(testProjectApiGroupService.selectTestProjectApiGroupResult(apiGroupId));
    }

    /**
     * 新增测试项目API分组
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:add')")
    @Log(title = "测试项目API分组", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@RequestBody TestProjectApiGroup testProjectApiGroup) {
        return toR(testProjectApiGroupService.insertTestProjectApiGroup(testProjectApiGroup));
    }

    /**
     * 修改测试项目API分组
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:edit')")
    @Log(title = "测试项目API分组", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@RequestBody TestProjectApiGroup testProjectApiGroup) {
        return toR(testProjectApiGroupService.updateTestProjectApiGroup(testProjectApiGroup));
    }

    /**
     * 删除测试项目API分组
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:remove')")
    @Log(title = "测试项目API分组", businessType = BusinessType.DELETE)
    @DeleteMapping("/{apiGroupIds}")
    public R<Void> remove(@PathVariable Long[] apiGroupIds) {
        List<Long> idList = Convert.toLongList(apiGroupIds);
        return toR(testProjectApiGroupService.logicDeleteTestProjectApiGroupByIdList(idList));
    }
}
