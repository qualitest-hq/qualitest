package com.qualitest.web.controller.project;

import com.qualitest.common.annotation.Log;
import com.qualitest.common.core.controller.BaseController;
import com.qualitest.common.core.domain.R;
import com.qualitest.common.core.page.TableDataInfo;
import com.qualitest.common.core.text.Convert;
import com.qualitest.common.enums.BusinessType;
import com.qualitest.common.utils.poi.ExcelUtil;
import com.qualitest.project.domain.TestProjectTemplate;
import com.qualitest.project.params.TestProjectTemplateParams;
import com.qualitest.project.result.TestProjectTemplateResult;
import com.qualitest.project.service.ITestProjectTemplateService;
import com.qualitest.project.support.templatepack.ProjectTemplatePackModels.ImportRequest;
import com.qualitest.project.support.templatepack.ProjectTemplatePackModels.PackOpResult;
import com.qualitest.project.support.templatepack.ProjectTemplatePackService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 项目模板Controller
 *
 * @author qualitest
 * @date 2026-09-09
 */
@RestController
@RequestMapping("/project/testProjectTemplate")
@AllArgsConstructor
public class TestProjectTemplateController extends BaseController {

    private final ITestProjectTemplateService testProjectTemplateService;
    private final ProjectTemplatePackService projectTemplatePackService;

    /**
     * 查询项目模板列表
     */
    @PreAuthorize("@ss.hasPermi('project:testProjectTemplate:list')")
    @GetMapping("/list")
    public TableDataInfo list(TestProjectTemplateParams params) {
        startPage();
        List<TestProjectTemplateResult> list = testProjectTemplateService.selectTestProjectTemplateResultList(params);
        return getDataTable(list);
    }

    /**
     * 导出项目模板列表
     */
    @PreAuthorize("@ss.hasPermi('project:testProjectTemplate:export')")
    @Log(title = "项目模板", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, TestProjectTemplateParams params) {
        List<TestProjectTemplateResult> list = testProjectTemplateService.selectTestProjectTemplateResultList(params);
        ExcelUtil<TestProjectTemplateResult> util = new ExcelUtil<>(TestProjectTemplateResult.class);
        util.exportExcel(response, list, "项目模板数据");
    }

    /**
     * 查询已启用项目模板列表（新建项目 / 项目设置勾选）
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @GetMapping("/enabledList")
    public R<List<TestProjectTemplateResult>> enabledList() {
        return ok(testProjectTemplateService.selectEnabledList());
    }

    /**
     * 返回精简包 JSON Schema 原文
     */
    @PreAuthorize("@ss.hasPermi('project:testProjectTemplate:add')")
    @GetMapping(value = "/schema", produces = MediaType.APPLICATION_JSON_VALUE)
    public String schema() {
        return projectTemplatePackService.loadSchemaJson();
    }

    /**
     * 返回可复制给 AI 的精简包生成提示词正文
     */
    @PreAuthorize("@ss.hasPermi('project:testProjectTemplate:add')")
    @GetMapping("/aiPrompt")
    public R<String> aiPrompt() {
        return ok(projectTemplatePackService.loadAiPromptText());
    }

    /**
     * 校验完整包或精简包（摘要与预览，不写库）
     */
    @PreAuthorize("@ss.hasPermi('project:testProjectTemplate:add')")
    @PostMapping("/validate")
    public R<PackOpResult> validate(@RequestBody com.fasterxml.jackson.databind.JsonNode template) {
        return ok(projectTemplatePackService.validate(template));
    }

    /**
     * 导入完整包或精简包为自定义模板
     */
    @PreAuthorize("@ss.hasPermi('project:testProjectTemplate:add')")
    @Log(title = "测试项目模板导入", businessType = BusinessType.INSERT)
    @PostMapping("/import")
    public R<PackOpResult> importTemplate(@RequestBody ImportRequest request) {
        return ok(projectTemplatePackService.importTemplate(request));
    }

    /**
     * 按主键导出完整包 JSON（含预制测试流等）
     */
    @PreAuthorize("@ss.hasPermi('project:testProjectTemplate:query')")
    @GetMapping("/{testProjectTemplateId}/export")
    public R<Map<String, Object>> exportPack(@PathVariable Long testProjectTemplateId) {
        return ok(projectTemplatePackService.exportPack(testProjectTemplateId));
    }

    /**
     * 获取项目模板详细信息
     */
    @PreAuthorize("@ss.hasPermi('project:testProjectTemplate:query')")
    @GetMapping("/{testProjectTemplateId}")
    public R<TestProjectTemplateResult> getInfo(@PathVariable Long testProjectTemplateId) {
        return ok(testProjectTemplateService.selectTestProjectTemplateResult(testProjectTemplateId));
    }

    /**
     * 新增项目模板
     */
    @PreAuthorize("@ss.hasPermi('project:testProjectTemplate:add')")
    @Log(title = "测试项目模板", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@RequestBody TestProjectTemplate entity) {
        return toR(testProjectTemplateService.insertTestProjectTemplate(entity));
    }

    /**
     * 修改项目模板
     */
    @PreAuthorize("@ss.hasPermi('project:testProjectTemplate:edit')")
    @Log(title = "测试项目模板", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@RequestBody TestProjectTemplate entity) {
        return toR(testProjectTemplateService.updateTestProjectTemplate(entity));
    }

    /**
     * 克隆为自定义项目模板
     */
    @PreAuthorize("@ss.hasPermi('project:testProjectTemplate:add')")
    @Log(title = "测试项目模板", businessType = BusinessType.INSERT)
    @PostMapping("/{testProjectTemplateId}/clone")
    public R<Long> clone(@PathVariable Long testProjectTemplateId) {
        return ok(testProjectTemplateService.cloneTestProjectTemplate(testProjectTemplateId));
    }

    /**
     * 删除项目模板
     */
    @PreAuthorize("@ss.hasPermi('project:testProjectTemplate:remove')")
    @Log(title = "测试项目模板", businessType = BusinessType.DELETE)
    @DeleteMapping("/{testProjectTemplateIds}")
    public R<Void> remove(@PathVariable Long[] testProjectTemplateIds) {
        List<Long> idList = Convert.toLongList(testProjectTemplateIds);
        return toR(testProjectTemplateService.logicDeleteTestProjectTemplateByIdList(idList));
    }
}
