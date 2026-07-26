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
import com.qualitest.ai.domain.AiChatMessage;
import com.qualitest.ai.params.AiChatMessageParams;
import com.qualitest.ai.result.AiChatMessageResult;
import com.qualitest.ai.service.IAiChatMessageService;
import com.qualitest.common.core.text.Convert;
import com.qualitest.common.utils.poi.ExcelUtil;
import com.qualitest.common.core.page.TableDataInfo;

/**
 * AI 会话消息Controller
 * 
 * @author qualitest
 * @date 2026-06-15
 */
@RestController
@RequestMapping("/ai/aiChatMessage")
@AllArgsConstructor
public class AiChatMessageController extends BaseController {

    private final IAiChatMessageService aiChatMessageService;

    /**
     * 查询AI 会话消息列表
     */
    @PreAuthorize("@ss.hasPermi('ai:aiChatMessage:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiChatMessageParams params) {
        startPage();
        List<AiChatMessageResult> list = aiChatMessageService.selectAiChatMessageResultList(params);
        return getDataTable(list);
    }

    /**
     * 导出AI 会话消息列表
     */
    @PreAuthorize("@ss.hasPermi('ai:aiChatMessage:export')")
    @Log(title = "AI 会话消息", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiChatMessageParams params) {
        List<AiChatMessageResult> list = aiChatMessageService.selectAiChatMessageResultList(params);
        ExcelUtil<AiChatMessageResult> util = new ExcelUtil<>(AiChatMessageResult.class);
        util.exportExcel(response, list, "AI 会话消息数据");
    }

    /**
     * 获取AI 会话消息详细信息
     */
    @PreAuthorize("@ss.hasPermi('ai:aiChatMessage:query')")
    @GetMapping(value = "/{aiChatMessageId}")
    public R<AiChatMessageResult> getInfo(@PathVariable("aiChatMessageId") Long aiChatMessageId) {
        return ok(aiChatMessageService.selectAiChatMessageResult(aiChatMessageId));
    }

    /**
     * 新增AI 会话消息
     */
    @PreAuthorize("@ss.hasPermi('ai:aiChatMessage:add')")
    @Log(title = "AI 会话消息", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@RequestBody AiChatMessage aiChatMessage) {
        return toR(aiChatMessageService.insertAiChatMessage(aiChatMessage));
    }

    /**
     * 修改AI 会话消息
     */
    @PreAuthorize("@ss.hasPermi('ai:aiChatMessage:edit')")
    @Log(title = "AI 会话消息", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@RequestBody AiChatMessage aiChatMessage) {
        return toR(aiChatMessageService.updateAiChatMessage(aiChatMessage));
    }

    /**
     * 删除AI 会话消息
     */
    @PreAuthorize("@ss.hasPermi('ai:aiChatMessage:remove')")
    @Log(title = "AI 会话消息", businessType = BusinessType.DELETE)
    @DeleteMapping("/{aiChatMessageIds}")
    public R<Void> remove(@PathVariable Long[] aiChatMessageIds) {
        List<Long> idList = Convert.toLongList(aiChatMessageIds);
        return toR(aiChatMessageService.deleteAiChatMessageByIdList(idList));
    }
}
