package com.qualitest.web.controller.project;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.qualitest.common.annotation.Log;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.common.core.controller.BaseController;
import com.qualitest.common.core.domain.R;
import com.qualitest.common.enums.BusinessType;
import com.qualitest.project.domain.TestFlow;
import com.qualitest.ai.scenario.flow.FlowAuthHeaderRefreshService;
import com.qualitest.ai.scenario.flow.model.RefreshAuthHeadersResult;
import com.qualitest.project.params.CreateSubflowFromTemplateParams;
import com.qualitest.project.params.RefreshAuthHeadersParams;
import com.qualitest.project.params.TestFlowApiHealthPreviewParams;
import com.qualitest.project.params.TestFlowParams;
import com.qualitest.project.params.UpgradeTestFlowGraphParams;
import com.qualitest.project.result.TestFlowResult;
import com.qualitest.project.service.ITestFlowService;
import com.qualitest.project.service.ITestProjectMemberService;
import com.qualitest.project.service.TestFlowGraphUpgradeService;
import com.qualitest.flow.diagnose.ApiFlowHealthPersistService;
import com.qualitest.flow.diagnose.ApiFlowReferenceScanService;
import com.qualitest.flow.subflow.SubflowTemplateCatalog;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.common.core.text.Convert;
import com.qualitest.common.utils.poi.ExcelUtil;
import com.qualitest.common.core.page.TableDataInfo;

/**
 * 测试流Controller
 * 
 * @author qualitest
 * @date 2026-06-05
 */
@RestController
@RequestMapping("/project/testFlow")
@AllArgsConstructor
public class TestFlowController extends BaseController {

    private final ITestFlowService testFlowService;
    private final ITestProjectMemberService testProjectMemberService;
    private final TestFlowGraphUpgradeService testFlowGraphUpgradeService;
    /** 保存流或主动刷新时，把语义告警条数写回 test_flow.api_health_* */
    private final ApiFlowHealthPersistService apiFlowHealthPersistService;
    /** 按入参 graphJson 做语义体检，不写库（画布预检用） */
    private final ApiFlowReferenceScanService apiFlowReferenceScanService;
    /** 按项目鉴权刷新本流托管头（只提案，不写库） */
    private final FlowAuthHeaderRefreshService flowAuthHeaderRefreshService;

    /**
     * 查询测试流列表
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:list')")
    @GetMapping("/list")
    public TableDataInfo list(TestFlowParams params) {
        if (params.getTestProjectId() != null) {
            testProjectMemberService.getCheckProjectMemberRole(params.getTestProjectId());
        }
        startPage();
        List<TestFlowResult> list = testFlowService.selectTestFlowResultList(params);
        return getDataTable(list);
    }

    /**
     * 导出测试流列表
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:export')")
    @Log(title = "测试流", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, TestFlowParams params) {
        if (params.getTestProjectId() != null) {
            testProjectMemberService.getCheckProjectMemberRole(params.getTestProjectId());
        }
        List<TestFlowResult> list = testFlowService.selectTestFlowResultList(params);
        ExcelUtil<TestFlowResult> util = new ExcelUtil<>(TestFlowResult.class);
        util.exportExcel(response, list, "测试流数据");
    }

    /**
     * 获取测试流详细信息
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @GetMapping(value = "/{testFlowId}")
    public R<TestFlowResult> getInfo(@PathVariable("testFlowId") Long testFlowId) {
        TestFlowResult result = testFlowService.selectTestFlowResult(testFlowId);
        if (result != null && result.getTestProjectId() != null) {
            testProjectMemberService.getCheckProjectMemberRole(result.getTestProjectId());
        }
        if (result != null) {
            testFlowGraphUpgradeService.enrichUpgradeMeta(result);
        }
        return ok(result);
    }

    /**
     * 按库中已保存的图检查本测试流 HTTP 节点的 API 语义告警，并回写 api_health_*。
     * <p>
     * 告警包括：绑定的 API 不存在、测值参数已从接口中移除、抽取路径对不上响应结构等。
     * 仅提示，不拦截保存或执行。写回字段供仪表盘「接口变更待关注」列表使用。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @GetMapping("/{testFlowId}/apiHealth")
    public R<ApiFlowReferenceScanService.FlowApiHealthResult> apiHealth(
            @PathVariable("testFlowId") Long testFlowId) {
        requireAccessibleFlow(testFlowId);
        return ok(apiFlowHealthPersistService.refreshAndGet(testFlowId));
    }

    /**
     * 按请求体里的画布草稿 graphJson 做 API 语义预检，不回写 api_health_*。
     * <p>
     * 用于画布未保存时：左上角校验条、节点属性面板按「页面上当前图」展示告警，
     * 换绑修好后无需先点保存即可看到告警消失。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @PostMapping("/{testFlowId}/apiHealth/preview")
    public R<ApiFlowReferenceScanService.FlowApiHealthResult> apiHealthPreview(
            @PathVariable("testFlowId") Long testFlowId,
            @RequestBody(required = false) TestFlowApiHealthPreviewParams params) {
        TestFlow flow = requireAccessibleFlow(testFlowId);
        String graphJson = params != null ? params.getGraphJson() : null;
        return ok(apiFlowReferenceScanService.checkGraphHealth(flow, graphJson));
    }

    /**
     * 按当前项目鉴权配置，为本流 project HTTP 节点刷新 profileManaged 托管头。
     * <p>
     * 只返回 Staging 可用的 updateNodes patch，不写 test_flow；前端确认后再保存。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:edit')")
    @PostMapping("/refreshAuthHeaders")
    public R<RefreshAuthHeadersResult> refreshAuthHeaders(@RequestBody RefreshAuthHeadersParams params) {
        if (params == null || params.getTestProjectId() == null) {
            throw new ServiceException("testProjectId 不能为空");
        }
        testProjectMemberService.getCheckProjectMemberRole(params.getTestProjectId());
        return ok(flowAuthHeaderRefreshService.refresh(params.getTestProjectId(), params.getGraphJson()));
    }

    /**
     * 加载未删除的测试流，并校验当前登录用户是该流所属项目的成员。
     *
     * @return 通过校验的测试流实体
     */
    private TestFlow requireAccessibleFlow(Long testFlowId) {
        TestFlow flow = testFlowService.selectTestFlowById(testFlowId);
        if (flow == null || (flow.getDelStatus() != null && flow.getDelStatus() != 0)) {
            throw new ServiceException("测试流不存在");
        }
        testProjectMemberService.getCheckProjectMemberRole(flow.getTestProjectId());
        return flow;
    }

