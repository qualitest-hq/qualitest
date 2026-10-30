package com.qualitest.web.controller.project;

import com.qualitest.common.annotation.Log;
import com.qualitest.common.core.controller.BaseController;
import com.qualitest.common.core.domain.R;
import com.qualitest.common.core.page.TableDataInfo;
import com.qualitest.common.core.text.Convert;
import com.qualitest.common.enums.BusinessType;
import com.qualitest.common.utils.poi.ExcelUtil;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.enums.TestProjectMemberPerm;
import com.qualitest.project.params.TestProjectApiBizCodeParams;
import com.qualitest.project.params.TestProjectApiGroupParams;
import com.qualitest.project.params.TestProjectApiParams;
import com.qualitest.project.result.TestProjectApiGroupResult;
import com.qualitest.project.result.TestProjectApiResult;
import com.qualitest.project.result.TestProjectApiTreeResult;
import com.qualitest.project.service.ITestProjectApiGroupService;
import com.qualitest.project.service.ITestProjectApiService;
import com.qualitest.project.service.ITestProjectMemberService;
import com.qualitest.project.support.TestProjectApiBizCodeService;
import com.qualitest.flow.diagnose.ApiFlowHealthPersistService;
import com.qualitest.flow.diagnose.ApiFlowReferenceItem;
import com.qualitest.flow.diagnose.ApiFlowReferenceScanService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 测试项目APIController
 *
 * @author qualitest
 * @date 2026-02-05
 */
@RestController
@RequestMapping("/project/testProjectApi")
@AllArgsConstructor
public class TestProjectApiController extends BaseController {

    private final ITestProjectApiService testProjectApiService;

    private final ITestProjectApiGroupService testProjectApiGroupService;

    private final TestProjectApiBizCodeService testProjectApiBizCodeService;

    private final ApiFlowReferenceScanService apiFlowReferenceScanService;

    /** Web 保存 API 后回写引用流的 api_health_* */
    private final ApiFlowHealthPersistService apiFlowHealthPersistService;

    private final ITestProjectMemberService testProjectMemberService;

    /**
     * 侧边栏 API 树；需查看接口权限。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:list')")
    @GetMapping("/apiTree")
    public R<List<TestProjectApiTreeResult>> apiTree(@RequestParam Long testProjectId) {
        testProjectMemberService.requireCapability(testProjectId, TestProjectMemberPerm.API_VIEW);
        List<TestProjectApiGroupResult> groups = testProjectApiGroupService.selectTestProjectApiGroupResultList(TestProjectApiGroupParams.builder()
                .testProjectId(testProjectId)
                .build());
        List<TestProjectApiResult> apis = testProjectApiService.selectTestProjectApiResultList(TestProjectApiParams.builder()
                .testProjectId(testProjectId)
                .build());
        return ok(TestProjectApiTreeResult.buildTree(groups, apis));
    }

    /**
     * 查询测试项目 API 列表；指定项目时需查看接口权限。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:list')")
    @GetMapping("/list")
    public TableDataInfo list(TestProjectApiParams params) {
        if (params.getTestProjectId() != null) {
            testProjectMemberService.requireCapability(params.getTestProjectId(), TestProjectMemberPerm.API_VIEW);
        }
        startPage();
        List<TestProjectApiResult> list = testProjectApiService.selectTestProjectApiResultList(params);
        return getDataTable(list);
    }

    /**
     * 导出测试项目 API 列表；指定项目时需查看接口权限。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:export')")
    @Log(title = "测试项目API", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, TestProjectApiParams params) {
        if (params.getTestProjectId() != null) {
            testProjectMemberService.requireCapability(params.getTestProjectId(), TestProjectMemberPerm.API_VIEW);
        }
        List<TestProjectApiResult> list = testProjectApiService.selectTestProjectApiResultList(params);
        ExcelUtil<TestProjectApiResult> util = new ExcelUtil<>(TestProjectApiResult.class);
        util.exportExcel(response, list, "测试项目API数据");
    }

    /**
     * 获取测试项目 API 详细信息；需查看接口权限。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @GetMapping(value = "/{testProjectApiId}")
    public R<TestProjectApiResult> getInfo(@PathVariable("testProjectApiId") Long testProjectApiId) {
        TestProjectApiResult result = testProjectApiService.selectTestProjectApiResult(testProjectApiId);
        if (result != null && result.getTestProjectId() != null) {
            testProjectMemberService.requireCapability(result.getTestProjectId(), TestProjectMemberPerm.API_VIEW);
        }
        return ok(result);
    }

    /**
     * 列出绑定了该 API 的测试流 HTTP 节点（反向引用）；需查看接口权限。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @GetMapping("/{testProjectApiId}/flowReferences")
    public R<List<ApiFlowReferenceItem>> flowReferences(@PathVariable Long testProjectApiId) {
        requireApiCapability(testProjectApiId, TestProjectMemberPerm.API_VIEW);
        return ok(apiFlowReferenceScanService.listReferences(testProjectApiId));
    }

    /**
     * 列出该 API 的测试流引用并做语义健康检查；需查看接口权限。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @GetMapping("/{testProjectApiId}/diagnoseImpacts")
    public R<ApiFlowReferenceScanService.DiagnoseImpactsResult> diagnoseImpacts(
            @PathVariable Long testProjectApiId) {
        requireApiCapability(testProjectApiId, TestProjectMemberPerm.API_VIEW);
        return ok(apiFlowReferenceScanService.diagnoseImpacts(testProjectApiId));
    }

    /**
     * 校验当前用户对该 API 所属项目具备指定权限。
     */
    private void requireApiCapability(Long testProjectApiId, TestProjectMemberPerm perm) {
        TestProjectApiResult api = testProjectApiService.selectTestProjectApiResult(testProjectApiId);
        if (api != null && api.getTestProjectId() != null) {
            testProjectMemberService.requireCapability(api.getTestProjectId(), perm);
        }
    }

