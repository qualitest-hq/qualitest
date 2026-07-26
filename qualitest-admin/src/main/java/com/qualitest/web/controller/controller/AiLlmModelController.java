package com.qualitest.web.controller.controller;

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
import com.qualitest.ai.domain.AiLlmModel;
import com.qualitest.ai.params.AiLlmModelParams;
import com.qualitest.ai.result.AiLlmModelResult;
import com.qualitest.ai.service.IAiLlmModelService;
import com.qualitest.common.core.text.Convert;
import com.qualitest.common.utils.poi.ExcelUtil;
import com.qualitest.common.core.page.TableDataInfo;

/**
 * AI 模型Controller
 * 
 * @author qualitest
 * @date 2026-06-15
 */
@RestController
@RequestMapping("/ai/aiLlmModel")
@AllArgsConstructor
public class AiLlmModelController extends BaseController {

    private final IAiLlmModelService aiLlmModelService;

    /**
     * 查询AI 模型列表
     */
    @PreAuthorize("@ss.hasPermi('ai:aiLlmModel:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiLlmModelParams params) {
        startPage();
        List<AiLlmModelResult> list = aiLlmModelService.selectAiLlmModelResultList(params);
        return getDataTable(list);
    }

    /**
     * 导出AI 模型列表
     */
    @PreAuthorize("@ss.hasPermi('ai:aiLlmModel:export')")
    @Log(title = "AI 模型", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiLlmModelParams params) {
        List<AiLlmModelResult> list = aiLlmModelService.selectAiLlmModelResultList(params);
        ExcelUtil<AiLlmModelResult> util = new ExcelUtil<>(AiLlmModelResult.class);
        util.exportExcel(response, list, "AI 模型数据");
    }

    /**
     * 获取AI 模型详细信息
     */
    @PreAuthorize("@ss.hasPermi('ai:aiLlmModel:query')")
    @GetMapping(value = "/{aiLlmModelId}")
    public R<AiLlmModelResult> getInfo(@PathVariable("aiLlmModelId") Long aiLlmModelId) {
        return ok(aiLlmModelService.selectAiLlmModelResult(aiLlmModelId));
    }

    /**
     * 新增AI 模型
     */
    @PreAuthorize("@ss.hasPermi('ai:aiLlmModel:add')")
    @Log(title = "AI 模型", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@RequestBody AiLlmModel aiLlmModel) {
        return toR(aiLlmModelService.insertAiLlmModel(aiLlmModel));
    }

    /**
     * 修改AI 模型
     */
    @PreAuthorize("@ss.hasPermi('ai:aiLlmModel:edit')")
    @Log(title = "AI 模型", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@RequestBody AiLlmModel aiLlmModel) {
        return toR(aiLlmModelService.updateAiLlmModel(aiLlmModel));
    }

    /**
     * 删除AI 模型
     */
    @PreAuthorize("@ss.hasPermi('ai:aiLlmModel:remove')")
    @Log(title = "AI 模型", businessType = BusinessType.DELETE)
    @DeleteMapping("/{aiLlmModelIds}")
    public R<Void> remove(@PathVariable Long[] aiLlmModelIds) {
        List<Long> idList = Convert.toLongList(aiLlmModelIds);
        return toR(aiLlmModelService.logicDeleteAiLlmModelByIdList(idList));
    }
}
