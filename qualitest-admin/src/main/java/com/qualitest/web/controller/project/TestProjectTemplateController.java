package com.qualitest.web.controller.project;

import com.qualitest.common.annotation.Log;
import com.qualitest.common.core.controller.BaseController;
import com.qualitest.common.core.domain.R;
import com.qualitest.common.core.page.TableDataInfo;
import com.qualitest.common.core.text.Convert;
import com.qualitest.common.enums.BusinessType;
import com.qualitest.project.domain.TestProjectTemplate;
import com.qualitest.project.params.TestProjectTemplateParams;
import com.qualitest.project.result.TestProjectTemplateResult;
import com.qualitest.project.service.ITestProjectTemplateService;
import lombok.AllArgsConstructor;
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

/**
 * 鉴权模板库接口：列表、详情、增改删、克隆、启用列表。
 */
@RestController
@RequestMapping("/project/testProjectTemplate")
@AllArgsConstructor
public class TestProjectTemplateController extends BaseController {

    private final ITestProjectTemplateService testProjectTemplateService;

    /** 分页列表。 */
    @PreAuthorize("@ss.hasPermi('project:testProjectTemplate:list')")
    @GetMapping("/list")
    public TableDataInfo list(TestProjectTemplateParams params) {
        startPage();
        List<TestProjectTemplateResult> list = testProjectTemplateService.selectTestProjectTemplateResultList(params);
        return getDataTable(list);
    }

    /** 已启用模板，给新建项目或设置页勾选。 */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @GetMapping("/enabledList")
    public R<List<TestProjectTemplateResult>> enabledList() {
        return ok(testProjectTemplateService.selectEnabledList());
    }

    /** 模板详情。 */
    @PreAuthorize("@ss.hasPermi('project:testProjectTemplate:query')")
    @GetMapping("/{testProjectTemplateId}")
    public R<TestProjectTemplateResult> getInfo(@PathVariable Long testProjectTemplateId) {
        return ok(testProjectTemplateService.selectTestProjectTemplateResult(testProjectTemplateId));
    }

    /** 新增自定义模板。 */
    @PreAuthorize("@ss.hasPermi('project:testProjectTemplate:add')")
    @Log(title = "测试项目模板", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@RequestBody TestProjectTemplate entity) {
        return toR(testProjectTemplateService.insertTestProjectTemplate(entity));
    }

    /** 修改自定义模板。 */
    @PreAuthorize("@ss.hasPermi('project:testProjectTemplate:edit')")
    @Log(title = "测试项目模板", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@RequestBody TestProjectTemplate entity) {
        return toR(testProjectTemplateService.updateTestProjectTemplate(entity));
    }

    /** 克隆为自定义模板，返回新 id。 */
    @PreAuthorize("@ss.hasPermi('project:testProjectTemplate:add')")
    @Log(title = "测试项目模板", businessType = BusinessType.INSERT)
    @PostMapping("/{testProjectTemplateId}/clone")
    public R<Long> clone(@PathVariable Long testProjectTemplateId) {
        return ok(testProjectTemplateService.cloneTestProjectTemplate(testProjectTemplateId));
    }

    /** 逻辑删除自定义模板。 */
    @PreAuthorize("@ss.hasPermi('project:testProjectTemplate:remove')")
    @Log(title = "测试项目模板", businessType = BusinessType.DELETE)
    @DeleteMapping("/{testProjectTemplateIds}")
    public R<Void> remove(@PathVariable Long[] testProjectTemplateIds) {
        List<Long> idList = Convert.toLongList(testProjectTemplateIds);
        return toR(testProjectTemplateService.logicDeleteTestProjectTemplateByIdList(idList));
    }
}