    /**
     * 新增测试项目 API；需新增接口权限。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:add')")
    @Log(title = "测试项目API", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@RequestBody TestProjectApi testProjectApi) {
        if (testProjectApi.getTestProjectId() != null) {
            testProjectMemberService.requireCapability(testProjectApi.getTestProjectId(), TestProjectMemberPerm.API_ADD);
        }
        return toR(testProjectApiService.insertTestProjectApi(testProjectApi));
    }

    /**
     * 修改测试项目 API；需编辑接口与分组权限。
     * 保存成功后，对该 API 在图中的全部引用流重新体检并回写 api_health_*。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:edit')")
    @Log(title = "测试项目API", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@RequestBody TestProjectApi testProjectApi) {
        Long projectId = testProjectApi.getTestProjectId();
        if (projectId == null && testProjectApi.getTestProjectApiId() != null) {
            TestProjectApiResult existing = testProjectApiService.selectTestProjectApiResult(
                    testProjectApi.getTestProjectApiId());
            if (existing != null) {
                projectId = existing.getTestProjectId();
            }
        }
        if (projectId != null) {
            testProjectMemberService.requireCapability(projectId, TestProjectMemberPerm.API_EDIT);
        }
        int rows = testProjectApiService.updateTestProjectApi(testProjectApi);
        if (rows > 0 && testProjectApi.getTestProjectApiId() != null) {
            apiFlowHealthPersistService.refreshByApiId(testProjectApi.getTestProjectApiId());
        }
        return toR(rows);
    }

    /**
     * 追加 API 业务响应码白名单中的 successValues。
     * 合并去重后写回 biz_code_config；返回写库后的完整 JSON。
     * 需编辑接口与分组权限。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:edit')")
    @Log(title = "测试项目API业务Code", businessType = BusinessType.UPDATE)
    @PutMapping("/{testProjectApiId}/bizCodeConfig")
    public R<String> appendBizCodeConfig(@PathVariable Long testProjectApiId,
                                         @RequestBody TestProjectApiBizCodeParams params) {
        requireApiCapability(testProjectApiId, TestProjectMemberPerm.API_EDIT);
        return ok(testProjectApiBizCodeService.appendSuccessValues(
                testProjectApiId,
                params != null ? params.getSuccessValues() : null,
                params != null ? params.getSource() : null));
    }

    /**
     * 删除测试项目 API；逐项校验删除接口权限。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:remove')")
    @Log(title = "测试项目API", businessType = BusinessType.DELETE)
    @DeleteMapping("/{testProjectApiIds}")
    public R<Void> remove(@PathVariable Long[] testProjectApiIds) {
        List<Long> idList = Convert.toLongList(testProjectApiIds);
        for (Long id : idList) {
            requireApiCapability(id, TestProjectMemberPerm.API_REMOVE);
        }
        return toR(testProjectApiService.logicDeleteTestProjectApiByIdList(idList));
    }
}
