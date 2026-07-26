package com.qualitest.web.controller.project;

import com.qualitest.common.annotation.Log;
import com.qualitest.common.core.controller.BaseController;
import com.qualitest.common.core.domain.R;
import com.qualitest.common.core.page.TableDataInfo;
import com.qualitest.common.core.text.Convert;
import com.qualitest.common.enums.BusinessType;
import com.qualitest.project.params.TestProjectAssetParams;
import com.qualitest.project.params.TestProjectAssetSaveParams;
import com.qualitest.project.result.TestProjectAssetResult;
import com.qualitest.project.service.ITestProjectAssetService;
import com.qualitest.project.service.ITestProjectMemberService;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 项目素材库 Controller
 *
 * @author qualitest
 */
@RestController
@RequestMapping("/project/testProjectAsset")
@AllArgsConstructor
public class TestProjectAssetController extends BaseController {

    private final ITestProjectAssetService testProjectAssetService;
    private final ITestProjectMemberService testProjectMemberService;

    /**
     * 查询项目下素材列表（不分页）
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @GetMapping("/list")
    public TableDataInfo list(TestProjectAssetParams params) {
        testProjectMemberService.getCheckProjectMemberRole(params.getTestProjectId());
        List<TestProjectAssetResult> list = testProjectAssetService.selectTestProjectAssetResultList(params);
        return getDataTable(list);
    }

    /**
     * 按条目 id 查询单条
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @GetMapping("/{id}")
    public R<TestProjectAssetResult> getInfo(
            @PathVariable("id") Long id,
            @RequestParam("testProjectId") Long testProjectId) {
        testProjectMemberService.getCheckProjectMemberRole(testProjectId);
        return ok(testProjectAssetService.selectTestProjectAssetResult(testProjectId, id));
    }

    /**
     * 按 key 查询单条
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @GetMapping("/byKey")
    public R<TestProjectAssetResult> getByKey(
            @RequestParam("testProjectId") Long testProjectId,
            @RequestParam("key") String key) {
        testProjectMemberService.getCheckProjectMemberRole(testProjectId);
        return ok(testProjectAssetService.selectTestProjectAssetResultByKey(testProjectId, key));
    }

    /**
     * 新增素材条目
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:add')")
    @Log(title = "项目素材库", businessType = BusinessType.INSERT)
    @PostMapping
    public R<TestProjectAssetResult> add(@RequestBody TestProjectAssetSaveParams params) {
        testProjectMemberService.getCheckProjectMemberRole(params.getTestProjectId());
        return ok(testProjectAssetService.insertTestProjectAsset(params));
    }

    /**
     * 修改素材条目
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:edit')")
    @Log(title = "项目素材库", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<TestProjectAssetResult> edit(@RequestBody TestProjectAssetSaveParams params) {
        testProjectMemberService.getCheckProjectMemberRole(params.getTestProjectId());
        return ok(testProjectAssetService.updateTestProjectAsset(params));
    }

    /**
     * 批量删除素材条目
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:remove')")
    @Log(title = "项目素材库", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(
            @PathVariable Long[] ids,
            @RequestParam("testProjectId") Long testProjectId) {
        testProjectMemberService.getCheckProjectMemberRole(testProjectId);
        List<Long> idList = Convert.toLongList(ids);
        return toR(testProjectAssetService.deleteTestProjectAssetByIds(testProjectId, idList));
    }

}
