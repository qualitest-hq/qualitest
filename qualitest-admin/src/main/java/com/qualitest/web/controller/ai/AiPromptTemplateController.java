package com.qualitest.web.controller.ai;

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
import com.qualitest.common.core.controller.BaseController;
import com.qualitest.common.core.domain.R;
import com.qualitest.common.enums.BusinessType;
import com.qualitest.ai.domain.AiPromptTemplate;
import com.qualitest.ai.params.AiPromptTemplateParams;
import com.qualitest.ai.result.AiPromptTemplateResult;
import com.qualitest.ai.service.IAiPromptTemplateService;
import com.qualitest.common.core.text.Convert;
import com.qualitest.common.utils.poi.ExcelUtil;
import com.qualitest.common.core.page.TableDataInfo;

/**
 * AI提示词模板Controller
 * 
 * @author qualitest
 * @date 2026-07-04
 */
@RestController
@RequestMapping("/ai/aiPromptTemplate")
@AllArgsConstructor
public class AiPromptTemplateController extends BaseController {

    private final IAiPromptTemplateService aiPromptTemplateService;

    /**
     * 查询AI提示词模板列表
     */
    @PreAuthorize("@ss.hasPermi('ai:aiPromptTemplate:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiPromptTemplateParams params) {
        startPage();
        List<AiPromptTemplateResult> list = aiPromptTemplateService.selectAiPromptTemplateResultList(params);
        return getDataTable(list);
    }

    /**
     * 导出AI提示词模板列表
     */
    @PreAuthorize("@ss.hasPermi('ai:aiPromptTemplate:export')")
    @Log(title = "AI提示词模板", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiPromptTemplateParams params) {
        List<AiPromptTemplateResult> list = aiPromptTemplateService.selectAiPromptTemplateResultList(params);
        ExcelUtil<AiPromptTemplateResult> util = new ExcelUtil<>(AiPromptTemplateResult.class);
        util.exportExcel(response, list, "AI提示词模板数据");
    }

    /**
     * 获取AI提示词模板详细信息
     */
    @PreAuthorize("@ss.hasPermi('ai:aiPromptTemplate:query')")
    @GetMapping(value = "/{aiPromptTemplateId}")
    public R<AiPromptTemplateResult> getInfo(@PathVariable("aiPromptTemplateId") Long aiPromptTemplateId) {
        return ok(aiPromptTemplateService.selectAiPromptTemplateResult(aiPromptTemplateId));
    }

    /**
     * 新增AI提示词模板
     */
    @PreAuthorize("@ss.hasPermi('ai:aiPromptTemplate:add')")
    @Log(title = "AI提示词模板", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@RequestBody AiPromptTemplate aiPromptTemplate) {
        return toR(aiPromptTemplateService.insertAiPromptTemplate(aiPromptTemplate));
    }

    /**
     * 修改AI提示词模板
     */
    @PreAuthorize("@ss.hasPermi('ai:aiPromptTemplate:edit')")
    @Log(title = "AI提示词模板", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@RequestBody AiPromptTemplate aiPromptTemplate) {
        return toR(aiPromptTemplateService.updateAiPromptTemplate(aiPromptTemplate));
    }

    /**
     * 删除AI提示词模板
     */
    @PreAuthorize("@ss.hasPermi('ai:aiPromptTemplate:remove')")
    @Log(title = "AI提示词模板", businessType = BusinessType.DELETE)
    @DeleteMapping("/{aiPromptTemplateIds}")
    public R<Void> remove(@PathVariable Long[] aiPromptTemplateIds) {
        List<Long> idList = Convert.toLongList(aiPromptTemplateIds);
        return toR(aiPromptTemplateService.logicDeleteAiPromptTemplateByIdList(idList));
    }
}