    /**
     * 将测试流 graph_json 迁移到当前 schema 版本并持久化。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:edit')")
    @Log(title = "测试流", businessType = BusinessType.UPDATE)
    @PostMapping("/upgradeGraph")
    public R<TestFlowResult> upgradeGraph(@RequestBody UpgradeTestFlowGraphParams params) {
        if (params == null || params.getTestFlowId() == null) {
            throw new ServiceException("testFlowId 不能为空");
        }
        TestFlow flow = testFlowService.selectTestFlowById(params.getTestFlowId());
        if (flow == null || (flow.getDelStatus() != null && flow.getDelStatus() != 0)) {
            throw new ServiceException("测试流不存在");
        }
        testProjectMemberService.getCheckProjectMemberRole(flow.getTestProjectId());
        return ok(testFlowGraphUpgradeService.upgradeGraph(flow, testFlowService));
    }

    /**
     * 列举平台内置子流模板元数据（templateId、名称、inputs/outputs），不含 graphJson 全文。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:list')")
    @GetMapping("/subflowTemplates")
    public R<List<JSONObject>> listSubflowTemplates() {
        return ok(SubflowTemplateCatalog.listTemplates());
    }

    /**
     * 按 templateId 复制平台模板为当前项目的测试流，含图与 meta.flowOutputs。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:add')")
    @Log(title = "测试流", businessType = BusinessType.INSERT)
    @PostMapping("/fromSubflowTemplate")
    public R<TestFlowResult> createFromSubflowTemplate(@RequestBody CreateSubflowFromTemplateParams params) {
        if (params == null || params.getTestProjectId() == null) {
            throw new ServiceException("testProjectId 不能为空");
        }
        testProjectMemberService.getCheckProjectMemberRole(params.getTestProjectId());
        Long testFlowId = testFlowService.createFromSubflowTemplate(params);
        TestFlowResult created = testFlowService.selectTestFlowResult(testFlowId);
        return ok(created);
    }

    /**
     * 新增测试流
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:add')")
    @Log(title = "测试流", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@RequestBody TestFlow testFlow) {
        if (testFlow.getTestProjectId() != null) {
            testProjectMemberService.getCheckProjectMemberRole(testFlow.getTestProjectId());
        }
        return toR(testFlowService.insertTestFlow(testFlow));
    }

    /**
     * 修改测试流。
     * 保存成功后对该流重新做 API 语义体检并回写 api_health_*。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:edit')")
    @Log(title = "测试流", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@RequestBody TestFlow testFlow) {
        if (testFlow.getTestProjectId() != null) {
            testProjectMemberService.getCheckProjectMemberRole(testFlow.getTestProjectId());
        } else if (testFlow.getTestFlowId() != null) {
            TestFlowResult existing = testFlowService.selectTestFlowResult(testFlow.getTestFlowId());
            if (existing != null && existing.getTestProjectId() != null) {
                testProjectMemberService.getCheckProjectMemberRole(existing.getTestProjectId());
            }
        }
        int rows = testFlowService.updateTestFlow(testFlow);
        if (rows > 0 && testFlow.getTestFlowId() != null) {
            apiFlowHealthPersistService.refreshFlow(testFlow.getTestFlowId());
        }
        return toR(rows);
    }

    /**
     * 删除测试流
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:remove')")
    @Log(title = "测试流", businessType = BusinessType.DELETE)
    @DeleteMapping("/{testFlowIds}")
    public R<Void> remove(@PathVariable Long[] testFlowIds) {
        List<Long> idList = Convert.toLongList(testFlowIds);
        for (Long id : idList) {
            TestFlowResult existing = testFlowService.selectTestFlowResult(id);
            if (existing != null && existing.getTestProjectId() != null) {
                testProjectMemberService.getCheckProjectMemberRole(existing.getTestProjectId());
            }
        }
        return toR(testFlowService.logicDeleteTestFlowByIdList(idList));
    }
}
