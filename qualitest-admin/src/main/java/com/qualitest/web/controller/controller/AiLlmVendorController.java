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
import com.qualitest.ai.domain.AiLlmVendor;
import com.qualitest.ai.params.AiLlmVendorParams;
import com.qualitest.ai.result.AiLlmVendorResult;
import com.qualitest.ai.service.IAiLlmVendorService;
import com.qualitest.common.core.text.Convert;
import com.qualitest.common.utils.poi.ExcelUtil;
import com.qualitest.common.core.page.TableDataInfo;

/**
 * AI 厂商管理接口。
 * <p>
 * 列表与详情响应中的 apiKey 经 {@link #maskApiKey} 脱敏为 {@code ******}，避免密钥泄露。
 */
@RestController
@RequestMapping("/ai/aiLlmVendor")
@AllArgsConstructor
public class AiLlmVendorController extends BaseController {

    private final IAiLlmVendorService aiLlmVendorService;

    /**
     * 查询AI 厂商列表
     */
    @PreAuthorize("@ss.hasPermi('ai:aiLlmVendor:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiLlmVendorParams params) {
        startPage();
        List<AiLlmVendorResult> list = aiLlmVendorService.selectAiLlmVendorResultList(params);
        list.forEach(this::maskApiKey);
        return getDataTable(list);
    }

    /**
     * 导出AI 厂商列表
     */
    @PreAuthorize("@ss.hasPermi('ai:aiLlmVendor:export')")
    @Log(title = "AI 厂商", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiLlmVendorParams params) {
        List<AiLlmVendorResult> list = aiLlmVendorService.selectAiLlmVendorResultList(params);
        ExcelUtil<AiLlmVendorResult> util = new ExcelUtil<>(AiLlmVendorResult.class);
        util.exportExcel(response, list, "AI 厂商数据");
    }

    /**
     * 获取AI 厂商详细信息
     */
    @PreAuthorize("@ss.hasPermi('ai:aiLlmVendor:query')")
    @GetMapping(value = "/{aiLlmVendorId}")
    public R<AiLlmVendorResult> getInfo(@PathVariable("aiLlmVendorId") Long aiLlmVendorId) {
        AiLlmVendorResult result = aiLlmVendorService.selectAiLlmVendorResult(aiLlmVendorId);
        maskApiKey(result);
        return ok(result);
    }

    /**
     * 新增AI 厂商
     */
    @PreAuthorize("@ss.hasPermi('ai:aiLlmVendor:add')")
    @Log(title = "AI 厂商", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@RequestBody AiLlmVendor aiLlmVendor) {
        return toR(aiLlmVendorService.insertAiLlmVendor(aiLlmVendor));
    }

    /**
     * 修改AI 厂商
     */
    @PreAuthorize("@ss.hasPermi('ai:aiLlmVendor:edit')")
    @Log(title = "AI 厂商", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@RequestBody AiLlmVendor aiLlmVendor) {
        return toR(aiLlmVendorService.updateAiLlmVendor(aiLlmVendor));
    }

    /**
     * 删除AI 厂商
     */
    @PreAuthorize("@ss.hasPermi('ai:aiLlmVendor:remove')")
    @Log(title = "AI 厂商", businessType = BusinessType.DELETE)
    @DeleteMapping("/{aiLlmVendorIds}")
    public R<Void> remove(@PathVariable Long[] aiLlmVendorIds) {
        List<Long> idList = Convert.toLongList(aiLlmVendorIds);
        return toR(aiLlmVendorService.logicDeleteAiLlmVendorByIdList(idList));
    }

    /** 将非空 apiKey 替换为占位符，仅用于对外查询接口 */
    private void maskApiKey(AiLlmVendorResult result) {
        if (result != null && result.getApiKey() != null && !result.getApiKey().isBlank()) {
            result.setApiKey("******");
        }
    }
}
