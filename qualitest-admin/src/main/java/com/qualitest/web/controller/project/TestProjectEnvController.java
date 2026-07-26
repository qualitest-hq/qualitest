package com.qualitest.web.controller.project;

import com.qualitest.common.annotation.Log;
import com.qualitest.common.core.controller.BaseController;
import com.qualitest.common.core.domain.R;
import com.qualitest.common.core.page.TableDataInfo;
import com.qualitest.common.core.text.Convert;
import com.qualitest.common.enums.BusinessType;
import com.qualitest.common.utils.SecurityUtils;
import com.qualitest.common.utils.poi.ExcelUtil;
import com.qualitest.project.domain.TestProjectEnv;
import com.qualitest.project.params.TestProjectEnvParams;
import com.qualitest.project.params.TestProjectEnvReorderParams;
import com.qualitest.project.result.TestProjectEnvResult;
import com.qualitest.project.service.ITestProjectEnvService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

/**
 * 测试项目环境Controller
 *
 * @author qualitest
 * @date 2026-02-05
 */
@RestController
@RequestMapping("/project/testProjectEnv")
@AllArgsConstructor
public class TestProjectEnvController extends BaseController {

    private final ITestProjectEnvService testProjectEnvService;

    /**
     * 查询测试项目环境列表
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:list')")
    @GetMapping("/list")
    public TableDataInfo list(TestProjectEnvParams params) {
        params.setUserId(SecurityUtils.getUserId());
        startPage();
        List<TestProjectEnvResult> list = testProjectEnvService.selectTestProjectEnvResultList(params);
        return getDataTable(list);
    }

    /**
     * 导出测试项目环境列表
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:export')")
    @Log(title = "测试项目环境", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, TestProjectEnvParams params) {
        params.setUserId(SecurityUtils.getUserId());
        List<TestProjectEnvResult> list = testProjectEnvService.selectTestProjectEnvResultList(params);
        ExcelUtil<TestProjectEnvResult> util = new ExcelUtil<>(TestProjectEnvResult.class);
        util.exportExcel(response, list, "测试项目环境数据");
    }

    /**
     * 获取测试项目环境详细信息
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @GetMapping(value = "/{testProjectEnvId}")
    public R<TestProjectEnvResult> getInfo(@PathVariable("testProjectEnvId") Long testProjectEnvId) {
        return ok(testProjectEnvService.selectTestProjectEnvResult(testProjectEnvId));
    }

    /**
     * 新增测试项目环境
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:add')")
    @Log(title = "测试项目环境", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@RequestBody TestProjectEnv testProjectEnv) {
        if (Objects.isNull(testProjectEnv.getUserId())) {
            testProjectEnv.setUserId(SecurityUtils.getUserId());
        }
        return toR(testProjectEnvService.insertTestProjectEnv(testProjectEnv));
    }

    /**
     * 修改测试项目环境
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:edit')")
    @Log(title = "测试项目环境", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@RequestBody TestProjectEnv testProjectEnv) {
        return toR(testProjectEnvService.updateTestProjectEnv(testProjectEnv));
    }

    /**
     * 拖动排序（批量更新 sort_num，单接口单事务）
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:edit')")
    @Log(title = "测试项目环境排序", businessType = BusinessType.UPDATE)
    @PutMapping("/reorder")
    public R<Void> reorder(@RequestBody TestProjectEnvReorderParams params) {
        testProjectEnvService.reorderTestProjectEnvs(
                params.getTestProjectId(),
                params.getOrderedEnvIds(),
                SecurityUtils.getUserId());
        return ok();
    }

    /**
     * 删除测试项目环境
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:remove')")
    @Log(title = "测试项目环境", businessType = BusinessType.DELETE)
    @DeleteMapping("/{testProjectEnvIds}")
    public R<Void> remove(@PathVariable Long[] testProjectEnvIds) {
        List<Long> idList = Convert.toLongList(testProjectEnvIds);
        return toR(testProjectEnvService.logicDeleteTestProjectEnvByIdList(idList));
    }
}
